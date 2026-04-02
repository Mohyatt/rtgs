import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TunnelPublicDTO } from '../../models/tunnel.model';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-portail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portail.html'
})
export class PortailComponent implements OnInit {
  private http = inject(HttpClient);

  tunnels = signal<TunnelPublicDTO[]>([]);
  loading = signal(true);
  searchText = signal('');

  ngOnInit() {
    this.http.get<TunnelPublicDTO[]>(`${environment.apiUrl}/api/portail/tunnels`).subscribe({
      next: t => { this.tunnels.set(t); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  filtered() {
    const s = this.searchText().toLowerCase();
    return s ? this.tunnels().filter(t => t.libelle.toLowerCase().includes(s) || t.departement.toLowerCase().includes(s)) : this.tunnels();
  }

  conformiteClass(s: string) {
    return s === 'CONFORME' ? 'status-pill--conforme' : s === 'EN_RETARD' ? 'status-pill--retard' : 'status-pill--aplanifier';
  }

  conformiteLabel(s: string) {
    return s === 'CONFORME' ? 'Conforme' : s === 'EN_RETARD' ? 'En retard' : 'À planifier';
  }
}
