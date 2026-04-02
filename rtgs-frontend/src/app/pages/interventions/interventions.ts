import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { InterventionService } from '../../services/intervention.service';
import { InterventionDTO } from '../../models/intervention.model';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-interventions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './interventions.html'
})
export class InterventionsComponent implements OnInit {
  private service = inject(InterventionService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  interventions = signal<InterventionDTO[]>([]);
  loading = signal(true);
  searchText = signal('');

  filtreStatut = signal('');
  filtreType = signal('');
  filtreDateFrom = signal('');
  filtreDateTo = signal('');
  filtreRetard = signal('');
  filtreCompetence = signal('');

  types = [
    { value: 'VISITE_SECURITE_ANNUELLE', label: 'Visite sécurité annuelle' },
    { value: 'CONTROLE_VENTILATION', label: 'Contrôle ventilation' },
    { value: 'EXPERTISE_GENIE_CIVIL', label: 'Expertise génie civil' },
    { value: 'INSPECTION_PERIODIQUE', label: 'Inspection périodique' },
    { value: 'CONTROLE_SUITE_INCIDENT', label: 'Contrôle suite incident' },
    { value: 'INTERVENTION_CORRECTIVE', label: 'Intervention corrective' },
  ];

  get role() { return this.auth.currentRole(); }
  get userId() { return this.auth.currentUser()?.userId; }
  get canCreate() { return this.role === 'CHARGE_MISSION'; }
  get canEdit() { return this.role === 'CHARGE_MISSION'; }
  get canChangeStatut() {
    return this.role === 'CHARGE_MISSION' || this.role === 'INGENIEUR';
  }
  get isReadOnly() { return this.role === 'EXPLOITANT'; }

  canModify(i: InterventionDTO): boolean {
    if (this.role !== 'CHARGE_MISSION') return false;
    const isCreateur = i.createurId === this.userId;
    return isCreateur && (i.statut === 'BROUILLON' || i.statut === 'PLANIFIEE');
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['statut']) this.filtreStatut.set(params['statut']);
      if (params['type']) this.filtreType.set(params['type']);
      if (params['dateFrom']) this.filtreDateFrom.set(params['dateFrom']);
      if (params['dateTo']) this.filtreDateTo.set(params['dateTo']);
      if (params['retard']) this.filtreRetard.set(params['retard']);
      if (params['competence']) this.filtreCompetence.set(params['competence']);
      this.load();
    });
  }

  load() {
    this.loading.set(true);
    this.service.getAll(
      this.filtreStatut() || undefined,
      this.filtreType() || undefined,
      undefined,
      this.filtreDateFrom() || undefined,
      this.filtreDateTo() || undefined
    ).subscribe({
      next: i => { this.interventions.set(i); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  filtered() {
    let list = this.interventions();
    const s = this.searchText().toLowerCase();
    if (s) {
      list = list.filter(i =>
        i.reference.toLowerCase().includes(s) ||
        i.tunnel?.libelle?.toLowerCase().includes(s) ||
        i.createurNom?.toLowerCase().includes(s) ||
        (i.affectations || []).some(a => a.nomComplet?.toLowerCase().includes(s)));
    }
    const retard = this.filtreRetard();
    if (retard === 'oui') list = list.filter(i => this.isEnRetard(i));
    if (retard === 'non') list = list.filter(i => !this.isEnRetard(i));

    const comp = this.filtreCompetence();
    if (comp) {
      list = list.filter(i =>
        i.competencesRequises?.toUpperCase().includes(comp.toUpperCase()));
    }
    return list;
  }

  setFiltre(field: string, value: string) {
    switch (field) {
      case 'statut': this.filtreStatut.set(value); this.load(); break;
      case 'type': this.filtreType.set(value); this.load(); break;
      case 'dateFrom': this.filtreDateFrom.set(value); this.load(); break;
      case 'dateTo': this.filtreDateTo.set(value); this.load(); break;
      case 'retard': this.filtreRetard.set(value); break;
      case 'competence': this.filtreCompetence.set(value); break;
    }
  }

  resetFilters() {
    this.filtreStatut.set('');
    this.filtreType.set('');
    this.filtreDateFrom.set('');
    this.filtreDateTo.set('');
    this.filtreRetard.set('');
    this.filtreCompetence.set('');
    this.searchText.set('');
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(this.filtreStatut() || this.filtreType() || this.filtreDateFrom() ||
              this.filtreDateTo() || this.filtreRetard() || this.filtreCompetence() || this.searchText());
  }

  statutClass(s: string) {
    const map: Record<string, string> = {
      PLANIFIEE: 'status-pill--planifiee',
      EN_COURS: 'status-pill--en-cours',
      CLOTUREE: 'status-pill--cloturee',
      ANNULEE: 'status-pill--annulee',
      BROUILLON: 'status-pill--brouillon',
      SUSPENDUE: 'status-pill--suspendue',
      A_CLOTURER: 'status-pill--a-cloturer'
    };
    return map[s] || '';
  }

  statutLabel(s: string) {
    const map: Record<string, string> = {
      BROUILLON: 'Brouillon', PLANIFIEE: 'Planifiée', EN_COURS: 'En cours',
      SUSPENDUE: 'Suspendue', A_CLOTURER: 'À clôturer',
      CLOTUREE: 'Clôturée', ANNULEE: 'Annulée'
    };
    return map[s] || s;
  }

  isEnRetard(i: InterventionDTO) {
    if (i.statut === 'CLOTUREE' || i.statut === 'ANNULEE') return false;
    const now = new Date();
    if (i.statut === 'PLANIFIEE' && i.datePrevue && new Date(i.datePrevue) < now) return true;
    if (i.statut === 'EN_COURS' && i.dateFinPrevue && new Date(i.dateFinPrevue) < now) return true;
    return false;
  }

  getChefMission(i: InterventionDTO): string {
    const chef = (i.affectations || []).find(a => a.role?.toLowerCase().includes('chef'));
    return chef ? chef.nomComplet : '—';
  }

  getChargeMission(i: InterventionDTO): string {
    return i.createurNom || '—';
  }

  isChefMission(i: InterventionDTO): boolean {
    return i.monRole?.toLowerCase().includes('chef') === true;
  }

  canDemarrer(i: InterventionDTO): boolean {
    if (this.role === 'INGENIEUR') return this.isChefMission(i) && (i.statut === 'PLANIFIEE' || i.statut === 'SUSPENDUE');
    return false;
  }

  canSoumettreRapport(i: InterventionDTO): boolean {
    return this.role === 'INGENIEUR' && this.isChefMission(i) && i.statut === 'EN_COURS';
  }

  canSuspendre(i: InterventionDTO): boolean {
    if (i.statut !== 'EN_COURS') return false;
    if (this.role === 'INGENIEUR') return this.isChefMission(i);
    return this.role === 'CHARGE_MISSION' || this.role === 'ADMIN';
  }

  canReprendre(i: InterventionDTO): boolean {
    if (i.statut !== 'SUSPENDUE') return false;
    if (this.role === 'INGENIEUR') return this.isChefMission(i);
    return this.role === 'CHARGE_MISSION' || this.role === 'ADMIN';
  }

  canAnnuler(i: InterventionDTO): boolean {
    if (this.role !== 'CHARGE_MISSION' && this.role !== 'ADMIN') return false;
    return ['BROUILLON', 'PLANIFIEE', 'SUSPENDUE'].includes(i.statut);
  }

  canCloturer(i: InterventionDTO): boolean {
    return this.role === 'CHARGE_MISSION' && i.statut === 'A_CLOTURER';
  }

  demarrerIntervention(i: InterventionDTO) {
    this.service.changeStatut(i.id, 'EN_COURS').subscribe({
      next: () => { this.toast.show('Intervention démarrée', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  suspendreIntervention(i: InterventionDTO) {
    if (!confirm('Voulez-vous suspendre cette intervention ?')) return;
    this.service.changeStatut(i.id, 'SUSPENDUE').subscribe({
      next: () => { this.toast.show('Intervention suspendue', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  reprendreIntervention(i: InterventionDTO) {
    this.service.changeStatut(i.id, 'EN_COURS').subscribe({
      next: () => { this.toast.show('Intervention reprise', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  annulerIntervention(i: InterventionDTO) {
    if (!confirm('Voulez-vous annuler cette intervention ? Cette action est irréversible.')) return;
    this.service.changeStatut(i.id, 'ANNULEE').subscribe({
      next: () => { this.toast.show('Intervention annulée', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  goToRapport(i: InterventionDTO) {
    this.router.navigate(['/interventions', i.id, 'rapport']);
  }
}
