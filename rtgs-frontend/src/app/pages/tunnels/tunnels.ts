import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TunnelService } from '../../services/tunnel.service';
import { TunnelDTO } from '../../models/tunnel.model';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-tunnels',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tunnels.html'
})
export class TunnelsComponent implements OnInit {
  private service = inject(TunnelService);
  private toast = inject(ToastService);

  tunnels = signal<TunnelDTO[]>([]);
  selected = signal<TunnelDTO | null>(null);
  historique = signal<any[]>([]);
  loading = signal(true);
  searchText = signal('');
  filtreStatut = signal('');

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.service.getAll().subscribe({
      next: t => { this.tunnels.set(t); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.show('Erreur de chargement', 'error'); }
    });
  }

  filtered() {
    let list = this.tunnels();
    const s = this.searchText().toLowerCase();
    if (s) list = list.filter(t => t.libelle.toLowerCase().includes(s) || t.departement.toLowerCase().includes(s));
    if (this.filtreStatut()) list = list.filter(t => t.statut === this.filtreStatut());
    return list;
  }

  select(t: TunnelDTO) {
    this.selected.set(t);
    this.service.getHistorique(t.id).subscribe(h => this.historique.set(h));
  }

  exportCsv(t: TunnelDTO) {
    this.service.exportCsv(t.id).subscribe(csv => {
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `tunnel-${t.id}.csv`; a.click();
      URL.revokeObjectURL(url);
    });
  }

  statutClass(t: TunnelDTO): string {
    const days = this.daysSinceVisit(t);
    if (days > 365) return 'status-pill--retard';
    if (days > 300) return 'status-pill--aplanifier';
    return 'status-pill--conforme';
  }

  statutLabel(t: TunnelDTO): string {
    const days = this.daysSinceVisit(t);
    if (days > 365) return 'En retard';
    if (days > 300) return 'À planifier';
    return 'Conforme';
  }

  daysSinceVisit(t: TunnelDTO): number {
    if (!t.dateDerniereVisite) return 999;
    const diff = Date.now() - new Date(t.dateDerniereVisite).getTime();
    return Math.floor(diff / (1000 * 60 * 60 * 24));
  }
}
