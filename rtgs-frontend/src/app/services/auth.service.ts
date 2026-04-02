import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { AuthResponseDTO, LoginRequestDTO } from '../models/auth.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private readonly TOKEN_KEY = 'rtgs_token';
  private readonly USER_KEY = 'rtgs_user';

  private _user = signal<AuthResponseDTO | null>(this._loadUser());
  readonly currentUser = this._user.asReadonly();

  private _loadUser(): AuthResponseDTO | null {
    try {
      const raw = localStorage.getItem(this.USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }

  login(dto: LoginRequestDTO): Observable<AuthResponseDTO> {
    return this.http.post<AuthResponseDTO>(`${environment.apiUrl}/api/auth/login`, dto).pipe(
      tap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(res));
        this._user.set(res);
      })
    );
  }

  logout(): void {
    const user = this._user();
    this.http.post(`${environment.apiUrl}/api/auth/logout`, { userId: user?.userId }).subscribe({ error: () => {} });
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this._user.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUser(): AuthResponseDTO | null {
    return this._user();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  currentRole(): string {
    return this._user()?.role ?? '';
  }

  getRole(): string {
    return this._user()?.role ?? '';
  }

  getNomComplet(): string {
    return this._user()?.nomComplet ?? '';
  }

  getUserId(): number | null {
    return this._user()?.userId ?? null;
  }
}
