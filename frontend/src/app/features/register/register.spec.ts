import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Register } from './register';
import { EventItem } from '../events/events';

const EVENT: EventItem = {
  id: 3,
  name: 'Tineretului parkrun',
  startDateTime: '2026-09-05T06:00:00Z',
  locationName: 'Tineretului Park',
  city: 'Bucharest',
};

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // The route names the event to register for.
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ eventId: '3' }) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Resolve the event lookup so the form renders. */
  function loadEvent(): void {
    httpMock.expectOne('/api/events').flush([EVENT]);
    fixture.detectChanges();
  }

  function setInput(id: string, value: string): void {
    const input = fixture.nativeElement.querySelector('#' + id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('blocks submission and shows field errors when the form is empty', () => {
    loadEvent();

    submitForm();

    // The invalid form never calls the API — so nothing is registered and no email is sent.
    httpMock.expectNone('/api/registrations');
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Enter your name.');
    expect(text).toContain('Enter your email.');
  });

  it('shows an inline error for a malformed email without calling the API', () => {
    loadEvent();

    setInput('name', 'Ana Pop');
    setInput('email', 'not-an-email');
    submitForm();

    httpMock.expectNone('/api/registrations');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Enter a valid email address.');
  });

  it('rejects a name shorter than three characters', () => {
    loadEvent();

    setInput('name', 'Ab');
    setInput('email', 'ana.pop@example.com');
    submitForm();

    httpMock.expectNone('/api/registrations');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Name must be at least 3 characters.');
  });

  it('rejects a name that is only numbers', () => {
    loadEvent();

    setInput('name', '12345');
    setInput('email', 'ana.pop@example.com');
    submitForm();

    httpMock.expectNone('/api/registrations');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Name can’t be only numbers.');
  });

  it('rejects an email without a valid extension (e.g. a@a)', () => {
    loadEvent();

    setInput('name', 'Ana Pop');
    setInput('email', 'a@a');
    submitForm();

    httpMock.expectNone('/api/registrations');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Enter a valid email address.');
  });

  it('posts a valid registration and shows the confirmation', () => {
    loadEvent();

    setInput('name', 'Ana Pop');
    setInput('email', 'ana.pop@example.com');
    submitForm();

    const request = httpMock.expectOne('/api/registrations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ eventId: 3, name: 'Ana Pop', email: 'ana.pop@example.com' });

    request.flush({
      eventName: 'Tineretului parkrun',
      startDateTime: EVENT.startDateTime,
      locationName: 'Tineretului Park',
      city: 'Bucharest',
      email: 'ana.pop@example.com',
    });
    fixture.detectChanges();

    const confirmation = fixture.nativeElement.querySelector('[data-testid="confirmation"]');
    expect(confirmation).not.toBeNull();
    expect(confirmation.textContent).toContain('Tineretului parkrun');
    expect(confirmation.textContent).toContain('ana.pop@example.com');
  });

  it('surfaces a server field error against the email input', () => {
    loadEvent();

    setInput('name', 'Ana Pop');
    setInput('email', 'ana.pop@example.com');
    submitForm();

    httpMock.expectOne('/api/registrations').flush(
      { errors: { email: 'This email is already registered for this run.' } },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('This email is already registered for this run.');
    // Still on the form, no confirmation shown.
    expect(fixture.nativeElement.querySelector('[data-testid="confirmation"]')).toBeNull();
  });
});
