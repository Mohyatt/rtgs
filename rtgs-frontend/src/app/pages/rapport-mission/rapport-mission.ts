import { Component, OnInit, inject, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { ToastService } from '../../services/toast.service';
import { InterventionDTO } from '../../models/intervention.model';

@Component({
  selector: 'app-rapport-mission',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './rapport-mission.html'
})
export class RapportMissionComponent implements OnInit {
  @Input() id!: string;

  private service = inject(InterventionService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  intervention = signal<InterventionDTO | null>(null);
  loading = signal(true);
  saving = signal(false);

  form = this.fb.group({
    resume: ['', Validators.required],
    recommandations: ['']
  });

  ngOnInit() {
    this.service.getById(Number(this.id)).subscribe({
      next: i => { this.intervention.set(i); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  submit() {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.value;
    this.service.soumettreRapport(Number(this.id), {
      resume: v.resume!,
      recommandations: v.recommandations || undefined
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.show('Rapport soumis — en attente de clôture par le chargé de mission', 'success');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erreur lors de la soumission', 'error');
      }
    });
  }
}
