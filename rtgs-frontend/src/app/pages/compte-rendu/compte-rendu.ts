import { Component, OnInit, inject, signal, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormArray, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { ToastService } from '../../services/toast.service';
import { InterventionDTO } from '../../models/intervention.model';

@Component({
  selector: 'app-compte-rendu',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './compte-rendu.html'
})
export class CompteRenduComponent implements OnInit {
  @Input() id!: string;

  private service = inject(InterventionService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  intervention = signal<InterventionDTO | null>(null);
  loading = signal(true);
  saving = signal(false);

  hasCritique = signal(false);

  form = this.fb.group({
    dateRealisation: ['', Validators.required],
    resume: ['', Validators.required],
    recommandations: [''],
    nonConformites: this.fb.array([])
  });

  get ncArray() { return this.form.get('nonConformites') as FormArray; }

  ngOnInit() {
    this.service.getById(Number(this.id)).subscribe({
      next: i => { this.intervention.set(i); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  addNC() {
    this.ncArray.push(this.fb.group({
      gravite: ['MINEUR', Validators.required],
      description: ['', Validators.required],
      delaiCorrectionJours: [30]
    }));
    this.checkCritique();
  }

  removeNC(i: number) {
    this.ncArray.removeAt(i);
    this.checkCritique();
  }

  checkCritique() {
    const hasCrit = this.ncArray.controls.some(c => c.get('gravite')?.value === 'CRITIQUE');
    this.hasCritique.set(hasCrit);
  }

  graviteClass(g: string) {
    return g === 'CRITIQUE' ? 'gravite-critique' : g === 'MAJEUR' ? 'gravite-majeur' : 'gravite-mineur';
  }

  submit() {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.value;
    this.service.cloturer(Number(this.id), {
      dateRealisation: v.dateRealisation!,
      resume: v.resume!,
      recommandations: v.recommandations || undefined,
      nonConformites: (v.nonConformites as any[]) || []
    }).subscribe({
      next: (res) => {
        this.saving.set(false);
        const msg = res.referenceCorrectif
          ? `Intervention clôturée. Correctif créé : ${res.referenceCorrectif}`
          : 'Intervention clôturée avec succès.';
        this.toast.show(msg, 'success');
        this.router.navigate(['/interventions']);
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erreur lors de la clôture', 'error');
      }
    });
  }
}
