import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlerteDTO, AlerteAssignmentDTO, CdmPerformanceDTO } from '../models/alerte.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AlerteService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/alertes`;

  getAll(niveau?: string): Observable<AlerteDTO[]> {
    const params = niveau ? `?niveau=${niveau}` : '';
    return this.http.get<AlerteDTO[]>(`${this.base}${params}`);
  }

  calculer(): Observable<AlerteDTO[]> {
    return this.http.post<AlerteDTO[]>(`${this.base}/calculer`, {});
  }

  traiter(id: number, body: { commentaire?: string; chargeMissionId?: number }): Observable<AlerteDTO> {
    return this.http.put<AlerteDTO>(`${this.base}/${id}/traiter`, body);
  }

  traiterCdm(id: number, commentaire?: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/traiter-cdm`, { commentaire });
  }

  getMesAlertes(): Observable<AlerteAssignmentDTO[]> {
    return this.http.get<AlerteAssignmentDTO[]>(`${this.base}/mes-alertes`);
  }

  getMesAlertesEnAttente(): Observable<AlerteAssignmentDTO[]> {
    return this.http.get<AlerteAssignmentDTO[]>(`${this.base}/mes-alertes/en-attente`);
  }

  lierIntervention(assignmentId: number, interventionId: number): Observable<AlerteAssignmentDTO> {
    return this.http.put<AlerteAssignmentDTO>(
      `${this.base}/assignments/${assignmentId}/lier-intervention?interventionId=${interventionId}`, {}
    );
  }

  getPerformance(): Observable<CdmPerformanceDTO> {
    return this.http.get<CdmPerformanceDTO>(`${this.base}/performance`);
  }
}
