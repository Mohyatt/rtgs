import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './layout.html'
})
export class LayoutComponent implements OnInit, OnDestroy {
  private auth = inject(AuthService);
  private router = inject(Router);

  currentTime = signal('');
  currentDate = signal('');
  sidebarOpen = signal(typeof window !== 'undefined' ? window.innerWidth > 768 : true);
  notifOpen = signal(false);

  private clockInterval?: ReturnType<typeof setInterval>;

  user = this.auth.currentUser;

  role = computed(() => this.auth.currentRole());

  roleLabel = computed(() => {
    const map: Record<string, string> = {
      ADMIN: 'Administrateur',
      CHARGE_MISSION: 'Chargé de Mission',
      INGENIEUR: 'Ingénieur',
      EXPLOITANT: 'Exploitant',
      EXTERNE: 'Externe'
    };
    return map[this.role()] || this.role();
  });

  roleCode = computed(() => {
    const map: Record<string, string> = {
      ADMIN: 'ADM',
      CHARGE_MISSION: 'CDM',
      INGENIEUR: 'ING',
      EXPLOITANT: 'EXP',
      EXTERNE: 'EXT'
    };
    return map[this.role()] || 'USR';
  });

  userInitials = computed(() => {
    const name = this.user()?.nomComplet || '';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name[0]?.toUpperCase() || 'U';
  });

  navItems = [
    { path: '/dashboard', icon: 'dashboard', label: 'Tableau de bord', roles: ['ADMIN', 'CHARGE_MISSION', 'INGENIEUR', 'EXPLOITANT'] },
    { path: '/alertes', icon: 'warning', label: 'Alertes', roles: ['ADMIN', 'CHARGE_MISSION'] },
    { path: '/interventions', icon: 'engineering', label: 'Interventions', roles: ['ADMIN', 'CHARGE_MISSION', 'INGENIEUR', 'EXPLOITANT'] },
    { path: '/tunnels', icon: 'route', label: 'Tunnels', roles: ['ADMIN', 'CHARGE_MISSION', 'INGENIEUR', 'EXPLOITANT'] },
    { path: '/rapports', icon: 'description', label: 'Rapports', roles: ['ADMIN', 'CHARGE_MISSION', 'EXPLOITANT'] },
    { path: '/utilisateurs', icon: 'group', label: 'Utilisateurs', roles: ['ADMIN'] },
    { path: '/audit', icon: 'history', label: "Journal d'audit", roles: ['ADMIN'] },
  ];

  visibleNavItems = computed(() =>
    this.navItems.filter(item => item.roles.includes(this.role()))
  );

  ngOnInit() {
    this.updateClock();
    this.clockInterval = setInterval(() => this.updateClock(), 1000);
  }

  ngOnDestroy() {
    if (this.clockInterval) clearInterval(this.clockInterval);
  }

  private updateClock() {
    const now = new Date();
    this.currentTime.set(now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    this.currentDate.set(now.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }));
  }

  toggleSidebar() { this.sidebarOpen.update(v => !v); }
  toggleNotif() { this.notifOpen.update(v => !v); }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
