import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { UtilisateurDTO } from '../../models/utilisateur.model';
import { ToastService } from '../../services/toast.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-utilisateurs',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './utilisateurs.html'
})
export class UtilisateursComponent implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);

  users = signal<UtilisateurDTO[]>([]);
  loading = signal(true);
  searchText = signal('');
  filtreRole = signal('');
  filtreStatut = signal('');

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

    this.http.get<UtilisateurDTO[]>(`${environment.apiUrl}/api/users`, { params }).subscribe({
      next: u => { this.users.set(u); this.loading.set(false); },
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
