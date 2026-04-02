import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { AlerteService } from '../../services/alerte.service';
import { TunnelService } from '../../services/tunnel.service';
import { ToastService } from '../../services/toast.service';
import { TunnelDTO } from '../../models/tunnel.model';

const TYPE_COMPETENCES: Record<string, string[]> = {
  VISITE_SECURITE_ANNUELLE: ['SECURITE', 'VENTILATION'],
  INSPECTION_PERIODIQUE: ['SECURITE'],
  CONTROLE_VENTILATION: ['VENTILATION', 'EEG'],
  EXPERTISE_GENIE_CIVIL: ['MATERIAUX', 'GEOLOGIE'],
  AUDIT_EXPLOITATION: ['EXPLOITATION', 'SECURITE'],
  INTERVENTION_URGENCE: ['SECURITE', 'PCME'],
};

const TYPES = [
  { value: 'VISITE_SECURITE_ANNUELLE', label: 'Visite sécurité annuelle' },
  { value: 'INSPECTION_PERIODIQUE', label: 'Inspection périodique' },
  { value: 'CONTROLE_VENTILATION', label: 'Contrôle ventilation' },
  { value: 'EXPERTISE_GENIE_CIVIL', label: 'Expertise génie civil' },
  { value: 'AUDIT_EXPLOITATION', label: 'Audit exploitation' },
  { value: 'INTERVENTION_URGENCE', label: 'Intervention urgence' },
];

const ALL_COMPETENCES = ['SECURITE', 'VENTILATION', 'EEG', 'MATERIAUX', 'GEOLOGIE', 'EXPLOITATION', 'PCME'];

@Component({
  selector: 'app-nouvelle-intervention',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './nouvelle-intervention.html'
})
export class NouvelleInterventionComponent implements OnInit {
  private service = inject(InterventionService);
  private alerteService = inject(AlerteService);
  private tunnelService = inject(TunnelService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  tunnels = signal<TunnelDTO[]>([]);
  types = TYPES;
  allCompetences = ALL_COMPETENCES;
  selectedCompetences = signal<string[]>([]);
  loading = signal(false);
  alerteAssignmentId = signal<number | null>(null);
  alerteInfo = signal<string | null>(null);

  form = this.fb.group({
    tunnelId: [null as number | null, Validators.required],
    type: ['', Validators.required],
    datePrevue: ['', Validators.required],
    dateFinPrevue: [''],
    description: [''],
    priorite: ['NORMALE'],
  });

  ngOnInit() {
    this.tunnelService.getAll().subscribe(t => {
      this.tunnels.set(t);
      this.prefillFromAlerte(t);
    });
    this.form.get('type')!.valueChanges.subscribe(type => {
      if (type) this.autoCompetences(type);
    });
  }

  private prefillFromAlerte(tunnels: TunnelDTO[]) {
    const params = this.route.snapshot.queryParams;
    if (params['alerteAssignmentId']) {
      this.alerteAssignmentId.set(+params['alerteAssignmentId']);
      const desc = params['description'] || '';
      const niveau = params['niveau'] || '';
      const objetLibelle = params['objetLibelle'] || '';
      this.alerteInfo.set(`Alerte ${niveau} — ${objetLibelle}`);
      this.form.patchValue({
        description: `[Alerte ${niveau}] ${desc}`
      });

      if (params['objetType'] === 'TUNNEL' && params['objetId']) {
        const tunnelId = +params['objetId'];
        const found = tunnels.find(t => t.id === tunnelId);
        if (found) {
          this.form.patchValue({ tunnelId });
        }
      }

      if (niveau === 'CRITIQUE') {
        this.form.patchValue({ type: 'INTERVENTION_URGENCE' });
        this.autoCompetences('INTERVENTION_URGENCE');
      }
    }
  }

  autoCompetences(type: string) {
    const auto = TYPE_COMPETENCES[type] || [];
    this.selectedCompetences.set(auto);
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
    this.service.create({
      tunnel: { id: v.tunnelId! } as any,
      type: v.type!,
      datePrevue: v.datePrevue!,
      dateFinPrevue: v.dateFinPrevue || undefined,
      description: v.description || undefined,
      competencesRequises: this.selectedCompetences().join(','),
    }).subscribe({
      next: (intervention) => {
        const assignmentId = this.alerteAssignmentId();
        if (assignmentId) {
          this.alerteService.lierIntervention(assignmentId, intervention.id).subscribe({
            next: () => {
              this.loading.set(false);
              this.toast.show('Intervention créée et alerte traitée', 'success');
              this.router.navigate(['/interventions', intervention.id, 'affectation']);
            },
            error: () => {
              this.loading.set(false);
              this.toast.show('Intervention créée mais liaison alerte échouée', 'warning');
              this.router.navigate(['/interventions', intervention.id, 'affectation']);
            }
          });
        } else {
          this.loading.set(false);
          this.toast.show('Intervention créée avec succès', 'success');
          this.router.navigate(['/interventions', intervention.id, 'affectation']);
        }
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err.error?.message || 'Erreur lors de la création';
        this.toast.show(msg, 'error');
      }
    });
  }
}
