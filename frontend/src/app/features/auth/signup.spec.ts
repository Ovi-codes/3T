import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';

import { Signup } from './signup';

describe('Signup', () => {
  let fixture: ComponentFixture<Signup>;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Signup);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function setInput(id: string, value: string): void {
    const input = fixture.nativeElement.querySelector('#' + id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submitForm(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('blocks submission and shows field errors when empty', () => {
    submitForm();

    httpMock.expectNone('/api/auth/signup');
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Enter your email.');
    expect(text).toContain('Choose a password.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('rejects an email without a valid extension (e.g. a@a)', () => {
    setInput('email', 'a@a');
    setInput('password', 'correct horse');
    submitForm();

    httpMock.expectNone('/api/auth/signup');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Enter a valid email address.');
  });

  it('rejects a password shorter than eight characters', () => {
    setInput('email', 'ana@example.com');
    setInput('password', 'short');
    submitForm();

    httpMock.expectNone('/api/auth/signup');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Password must be at least 8 characters.',
    );
  });

  it('posts a valid signup and redirects to the dashboard', () => {
    setInput('email', 'ana@example.com');
    setInput('password', 'correct horse');
    submitForm();

    const request = httpMock.expectOne('/api/auth/signup');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 1, email: 'ana@example.com' });

    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('surfaces a taken-email error against the email field and does not redirect', () => {
    setInput('email', 'ana@example.com');
    setInput('password', 'correct horse');
    submitForm();

    httpMock.expectOne('/api/auth/signup').flush(
      { errors: { email: 'An account already exists for this email.' } },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'An account already exists for this email.',
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});
