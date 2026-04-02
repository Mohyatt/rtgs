import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { KpiDTO, ChargeIngenieurDTO } from '../models/kpi.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class TableauBordService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/tableau-bord`;

  getKpi(): Observable<KpiDTO> {
    return this.http.get<KpiDTO>(`${this.base}/kpi`);
  }

  getChargeIngenieurs(): Observable<ChargeIngenieurDTO[]> {
    return this.http.get<ChargeIngenieurDTO[]>(`${this.base}/charge-ingenieurs`);
  }

  getKpiCdm(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.base}/kpi/cdm`);
  }

  getKpiIngenieur(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.base}/kpi/ingenieur`);
  }
}
