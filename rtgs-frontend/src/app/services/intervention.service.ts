import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  InterventionDTO, AffectationRequestDTO,
  CloturerRequest, CloturerResponse
} from '../models/intervention.model';
import { UtilisateurDispoDTO } from '../models/utilisateur.model';
import { ConflitPlanningDTO } from '../models/kpi.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InterventionService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/interventions`;

  getAll(statut?: string, type?: string, tunnelId?: number, dateFrom?: string, dateTo?: string): Observable<InterventionDTO[]> {
    let params = new HttpParams();
    if (statut) params = params.set('statut', statut);
    if (type) params = params.set('type', type);
    if (tunnelId) params = params.set('tunnelId', tunnelId.toString());
    if (dateFrom) params = params.set('dateFrom', dateFrom);
    if (dateTo) params = params.set('dateTo', dateTo);
    return this.http.get<InterventionDTO[]>(this.base, { params });
  }

  getById(id: number): Observable<InterventionDTO> {
    return this.http.get<InterventionDTO>(`${this.base}/${id}`);
  }

  create(dto: Partial<InterventionDTO>): Observable<InterventionDTO> {
    return this.http.post<InterventionDTO>(this.base, dto);
  }

  update(id: number, dto: Partial<InterventionDTO>): Observable<InterventionDTO> {
    return this.http.put<InterventionDTO>(`${this.base}/${id}`, dto);
  }

  changeStatut(id: number, newStatut: string): Observable<InterventionDTO> {
    return this.http.put<InterventionDTO>(`${this.base}/${id}/statut`, { newStatut });
  }

  getIntervenantsDisponibles(id: number): Observable<UtilisateurDispoDTO[]> {
    return this.http.get<UtilisateurDispoDTO[]>(`${this.base}/${id}/intervenants-disponibles`);
  }

  getIntervenantsForRemplacement(id: number, remplacerUserId: number): Observable<UtilisateurDispoDTO[]> {
    return this.http.get<UtilisateurDispoDTO[]>(`${this.base}/${id}/intervenants-disponibles/remplacer/${remplacerUserId}`);
  }

  detecterConflits(id: number, userId: number): Observable<ConflitPlanningDTO> {
    return this.http.get<ConflitPlanningDTO>(`${this.base}/${id}/conflits/${userId}`);
  }

  affecter(id: number, affectations: AffectationRequestDTO[]): Observable<InterventionDTO> {
    return this.http.post<InterventionDTO>(`${this.base}/${id}/affectations`, affectations);
  }

  cloturer(id: number, request: CloturerRequest): Observable<CloturerResponse> {
    return this.http.post<CloturerResponse>(`${this.base}/${id}/cloturer`, request);
  }

  soumettreRapport(id: number, body: { resume: string; recommandations?: string }): Observable<InterventionDTO> {
    return this.http.post<InterventionDTO>(`${this.base}/${id}/rapport`, body);
  }

  verifierConflits(id: number, datePrevue: string, dateFinPrevue?: string): Observable<InterventionDTO[]> {
    let params = new HttpParams().set('datePrevue', datePrevue);
    if (dateFinPrevue) params = params.set('dateFinPrevue', dateFinPrevue);
    return this.http.get<InterventionDTO[]>(`${this.base}/${id}/verifier-conflits`, { params });
  }

  remplacerMembre(interventionId: number, ancienUserId: number, nouvelUserId: number): Observable<InterventionDTO> {
    return this.http.put<InterventionDTO>(`${this.base}/${interventionId}/affectations/${ancienUserId}/remplacer`, { nouvelUserId });
  }

  planifierAutomatique(): Observable<{ count: number }> {
    return this.http.post<{ count: number }>(`${this.base}/planifier-automatique`, {});
  }
}
