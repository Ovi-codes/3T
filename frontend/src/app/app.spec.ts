import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { App } from './app';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** The shell checks the session once on startup; answer that request with the given state. */
  function settleSession(state: { id: number; email: string } | 'signed-out'): void {
    const request = httpMock.expectOne('/api/auth/me');
    if (state === 'signed-out') {
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
    } else {
      request.flush(state);
    }
    fixture.detectChanges();
  }

  it('should create the app', () => {
    expect(fixture.componentInstance).toBeTruthy();
    settleSession('signed-out');
  });

  it('shows a "Log in" action when signed out', () => {
    settleSession('signed-out');

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="nav-login"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="nav-dashboard"]')).toBeNull();
  });

  it('shows a "My dashboard" action when signed in', () => {
    settleSession({ id: 1, email: 'ana@example.com' });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="nav-dashboard"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="nav-login"]')).toBeNull();
  });
});
