import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Guards routes that need a signed-in user (the dashboard). Scaffolded here; Increment 4 leans on
 * it fully. Three cases from the auth service's three-valued state:
 *  - known signed in  → allow
 *  - known signed out → redirect to /login
 *  - not yet checked  → ask the server (/me) once, then decide
 *
 * Redirects return a UrlTree so the router swaps navigation cleanly rather than cancelling it.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const current = auth.user();
  if (current) {
    return true;
  }
  if (current === null) {
    return router.parseUrl('/login');
  }
  return auth.loadCurrentUser().pipe(map((account) => (account ? true : router.parseUrl('/login'))));
};
