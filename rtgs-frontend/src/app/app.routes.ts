import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./pages/login/login').then(m => m.LoginComponent) },
  { path: 'portail', loadComponent: () => import('./pages/portail/portail').then(m => m.PortailComponent) },
  {
    path: '',
    loadComponent: () => import('./layout/layout').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./pages/tableau-bord/tableau-bord').then(m => m.TableauBordComponent) },
      { path: 'alertes', canActivate: [roleGuard], data: { roles: ['ADMIN', 'CHARGE_MISSION'] }, loadComponent: () => import('./pages/alertes/alertes').then(m => m.AlertesComponent) },
      { path: 'interventions', loadComponent: () => import('./pages/interventions/interventions').then(m => m.InterventionsComponent) },
      { path: 'interventions/nouvelle', canActivate: [roleGuard], data: { roles: ['CHARGE_MISSION'] }, loadComponent: () => import('./pages/nouvelle-intervention/nouvelle-intervention').then(m => m.NouvelleInterventionComponent) },
      { path: 'interventions/:id/modifier', canActivate: [roleGuard], data: { roles: ['CHARGE_MISSION'] }, loadComponent: () => import('./pages/modifier-intervention/modifier-intervention').then(m => m.ModifierInterventionComponent) },
      { path: 'interventions/:id/affectation', canActivate: [roleGuard], data: { roles: ['CHARGE_MISSION'] }, loadComponent: () => import('./pages/affectation/affectation').then(m => m.AffectationComponent) },
      { path: 'interventions/:id/rapport', canActivate: [roleGuard], data: { roles: ['INGENIEUR'] }, loadComponent: () => import('./pages/rapport-mission/rapport-mission').then(m => m.RapportMissionComponent) },
      { path: 'interventions/:id/compte-rendu', canActivate: [roleGuard], data: { roles: ['CHARGE_MISSION'] }, loadComponent: () => import('./pages/compte-rendu/compte-rendu').then(m => m.CompteRenduComponent) },
      { path: 'tunnels', loadComponent: () => import('./pages/tunnels/tunnels').then(m => m.TunnelsComponent) },
      { path: 'rapports', canActivate: [roleGuard], data: { roles: ['ADMIN', 'CHARGE_MISSION', 'EXPLOITANT'] }, loadComponent: () => import('./pages/rapports/rapports').then(m => m.RapportsComponent) },
      { path: 'audit', canActivate: [roleGuard], data: { roles: ['ADMIN'] }, loadComponent: () => import('./pages/audit/audit').then(m => m.AuditComponent) },
      { path: 'utilisateurs', canActivate: [roleGuard], data: { roles: ['ADMIN'] }, loadComponent: () => import('./pages/utilisateurs/utilisateurs').then(m => m.UtilisateursComponent) },
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
