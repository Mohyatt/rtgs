import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../services/toast.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-rapports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './rapports.html'
})
export class RapportsComponent {
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  private fb = inject(FormBuilder);

  generating = signal(false);

  form = this.fb.group({
    dateDebut: [''],
    dateFin: [''],
    tunnelId: [''],
    type: ['INTERVENTIONS'],
  });

  types = [
    { value: 'INTERVENTIONS', label: 'Rapport d\'interventions' },
    { value: 'CONFORMITE', label: 'Rapport de conformité' },
    { value: 'NON_CONFORMITES', label: 'Rapport de non-conformités' },
    { value: 'ACTIVITE', label: "Rapport d'activité global" },
  ];

  generer() {
    this.generating.set(true);
    const v = this.form.value;
    const params: any = { type: v.type };
    if (v.dateDebut) params.dateDebut = v.dateDebut;
    if (v.dateFin) params.dateFin = v.dateFin;
    if (v.tunnelId) params.tunnelId = v.tunnelId;

    this.http.get(`${environment.apiUrl}/api/rapports/pdf`, {
      params, responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        this.generating.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `rapport-${v.type?.toLowerCase()}-${new Date().toISOString().slice(0,10)}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.toast.show('Rapport PDF généré', 'success');
      },
      error: () => {
        this.generating.set(false);
        this.toast.show('Erreur lors de la génération du rapport', 'error');
      }
    });
  }
}
