import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TunnelDTO, TunnelPublicDTO } from '../models/tunnel.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class TunnelService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/tunnels`;

  getAll(): Observable<TunnelDTO[]> {
    return this.http.get<TunnelDTO[]>(this.base);
  }

  getById(id: number): Observable<TunnelDTO> {
    return this.http.get<TunnelDTO>(`${this.base}/${id}`);
  }

  rechercher(params: any): Observable<TunnelDTO[]> {
    return this.http.get<TunnelDTO[]>(`${this.base}/recherche`, { params });
  }

  getHistorique(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/${id}/historique`);
  }

  exportCsv(id: number): Observable<string> {
    return this.http.get(`${this.base}/${id}/export-csv`, { responseType: 'text' });
  }
}
