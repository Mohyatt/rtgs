import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);
  const requiredRoles: string[] = route.data['roles'] ?? [];
  const userRole = auth.getRole();
  if (requiredRoles.length > 0 && !requiredRoles.includes(userRole)) {
    toast.show('Accès refusé — rôle insuffisant', 'error');
    router.navigate(['/dashboard']);
    return false;
  }
  return true;
};
