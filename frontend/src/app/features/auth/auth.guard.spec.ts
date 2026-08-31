import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  GuardResult,
  Router,
  RouterStateSnapshot,
  provideRouter,
} from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { isObservable, lastValueFrom } from 'rxjs';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  /** Run the functional guard in an injection context and normalise its result to a Promise. */
  async function run(): Promise<GuardResult> {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return isObservable(result) ? lastValueFrom(result) : result;
  }

  it('allows a known signed-in user without asking the server', async () => {
    auth.login('ana@example.com', 'correct horse').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ id: 1, email: 'ana@example.com' });

    await expect(run()).resolves.toBe(true);
  });

  it('redirects a known signed-out user to /login', async () => {
    auth.logout().subscribe();
    httpMock.expectOne('/api/auth/logout').flush(null);

    const result = await run();
    expect(result).toEqual(router.parseUrl('/login'));
  });

  it('checks the server once when the session is unknown, then allows', async () => {
    const pending = run();
    httpMock.expectOne('/api/auth/me').flush({ id: 1, email: 'ana@example.com' });

    await expect(pending).resolves.toBe(true);
  });

  it('redirects to /login when the unknown session turns out to be signed out', async () => {
    const pending = run();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await pending).toEqual(router.parseUrl('/login'));
  });
});
