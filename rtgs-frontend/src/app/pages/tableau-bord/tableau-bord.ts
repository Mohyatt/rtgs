import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TableauBordService } from '../../services/tableau-bord.service';
import { AlerteService } from '../../services/alerte.service';
import { InterventionService } from '../../services/intervention.service';
import { KpiDTO, ChargeIngenieurDTO } from '../../models/kpi.model';
import { AlerteDTO, AlerteAssignmentDTO } from '../../models/alerte.model';
import { InterventionDTO } from '../../models/intervention.model';
import { UtilisateurDTO } from '../../models/utilisateur.model';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { environment } from '../../../environments/environment';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
  selector: 'app-tableau-bord',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './tableau-bord.html'
})
export class TableauBordComponent implements OnInit, OnDestroy {
  private tbService = inject(TableauBordService);
  private alerteService = inject(AlerteService);
  private interventionService = inject(InterventionService);
  private auth = inject(AuthService);
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  private router = inject(Router);

  kpi = signal<KpiDTO | null>(null);
  cdmKpi = signal<Record<string, number> | null>(null);
  ingKpi = signal<Record<string, number> | null>(null);
  alertes = signal<AlerteDTO[]>([]);
  interventions = signal<InterventionDTO[]>([]);
  charge = signal<ChargeIngenieurDTO[]>([]);
  mesAlertesAssignees = signal<AlerteAssignmentDTO[]>([]);
  loading = signal(true);
  mesMissionsActives = signal(0);
  userStatut = signal<string | null>(null);

  showIndispoModal = signal(false);
  indispoMotif = '';
  indispoDateRetour = '';
  indispoLoading = signal(false);
  indispoResult = signal<any>(null);

  private refreshInterval?: ReturnType<typeof setInterval>;

  get role() { return this.auth.currentRole(); }
  get isAdmin() { return this.role === 'ADMIN'; }
  get isChargeMission() { return this.role === 'CHARGE_MISSION'; }
  get isIngenieur() { return this.role === 'INGENIEUR'; }
  get isExploitant() { return this.role === 'EXPLOITANT'; }
  get isAdminOrCdm() { return this.isAdmin || this.isChargeMission; }

  mesMissionsCount(): number {
    const k = this.kpi() as (KpiDTO & { mesMissionsActives?: number }) | null;
    if (k != null && typeof k.mesMissionsActives === 'number') return k.mesMissionsActives;
    return this.mesMissionsActives();
  }

  ngOnInit() {
    this.load();
    this.refreshInterval = setInterval(() => this.load(), 60000);
  }

  ngOnDestroy() {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  load() {
    const chargeObs = this.isAdminOrCdm
      ? this.tbService.getChargeIngenieurs().pipe(catchError(() => of([])))
      : of([]);

    const meObs = this.isIngenieur
      ? this.http.get<UtilisateurDTO>(`${environment.apiUrl}/api/users/me`).pipe(catchError(() => of<UtilisateurDTO | null>(null)))
      : of<UtilisateurDTO | null>(null);

    const mesAlertesObs = this.isChargeMission
      ? this.alerteService.getMesAlertesEnAttente().pipe(catchError(() => of([])))
      : of([]);

    const cdmKpiObs = this.isChargeMission
      ? this.tbService.getKpiCdm().pipe(catchError(() => of(null)))
      : of(null);

    const ingKpiObs = this.isIngenieur
      ? this.tbService.getKpiIngenieur().pipe(catchError(() => of(null)))
      : of(null);

    forkJoin({
      kpi: this.tbService.getKpi().pipe(catchError(() => of(null))),
      alertes: this.alerteService.getAll().pipe(catchError(() => of([]))),
      interventions: this.interventionService.getAll().pipe(catchError(() => of([]))),
      charge: chargeObs,
      me: meObs,
      mesAlertes: mesAlertesObs,
      cdmKpi: cdmKpiObs,
      ingKpi: ingKpiObs
    }).subscribe(({ kpi, alertes, interventions, charge, me, mesAlertes, cdmKpi, ingKpi }) => {
      this.kpi.set(kpi);
      this.alertes.set((alertes as AlerteDTO[]).slice(0, 5));
      const intervList = interventions as InterventionDTO[];
      this.interventions.set(intervList.slice(0, 8));
      if (this.isIngenieur) {
        this.userStatut.set(me?.statut ?? null);
      } else {
        this.userStatut.set(null);
      }
      this.charge.set(charge as ChargeIngenieurDTO[]);
      this.mesAlertesAssignees.set(mesAlertes as AlerteAssignmentDTO[]);
      this.cdmKpi.set(cdmKpi as Record<string, number> | null);
      this.ingKpi.set(ingKpi as Record<string, number> | null);
      this.loading.set(false);
    });
  }

  signalerDisponibilite() {
    const cur = this.userStatut();
    if (cur !== 'INDISPONIBLE') {
      this.showIndispoModal.set(true);
      this.indispoMotif = '';
      this.indispoDateRetour = '';
      this.indispoResult.set(null);
    } else {
      this.envoyerDisponibilite('ACTIF', '', '');
    }
  }

  confirmerIndisponibilite() {
    this.envoyerDisponibilite('INDISPONIBLE', this.indispoMotif, this.indispoDateRetour);
  }

  fermerIndispoModal() {
    this.showIndispoModal.set(false);
    this.indispoResult.set(null);
  }

  private envoyerDisponibilite(statut: string, motif: string, dateRetour: string) {
    this.indispoLoading.set(true);
    let params = new HttpParams().set('statut', statut);
    if (motif) params = params.set('motif', motif);
    if (dateRetour) params = params.set('dateRetour', dateRetour);

    this.http.patch<any>(`${environment.apiUrl}/api/users/me/disponibilite`, {}, { params }).subscribe({
      next: res => {
        this.indispoLoading.set(false);
        this.userStatut.set(res.utilisateur?.statut || statut);
        if (statut === 'INDISPONIBLE' && res.nbMissionsSuspendues > 0) {
          this.indispoResult.set(res);
          this.toast.show(
            `Indisponibilité enregistrée — ${res.nbMissionsSuspendues} mission(s) suspendue(s)`,
            'success'
          );
        } else {
          this.showIndispoModal.set(false);
          this.toast.show(
            statut === 'ACTIF' ? 'Vous êtes déclaré disponible' : 'Indisponibilité enregistrée',
            'success'
          );
        }
        this.load();
      },
      error: () => {
        this.indispoLoading.set(false);
        this.toast.show('Impossible de mettre à jour la disponibilité', 'error');
      }
    });
  }

  niveauClass(n: string) {
    return n === 'CRITIQUE' ? 'alert-badge--critique' : n === 'PREVENTIF' ? 'alert-badge--preventif' : 'alert-badge--info';
  }

  statutClass(s: string) {
    const map: Record<string, string> = {
      BROUILLON: 'status-pill--brouillon', PLANIFIEE: 'status-pill--planifiee',
      EN_COURS: 'status-pill--en-cours', A_CLOTURER: 'status-pill--a-cloturer',
      CLOTUREE: 'status-pill--cloturee', ANNULEE: 'status-pill--annulee'
    };
    return map[s] || '';
  }

  statutLabel(s: string) {
    const map: Record<string, string> = {
      BROUILLON: 'Brouillon', PLANIFIEE: 'Planifiée', EN_COURS: 'En cours',
      A_CLOTURER: 'À clôturer', CLOTUREE: 'Clôturée', ANNULEE: 'Annulée'
    };
    return map[s] || s;
  }

  donutCircumference = 2 * Math.PI * 54;
  donutOffset(pct: number) {
    return this.donutCircumference - (pct / 100) * this.donutCircumference;
  }

  chargeBarWidth(n: number, max: number) {
    return max > 0 ? Math.min(100, (n / max) * 100) : 0;
  }

  maxCharge() {
    return Math.max(1, ...this.charge().map(c => c.nbMissionsActives));
  }

  chargeClass(n: string) {
    return n === 'ELEVE' ? 'charge-bar--eleve' : n === 'MOYEN' ? 'charge-bar--moyen' : 'charge-bar--ok';
  }

  missionsChef() {
    return this.interventions().filter(i =>
      i.monRole && i.monRole.toLowerCase().includes('chef'));
  }

  missionsIntervenant() {
    return this.interventions().filter(i =>
      i.monRole && !i.monRole.toLowerCase().includes('chef'));
  }

  interventionsACloturer() {
    return this.interventions().filter(i => i.statut === 'A_CLOTURER');
  }

  goToCloturer(i: InterventionDTO) {
    this.router.navigate(['/interventions', i.id, 'compte-rendu']);
  }

  demarrerIntervention(i: InterventionDTO) {
    this.interventionService.changeStatut(i.id, 'EN_COURS').subscribe({
      next: () => {
        this.toast.show('Intervention démarrée', 'success');
        this.load();
      },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  suspendreIntervention(i: InterventionDTO) {
    if (!confirm('Voulez-vous suspendre cette intervention ?')) return;
    this.interventionService.changeStatut(i.id, 'SUSPENDUE').subscribe({
      next: () => { this.toast.show('Intervention suspendue', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  reprendreIntervention(i: InterventionDTO) {
    this.interventionService.changeStatut(i.id, 'EN_COURS').subscribe({
      next: () => { this.toast.show('Intervention reprise', 'success'); this.load(); },
      error: (err: any) => this.toast.show(err.error?.message || 'Erreur', 'error')
    });
  }

  goToRapport(i: InterventionDTO) {
    this.router.navigate(['/interventions', i.id, 'rapport']);
  }

  goToAlertes(niveau?: string) {
    this.router.navigate(['/alertes'], niveau ? { queryParams: { niveau } } : {});
  }

  goToInterventions(queryParams?: Record<string, string>) {
    this.router.navigate(['/interventions'], queryParams ? { queryParams } : {});
  }

  creerInterventionDepuisAlerte(a: AlerteAssignmentDTO) {
    this.router.navigate(['/interventions/nouvelle'], {
      queryParams: {
        alerteAssignmentId: a.id,
        objetType: a.objetType,
        objetId: a.objetId,
        objetLibelle: a.objetLibelle,
        niveau: a.niveau,
        description: a.description
      }
    });
  }

  goToInterventionsMois() {
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();
    const to = new Date(y, m + 1, 0);
    const pad = (n: number) => n.toString().padStart(2, '0');
    this.router.navigate(['/interventions'], {
      queryParams: {
        dateFrom: `${y}-${pad(m + 1)}-01`,
        dateTo: `${y}-${pad(m + 1)}-${pad(to.getDate())}`
      }
    });
  }
}
