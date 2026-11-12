import {
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, filter, of, switchMap, take } from 'rxjs';

/** Public backend endpoint whose only job is to set the `XSRF-TOKEN` cookie (204, no body). */
export const CSRF_BOOTSTRAP_URL = '/api/auth/csrf';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** The header Angular's XSRF interceptor uses, and the one Spring's `csrf.spa()` reads. */
const XSRF_HEADER = 'X-XSRF-TOKEN';

/**
 * Makes sure a write never leaves without a CSRF token. The backend sets the `XSRF-TOKEN` cookie on
 * every response, and Angular's built-in XSRF interceptor copies it into `X-XSRF-TOKEN`. But a
 * write can go out before the first response lands (a fast submit, or a slow cold start), and then
 * it has no token and gets a 403. In that case, this interceptor fetches a token first, then sends
 * the write. When the cookie is already there (the usual case), it adds nothing.
 *
 * Angular's XSRF interceptor has already run by the time this one does (it is registered first),
 * so after the fetch this interceptor sets the header itself. Absolute URLs are left alone: the
 * token is only ever sent to our own origin.
 */
export const csrfBootstrapInterceptor: HttpInterceptorFn = (req, next) => {
  if (SAFE_METHODS.has(req.method) || /^https?:\/\//i.test(req.url)) {
    return next(req);
  }
  const tokens = inject(HttpXsrfTokenExtractor);
  if (tokens.getToken() !== null) {
    return next(req);
  }
  return next(new HttpRequest('GET', CSRF_BOOTSTRAP_URL)).pipe(
    filter((event) => event instanceof HttpResponse),
    take(1),
    // If the fetch fails, send the write anyway; the server's answer is the one to show.
    catchError(() => of(null)),
    switchMap(() => {
      const token = tokens.getToken();
      return next(token === null ? req : req.clone({ setHeaders: { [XSRF_HEADER]: token } }));
    }),
  );
};
