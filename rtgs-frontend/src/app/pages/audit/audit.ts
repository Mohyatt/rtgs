import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface AuditLogDTO {
  id: number;
  typeAction: string;
  typeObjet: string;
  idObjet: number;
  details: string;
  dateHeure: string;
  utilisateur?: { nomComplet?: string; nom?: string; prenom?: string };
}

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit.html'
})
export class AuditComponent implements OnInit {
  private http = inject(HttpClient);

  logs = signal<AuditLogDTO[]>([]);
  loading = signal(true);
  filtreType = signal('');
  searchText = signal('');

  typeActions = ['TUNNEL', 'INTERVENTION', 'UTILISATEUR'];

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    let params = new HttpParams();
    if (this.filtreType()) params = params.set('typeObjet', this.filtreType());
    this.http.get<AuditLogDTO[]>(`${environment.apiUrl}/api/audit-logs`, { params }).subscribe({
      next: l => { this.logs.set(l); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  filtered() {
    const s = this.searchText().toLowerCase();
    return s ? this.logs().filter(l => l.details?.toLowerCase().includes(s) || l.typeAction?.toLowerCase().includes(s)) : this.logs();
  }

  actionClass(a: string) {
    if (a.startsWith('ALERTE_CRITIQUE')) return 'tag-critique';
    if (a.startsWith('ALERTE')) return 'tag-preventif';
    if (a === 'CLOTURE') return 'tag-cloture';
    if (a === 'CONNEXION') return 'tag-info';
    return 'tag-default';
  }
}
