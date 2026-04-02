import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

interface DemoAccount {
  email: string;
  password: string;
  label: string;
  role: string;
  badge: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.html'
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);

  loading = signal(false);
  errorMsg = signal('');

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', Validators.required]
  });

  demoAccounts: DemoAccount[] = [
    { email: 'axel.here@betuf.fr', password: 'rtgs2026', label: 'Axel Hère', role: 'Administrateur / Direction', badge: 'ADM' },
    { email: 'isabelle.pont@betuf.fr', password: 'rtgs2026', label: 'Isabelle Pont', role: 'Chargée de Mission', badge: 'CDM' },
    { email: 'angela.d@betuf.fr', password: 'rtgs2026', label: 'Angela Doublesens', role: 'Ingénieure', badge: 'ING' },
    { email: 'jenny.civil@betuf.fr', password: 'rtgs2026', label: 'Jenny Civil', role: 'Administratrice Informatique', badge: 'ADM' },
  ];

  fillAndSubmit(account: DemoAccount) {
    this.form.setValue({ email: account.email, motDePasse: account.password });
    this.submit();
  }

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMsg.set('');
    const { email, motDePasse } = this.form.value;
    this.auth.login({ email: email!, motDePasse: motDePasse! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.show('Connexion réussie', 'success');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMsg.set(err.status === 401 ? 'Email ou mot de passe incorrect.' : 'Erreur de connexion. Réessayez.');
      }
    });
  }
}
