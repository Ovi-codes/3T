import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts with an unknown (undefined) user until checked', () => {
    expect(service.user()).toBeUndefined();
  });

  it('signup posts credentials and records the returned account', () => {
    service.signup('ana@example.com', 'correct horse').subscribe();

    const request = httpMock.expectOne('/api/auth/signup');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@example.com', password: 'correct horse' });
    request.flush({ id: 1, email: 'ana@example.com' });

    expect(service.user()).toEqual({ id: 1, email: 'ana@example.com' });
    expect(service.isSignedIn()).toBe(true);
  });

  it('login records the account', () => {
    service.login('ana@example.com', 'correct horse').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ id: 1, email: 'ana@example.com' });

    expect(service.user()).toEqual({ id: 1, email: 'ana@example.com' });
  });

  it('logout clears the account', () => {
    service.login('ana@example.com', 'correct horse').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ id: 1, email: 'ana@example.com' });

    service.logout().subscribe();
    httpMock.expectOne('/api/auth/logout').flush(null);

    expect(service.user()).toBeNull();
    expect(service.isSignedIn()).toBe(false);
  });

  it('loadCurrentUser resolves an active session', () => {
    service.loadCurrentUser().subscribe();
    httpMock.expectOne('/api/auth/me').flush({ id: 7, email: 'ana@example.com' });

    expect(service.user()).toEqual({ id: 7, email: 'ana@example.com' });
  });

  it('loadCurrentUser treats a 401 as signed-out, not an error', () => {
    let emitted: unknown = 'unset';
    service.loadCurrentUser().subscribe((account) => (emitted = account));
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(emitted).toBeNull();
    expect(service.user()).toBeNull();
  });
});
