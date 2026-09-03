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

  it('offers a skip-to-content link as the first focusable element (a11y)', () => {
    settleSession('signed-out');

    const el = fixture.nativeElement as HTMLElement;
    const skip = el.querySelector<HTMLAnchorElement>('.skip-link');
    expect(skip).not.toBeNull();
    // Targets the page's main landmark and sits before the header in the DOM (so, in tab order).
    expect(skip!.getAttribute('href')).toBe('#main-content');
    expect(skip!.compareDocumentPosition(el.querySelector('.app-header')!))
      .toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('always links to the privacy policy in the footer', () => {
    settleSession('signed-out');

    const link = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '[data-testid="footer-privacy"]',
    );
    expect(link).not.toBeNull();
    expect(link!.getAttribute('href')).toBe('/privacy');
  });
});
