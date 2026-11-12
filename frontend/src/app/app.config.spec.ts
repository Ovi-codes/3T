import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { appConfig } from './app.config';
import { CSRF_BOOTSTRAP_URL } from './core/csrf-bootstrap.interceptor';

function clearTokenCookie(): void {
  document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
}

/**
 * The CSRF token layer, client half: the backend sets a readable `XSRF-TOKEN` cookie and expects
 * it back in `X-XSRF-TOKEN` on every write. Angular's HttpClient does that by itself for relative
 * URLs, as long as the app's HTTP setup keeps XSRF protection on. These specs pin that, using the
 * real `appConfig` providers, so a future `withNoXsrfProtection()` or a renamed cookie fails here
 * and not as a wall of 403s in production. They also cover the bootstrap interceptor, which fetches
 * a token when a write would otherwise leave without one.
 */
describe('appConfig CSRF wiring', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-from-server; path=/';
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify(); // also proves no extra token fetch went out when the cookie was there
    clearTokenCookie();
  });

  it.each([
    ['POST', (c: HttpClient) => c.post('/api/registrations', {})],
    ['PUT', (c: HttpClient) => c.put('/api/me/registrations/1/finish-time', {})],
    ['DELETE', (c: HttpClient) => c.delete('/api/me')],
  ])('echoes the token cookie in X-XSRF-TOKEN on a %s', (method, send) => {
    send(http).subscribe();

    const request = httpMock.expectOne((req) => req.method === method);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('token-from-server');
    request.flush(null);
  });

  it('does not attach the token to a GET', () => {
    http.get('/api/events').subscribe();

    const request = httpMock.expectOne('/api/events');
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush([]);
  });

  it('fetches a token first when a write would go out without one', () => {
    clearTokenCookie();
    http.post('/api/auth/login', {}).subscribe();

    // The write waits: only the token fetch is in flight.
    const fetch = httpMock.expectOne(CSRF_BOOTSTRAP_URL);
    expect(fetch.request.method).toBe('GET');
    httpMock.expectNone('/api/auth/login');

    document.cookie = 'XSRF-TOKEN=fresh-token; path=/'; // what the 204 response sets
    fetch.flush(null, { status: 204, statusText: 'No Content' });

    const write = httpMock.expectOne('/api/auth/login');
    expect(write.request.headers.get('X-XSRF-TOKEN')).toBe('fresh-token');
    write.flush({});
  });

  it('still sends the write if the token fetch fails, so the server answer is shown', () => {
    clearTokenCookie();
    http.post('/api/registrations', {}).subscribe({ error: () => undefined });

    httpMock.expectOne(CSRF_BOOTSTRAP_URL).flush(null, { status: 503, statusText: 'Unavailable' });

    httpMock.expectOne('/api/registrations').flush(null, { status: 403, statusText: 'Forbidden' });
  });

  it('never sends the token to another origin', () => {
    http.post('https://example.com/collect', {}).subscribe();

    const request = httpMock.expectOne('https://example.com/collect');
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush(null);
  });
});
