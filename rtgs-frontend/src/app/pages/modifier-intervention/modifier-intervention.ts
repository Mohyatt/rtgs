import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { ToastService } from '../../services/toast.service';
import { InterventionDTO } from '../../models/intervention.model';

const ALL_COMPETENCES = ['SECURITE', 'VENTILATION', 'EEG', 'MATERIAUX', 'GEOLOGIE', 'EXPLOITATION', 'PCME'];

@Component({
  selector: 'app-modifier-intervention',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './modifier-intervention.html'
})
export class ModifierInterventionComponent implements OnInit {
  private service = inject(InterventionService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  allCompetences = ALL_COMPETENCES;
  selectedCompetences = signal<string[]>([]);
  loading = signal(false);
  loadingData = signal(true);
  intervention = signal<InterventionDTO | null>(null);
  interventionId = 0;

  form = this.fb.group({
    datePrevue: ['', Validators.required],
    dateFinPrevue: [''],
    description: [''],
  });

  ngOnInit() {
    this.interventionId = +this.route.snapshot.params['id'];
    this.service.getById(this.interventionId).subscribe({
      next: (i) => {
        this.intervention.set(i);
        this.form.patchValue({
          datePrevue: i.datePrevue || '',
          dateFinPrevue: i.dateFinPrevue || '',
          description: i.description || '',
        });
        this.selectedCompetences.set(
          i.competencesRequises ? i.competencesRequises.split(',').map(c => c.trim()) : []
        );
        this.loadingData.set(false);
      },
      error: () => {
        this.toast.show('Intervention introuvable', 'error');
        this.router.navigate(['/interventions']);
      }
    });
  }

  toggleComp(c: string) {
    this.selectedCompetences.update(list =>
      list.includes(c) ? list.filter(x => x !== c) : [...list, c]
    );
  }

  hasComp(c: string) { return this.selectedCompetences().includes(c); }

  submit() {
    if (this.form.invalid || this.selectedCompetences().length === 0) return;
    this.loading.set(true);
    const v = this.form.value;
    this.service.update(this.interventionId, {
      datePrevue: v.datePrevue!,
      dateFinPrevue: v.dateFinPrevue || undefined,
      description: v.description || undefined,
      competencesRequises: this.selectedCompetences().join(','),
    } as any).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.show('Intervention modifiée avec succès', 'success');
        this.router.navigate(['/interventions']);
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err.error?.message || 'Erreur lors de la modification';
        this.toast.show(msg, 'error');
      }
    });
  }
}
