import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';

import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
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

  it('blocks submission and shows required errors when empty', () => {
    submitForm();

    httpMock.expectNone('/api/auth/login');
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Enter your email.');
    expect(text).toContain('Enter your password.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('posts valid credentials and redirects to the dashboard', () => {
    setInput('email', 'ana@example.com');
    setInput('password', 'correct horse');
    submitForm();

    const request = httpMock.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 1, email: 'ana@example.com' });

    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });

  it('shows a whole-form error on wrong credentials and stays put', () => {
    setInput('email', 'ana@example.com');
    setInput('password', 'wrong password');
    submitForm();

    httpMock.expectOne('/api/auth/login').flush(
      { errors: { credentials: 'Email or password is incorrect.' } },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Email or password is incorrect.');
    expect(navigate).not.toHaveBeenCalled();
  });
});
