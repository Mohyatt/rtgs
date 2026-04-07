import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { InterventionService } from '../../services/intervention.service';
import { ToastService } from '../../services/toast.service';
import { InterventionDTO, AffectationResponseDTO } from '../../models/intervention.model';
import { UtilisateurDispoDTO } from '../../models/utilisateur.model';

const ALL_COMPETENCES = ['SECURITE', 'VENTILATION', 'EEG', 'MATERIAUX', 'GEOLOGIE', 'EXPLOITATION', 'PCME'];

@Component({
  selector: 'app-modifier-intervention',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './modifier-intervention.html'
})
export class ModifierInterventionComponent implements OnInit, OnDestroy {
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

  // Remplacement
  remplacerTarget = signal<AffectationResponseDTO | null>(null);
  disponibles = signal<UtilisateurDispoDTO[]>([]);
  loadingDisponibles = signal(false);
  replacingId = signal<number | null>(null);

  // Vérification conflit live
  conflitsDetectes = signal<InterventionDTO[]>([]);
  private dateChange$ = new Subject<{ datePrevue: string; dateFinPrevue: string }>();
  private destroy$ = new Subject<void>();

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
        // Verrouiller la date de début si déjà démarrée
        if (i.statut === 'EN_COURS') {
          this.form.get('datePrevue')?.disable();
        }
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

    // Vérification live : déclenche quand les dates changent (debounce 600ms)
    this.dateChange$.pipe(
      debounceTime(600),
      distinctUntilChanged((a, b) => a.datePrevue === b.datePrevue && a.dateFinPrevue === b.dateFinPrevue),
      switchMap(({ datePrevue, dateFinPrevue }) => {
        if (!datePrevue) return of([]);
        return this.service.verifierConflits(this.interventionId, datePrevue, dateFinPrevue || undefined)
          .pipe(catchError(() => of([])));
      })
    ).subscribe(conflits => this.conflitsDetectes.set(conflits));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onDateChange() {
    const v = this.form.value;
    this.dateChange$.next({
      datePrevue: v.datePrevue || '',
      dateFinPrevue: v.dateFinPrevue || ''
    });
  }

  ouvrirRemplacement(membre: AffectationResponseDTO) {
    this.remplacerTarget.set(membre);
    this.disponibles.set([]);
    this.loadingDisponibles.set(true);
    this.service.getIntervenantsForRemplacement(this.interventionId, membre.utilisateurId).subscribe({
      next: list => { this.disponibles.set(list); this.loadingDisponibles.set(false); },
      error: () => this.loadingDisponibles.set(false)
    });
  }

  annulerRemplacement() {
    this.remplacerTarget.set(null);
    this.disponibles.set([]);
  }

  confirmerRemplacement(nouvelId: number) {
    const target = this.remplacerTarget();
    if (!target) return;
    this.replacingId.set(nouvelId);
    this.service.remplacerMembre(this.interventionId, target.utilisateurId, nouvelId).subscribe({
      next: updated => {
        this.intervention.set(updated);
        this.remplacerTarget.set(null);
        this.disponibles.set([]);
        this.replacingId.set(null);
        this.toast.show(`${target.nomComplet} remplacé avec succès`, 'success');
      },
      error: (err) => {
        this.replacingId.set(null);
        this.toast.show(err.error?.message || 'Erreur lors du remplacement', 'error');
      }
    });
  }

  get dateFinError(): boolean {
    const v = this.form.value;
    return !!(v.datePrevue && v.dateFinPrevue && v.dateFinPrevue <= v.datePrevue);
  }

  get membreIndisponibles() {
    return (this.intervention()?.affectations || []).filter(a => a.statut === 'INDISPONIBLE');
  }

  toggleComp(c: string) {
    this.selectedCompetences.update(list =>
      list.includes(c) ? list.filter(x => x !== c) : [...list, c]
    );
  }

  hasComp(c: string) { return this.selectedCompetences().includes(c); }

  submit() {
    if (this.form.invalid || this.selectedCompetences().length === 0 || this.dateFinError) return;
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
