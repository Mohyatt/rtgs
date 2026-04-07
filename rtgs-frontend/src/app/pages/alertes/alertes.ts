import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { AlerteService } from '../../services/alerte.service';
import { AlerteDTO, AlerteAssignmentDTO } from '../../models/alerte.model';
import { ToastService } from '../../services/toast.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog';
import { environment } from '../../../environments/environment';
import { UtilisateurDTO } from '../../models/utilisateur.model';


@Component({
  selector: 'app-alertes',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './alertes.html'
})
export class AlertesComponent implements OnInit {
  private alerteService = inject(AlerteService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private dialog = inject(MatDialog);
  private http = inject(HttpClient);
  private router = inject(Router);

  alertes = signal<AlerteDTO[]>([]);
  mesAlertes = signal<AlerteAssignmentDTO[]>([]);
  filtreNiveau = signal<string>('');
  loading = signal(true);
  activeTab = signal<'systeme' | 'mes-alertes'>('systeme');

  get canTraiter() { return this.auth.currentRole() === 'ADMIN' || this.auth.currentRole() === 'CHARGE_MISSION'; }
  get isAdmin() { return this.auth.currentRole() === 'ADMIN'; }
  get isCdm() { return this.auth.currentRole() === 'CHARGE_MISSION'; }

  alertesFiltrees = computed(() => {
    const f = this.filtreNiveau();
    return f ? this.alertes().filter(a => a.niveau === f || (f === 'PREVENTIF' && a.niveau === 'PREVENTIF_RETARD')) : this.alertes();
  });

  critiques = computed(() => this.alertes().filter(a => a.niveau === 'CRITIQUE' || a.niveau === 'NC_CRITIQUE').length);
  ncCritiques = computed(() => this.alertes().filter(a => a.niveau === 'NC_CRITIQUE').length);
  preventives = computed(() => this.alertes().filter(a => a.niveau.startsWith('PREVENTIF')).length);
  infos = computed(() => this.alertes().filter(a => a.niveau === 'INFO').length);
  conflits = computed(() => this.alertes().filter(a => a.niveau === 'CONFLIT').length);

  ngOnInit() {
    this.load();
    if (this.isCdm) {
      this.activeTab.set('mes-alertes');
      this.loadMesAlertes();
    }
  }

  load() {
    this.loading.set(true);
    this.alerteService.getAll().subscribe({
      next: a => { this.alertes.set(a); this.loading.set(false); },
      error: () => { this.loading.set(false); }
    });
  }

  loadMesAlertes() {
    this.alerteService.getMesAlertes().subscribe({
      next: a => this.mesAlertes.set(a),
      error: () => {}
    });
  }

  creerInterventionDepuisAlerte(a: AlerteAssignmentDTO) {
    if (a.niveau === 'NC_CRITIQUE' && a.interventionId) {
      // L'intervention corrective existe déjà en BROUILLON → aller affecter l'équipe
      this.router.navigate(['/interventions', a.interventionId, 'affectation']);
    } else {
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
  }

  assignmentStatutClass(s: string) {
    return s === 'ASSIGNEE' ? 'status-pill--en-cours' : 'status-pill--cloturee';
  }

  assignmentStatutLabel(s: string) {
    return s === 'ASSIGNEE' ? 'À traiter' : 'Traitée';
  }

  traiter(alerte: AlerteDTO) {
    if (this.isAdmin) {
      this.traiterAdmin(alerte);
    } else {
      this.traiterCdmDirect(alerte);
    }
  }

  private traiterAdmin(alerte: AlerteDTO) {
    const isNcCritique = alerte.niveau === 'NC_CRITIQUE';

    this.http.get<any[]>(`${environment.apiUrl}/api/alertes/charges-mission`).subscribe({
      next: cdms => {
        const chargeMissionOptions = cdms.map(c => ({
          id: c.id, nomComplet: c.nomComplet, email: c.email,
          interventionsActives: c.interventionsActives,
          interventionsACloturer: c.interventionsACloturer,
          totalEnCharge: c.totalEnCharge,
          pourcentageCharge: c.pourcentageCharge
        }));

        const ref = this.dialog.open(ConfirmDialogComponent, {
          data: {
            title: isNcCritique ? 'Assigner l\'intervention corrective' : 'Traiter l\'alerte critique',
            message: isNcCritique
              ? `Une intervention corrective a été créée automatiquement. Désignez le chargé de mission qui doit la planifier et la traiter.`
              : `Sélectionnez le chargé de mission à affecter pour traiter cette alerte.`,
            requireComment: true,
            confirmLabel: 'Assigner et traiter',
            danger: false,
            chargeMissionOptions,
            showChargeInfo: true
          },
          panelClass: 'rtgs-dialog'
        });
        ref.afterClosed().subscribe(result => {
          if (result?.confirmed) {
            this.alerteService.traiter(alerte.id, {
              commentaire: result.comment,
              chargeMissionId: result.chargeMissionId ?? undefined
            }).subscribe({
              next: () => { this.toast.show('Alerte assignée au chargé de mission', 'success'); this.load(); },
              error: (err) => this.toast.show(err.error?.message || 'Erreur lors du traitement', 'error')
            });
          }
        });
      },
      error: () => this.toast.show('Impossible de charger les chargés de mission', 'error')
    });
  }

  private traiterCdmDirect(alerte: AlerteDTO) {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Acquitter l\'alerte',
        message: `Confirmez-vous avoir pris en compte cette alerte et que des actions correctives sont en cours ?`,
        requireComment: false,
        confirmLabel: 'Acquitter',
        danger: false
      },
      panelClass: 'rtgs-dialog'
    });
    ref.afterClosed().subscribe(result => {
      if (result?.confirmed) {
        this.alerteService.traiterCdm(alerte.id, result.comment).subscribe({
          next: () => { this.toast.show('Alerte acquittée', 'success'); this.load(); },
          error: (err) => this.toast.show(err.error?.message || 'Erreur', 'error')
        });
      }
    });
  }

  calculer() {
    this.alerteService.calculer().subscribe({
      next: () => { this.toast.show('Recalcul des alertes effectué', 'success'); this.load(); },
      error: () => this.toast.show('Erreur lors du calcul', 'error')
    });
  }

  niveauClass(n: string) {
    if (n === 'CRITIQUE' || n === 'NC_CRITIQUE') return 'alert-badge--critique';
    if (n.startsWith('PREVENTIF')) return 'alert-badge--preventif';
    if (n === 'CONFLIT') return 'alert-badge--conflit';
    return 'alert-badge--info';
  }

  niveauLabel(n: string) {
    if (n === 'NC_CRITIQUE') return 'NC CRITIQUE';
    return n;
  }

  objetClass(t: string) {
    return t === 'TUNNEL' ? 'tag-tunnel' : 'tag-intervention';
  }
}
