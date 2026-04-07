import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { UtilisateurDTO } from '../../models/utilisateur.model';
import { ToastService } from '../../services/toast.service';
import { environment } from '../../../environments/environment';
import { forkJoin } from 'rxjs';
import { SumFieldPipe } from '../../shared/pipes/sum-field.pipe';
import { FilterRolePipe } from '../../shared/pipes/filter-role.pipe';

interface UserPerformanceDTO {
  id: number;
  nomComplet: string;
  email: string;
  role: string;
  statut: string;
  pole?: string;
  missionsActives: number;
  missionsCloturees: number;
  missionsEnRetard: number;
  missionsTotal: number;
  tauxOccupation: number;
  niveauCharge?: string;
  missionsChefActives?: number;
  missionsIntervenantActives?: number;
  alertesEnAttente?: number;
  alertesTraitees?: number;
  alertesTotal?: number;
  tauxTraitementAlertes?: number;
  tauxCloture?: number;
  interventionsACloturer?: number;
}

@Component({
  selector: 'app-utilisateurs',
  standalone: true,
  imports: [CommonModule, SumFieldPipe, FilterRolePipe],
  templateUrl: './utilisateurs.html'
})
export class UtilisateursComponent implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);

  users = signal<UtilisateurDTO[]>([]);
  performances = signal<UserPerformanceDTO[]>([]);
  loading = signal(true);
  searchText = signal('');
  filtreRole = signal('');
  filtreStatut = signal('');
  activeTab = signal<'liste' | 'performances'>('liste');

  roles = [
    { value: 'ADMIN', label: 'Administrateur' },
    { value: 'CHARGE_MISSION', label: 'Chargé de mission' },
    { value: 'INGENIEUR', label: 'Ingénieur' },
    { value: 'EXPLOITANT', label: 'Exploitant' },
    { value: 'EXTERNE', label: 'Externe' },
  ];

  statuts = [
    { value: 'ACTIF', label: 'Actif' },
    { value: 'INDISPONIBLE', label: 'Indisponible' },
    { value: 'SUSPENDU', label: 'Suspendu' },
    { value: 'DESACTIVE', label: 'Désactivé' },
  ];

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    let params = new HttpParams();
    if (this.filtreRole()) params = params.set('role', this.filtreRole());
    if (this.filtreStatut()) params = params.set('statut', this.filtreStatut());

    forkJoin({
      users: this.http.get<UtilisateurDTO[]>(`${environment.apiUrl}/api/users`, { params }),
      perfs: this.http.get<UserPerformanceDTO[]>(`${environment.apiUrl}/api/tableau-bord/performances`)
    }).subscribe({
      next: ({ users, perfs }) => {
        this.users.set(users);
        this.performances.set(perfs);
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  filtered() {
    const s = this.searchText().toLowerCase();
    if (!s) return this.users();
    return this.users().filter(u =>
      u.nomComplet.toLowerCase().includes(s) ||
      u.email.toLowerCase().includes(s) ||
      u.pole?.toLowerCase().includes(s) ||
      u.competences?.toLowerCase().includes(s));
  }

  filteredPerfs() {
    const s = this.searchText().toLowerCase();
    const r = this.filtreRole();
    return this.performances().filter(p => {
      const matchSearch = !s || p.nomComplet.toLowerCase().includes(s) || p.email.toLowerCase().includes(s);
      const matchRole = !r || p.role === r;
      return matchSearch && matchRole;
    });
  }

  setFiltre(field: string, value: string) {
    if (field === 'role') this.filtreRole.set(value);
    if (field === 'statut') this.filtreStatut.set(value);
    this.load();
  }

  resetFilters() {
    this.searchText.set('');
    this.filtreRole.set('');
    this.filtreStatut.set('');
    this.load();
  }

  get hasActiveFilters(): boolean {
    return !!(this.searchText() || this.filtreRole() || this.filtreStatut());
  }

  roleLabel(role: string): string {
    return this.roles.find(r => r.value === role)?.label || role;
  }

  statutLabel(statut: string): string {
    return this.statuts.find(s => s.value === statut)?.label || statut;
  }

  statutClass(statut: string): string {
    const map: Record<string, string> = {
      ACTIF: 'status-pill--actif',
      INDISPONIBLE: 'status-pill--indisponible',
      SUSPENDU: 'status-pill--suspendu',
      DESACTIVE: 'status-pill--desactive'
    };
    return map[statut] || '';
  }

  niveauClass(n?: string): string {
    if (n === 'ELEVE') return 'badge-red';
    if (n === 'MOYEN') return 'badge-orange';
    return 'badge-green';
  }

  tauxClass(taux: number): string {
    if (taux >= 80) return 'badge-red';
    if (taux >= 50) return 'badge-orange';
    return 'badge-green';
  }

  isBlocked(u: UtilisateurDTO): boolean {
    return u.statut === 'SUSPENDU' || u.statut === 'DESACTIVE';
  }

  toggleBlock(u: UtilisateurDTO) {
    const newStatut = this.isBlocked(u) ? 'ACTIF' : 'SUSPENDU';
    const params = new HttpParams().set('statut', newStatut);
    this.http.patch<UtilisateurDTO>(`${environment.apiUrl}/api/users/${u.id}/statut`, {}, { params }).subscribe({
      next: updated => {
        this.users.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.toast.show(
          newStatut === 'SUSPENDU'
            ? `Accès de ${u.nomComplet} bloqué`
            : `Accès de ${u.nomComplet} rétabli`,
          'success'
        );
      },
      error: () => this.toast.show('Erreur lors de la mise à jour', 'error')
    });
  }
}
