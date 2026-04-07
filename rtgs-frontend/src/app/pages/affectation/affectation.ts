import { Component, OnInit, inject, signal, computed, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { InterventionDTO, AffectationRequestDTO } from '../../models/intervention.model';
import { UtilisateurDispoDTO } from '../../models/utilisateur.model';
import { ToastService } from '../../services/toast.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-affectation',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './affectation.html'
})
export class AffectationComponent implements OnInit {
  @Input() id!: string;

  private service = inject(InterventionService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private router = inject(Router);

  intervention = signal<InterventionDTO | null>(null);
  intervenants = signal<UtilisateurDispoDTO[]>([]);
  selectedIds = signal<number[]>([]);
  chefMissionId = signal<number | null>(null);
  loading = signal(true);
  saving = signal(false);

  get role() { return this.auth.currentRole(); }
  get canEdit() { return ['ADMIN', 'CHARGE_MISSION'].includes(this.role); }

  teamSelected = computed(() =>
    this.intervenants().filter(u => this.selectedIds().includes(u.id))
  );

  competencesCouverts = computed(() => {
    const team = this.teamSelected();
    const reqs = (this.intervention()?.competencesRequises || '').split(',').map(c => c.trim()).filter(Boolean);
    const covered = new Set(team.flatMap(u => u.competencesList || []));
    return reqs.map(c => ({ comp: c, ok: covered.has(c) }));
  });

  allCompsCovered = computed(() => this.competencesCouverts().every(c => c.ok));

  hasChefMission = computed(() => this.chefMissionId() !== null && this.selectedIds().includes(this.chefMissionId()!));

  canConfirm = computed(() =>
    this.selectedIds().length > 0 && this.hasChefMission()
  );

  ngOnInit() {
    const intId = Number(this.id);
    this.service.getById(intId).subscribe(i => this.intervention.set(i));
    this.service.getIntervenantsDisponibles(intId).subscribe({
      next: u => { this.intervenants.set(u); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  toggleUser(u: UtilisateurDispoDTO) {
    if (!this.canEdit) return;
    const id = u.id;
    const isSelected = this.selectedIds().includes(id);

    if (isSelected) {
      this.selectedIds.update(ids => ids.filter(x => x !== id));
      if (this.chefMissionId() === id) this.chefMissionId.set(null);
    } else {
      this.selectedIds.update(ids => [...ids, id]);
      if (this.chefMissionId() === null) this.chefMissionId.set(id);
    }
  }

  setChefMission(id: number) {
    if (!this.canEdit) return;
    this.chefMissionId.set(id);
  }

  isSelected(id: number) { return this.selectedIds().includes(id); }
  isChef(id: number) { return this.chefMissionId() === id; }

  confirmerAffectation() {
    if (!this.canEdit || !this.canConfirm()) return;
    this.saving.set(true);

    const chefId = this.chefMissionId()!;
    const affectations: AffectationRequestDTO[] = this.selectedIds().map(uid => ({
      utilisateurId: uid,
      role: uid === chefId ? 'Chef de mission' : 'Intervenant'
    }));

    this.service.affecter(Number(this.id), affectations).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.show('Équipe affectée avec succès', 'success');
        this.router.navigate(['/interventions']);
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.show(err.error?.message || "Erreur lors de l'affectation", 'error');
      }
    });
  }
}
