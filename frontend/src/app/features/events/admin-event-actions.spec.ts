import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AdminEventActions } from './admin-event-actions';
import { AdminEventItem } from './events.service';

/** 1 Oct 2026 18:30 in Bucharest (EEST, UTC+3) — the wall-clock time the edit form must show back. */
const SCHEDULED: AdminEventItem = {
  id: 7,
  name: 'Autumn 5k',
  startDateTime: '2026-10-01T18:30:00+03:00',
  locationName: 'Tineretului Park',
  city: 'Bucharest',
  status: 'SCHEDULED',
  registrationCount: 0,
};

describe('AdminEventActions', () => {
  let fixture: ComponentFixture<AdminEventActions>;
  let httpMock: HttpTestingController;
  let changes: number;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminEventActions],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminEventActions);
    httpMock = TestBed.inject(HttpTestingController);
    changes = 0;
    fixture.componentInstance.changed.subscribe(() => (changes += 1));
  });

  afterEach(() => httpMock.verify());

  function render(event: Partial<AdminEventItem> = {}): void {
    fixture.componentRef.setInput('event', { ...SCHEDULED, ...event });
    fixture.detectChanges();
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`);
  }

  function click(testid: string): void {
    (query(testid) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function setField(testid: string, value: string): void {
    const field = query(testid) as HTMLInputElement | HTMLSelectElement;
    field.value = value;
    field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('offers edit and remove for a run that is still on', () => {
    render();

    expect(query('event-edit')).not.toBeNull();
    expect(query('event-remove')).not.toBeNull();
    expect(query('event-readonly')).toBeNull();
  });

  it('makes a cancelled run read-only — no edit, no remove', () => {
    render({ status: 'CANCELLED', registrationCount: 4 });

    expect(query('event-edit')).toBeNull();
    expect(query('event-remove')).toBeNull();
    expect(query('event-readonly')).not.toBeNull();
  });

  describe('editing', () => {
    it('prefills the form with the run as it stands, in Bucharest time', () => {
      render();
      click('event-edit');

      expect((query('edit-name') as HTMLInputElement).value).toBe('Autumn 5k');
      expect((query('edit-date') as HTMLInputElement).value).toBe('2026-10-01');
      expect((query('edit-hour') as HTMLSelectElement).value).toBe('18');
      expect((query('edit-minute') as HTMLSelectElement).value).toBe('30');
    });

    it('saves the edit as a zone-less local date-time and announces the change', () => {
      render();
      click('event-edit');
      setField('edit-name', 'Autumn Night 5k');
      setField('edit-date', '2026-10-08');
      setField('edit-hour', '19');
      setField('edit-minute', '00');
      click('edit-save');

      const request = httpMock.expectOne('/api/admin/events/7');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        name: 'Autumn Night 5k',
        startDateTime: '2026-10-08T19:00',
      });
      request.flush({ ...SCHEDULED, name: 'Autumn Night 5k' });
      fixture.detectChanges();

      expect(changes).toBe(1);
      // The form closes again, back to the plain actions.
      expect(query('edit-form')).toBeNull();
      expect(query('event-edit')).not.toBeNull();
    });

    it('keeps the form open and shows a server field error when the edit is rejected', () => {
      render();
      click('event-edit');
      setField('edit-date', '2020-01-01');
      click('edit-save');

      httpMock.expectOne('/api/admin/events/7').flush(
        { errors: { startDateTime: 'The start must be in the future.' } },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(text()).toContain('The start must be in the future.');
      expect(query('edit-form')).not.toBeNull();
      expect(changes).toBe(0);
    });

    it('dismissing the edit makes no request and restores the actions', () => {
      render();
      click('event-edit');
      click('edit-dismiss');

      expect(query('edit-form')).toBeNull();
      expect(query('event-edit')).not.toBeNull();
      expect(changes).toBe(0);
      // No PUT was issued — afterEach's verify() would fail if one were left pending.
    });
  });

  describe('removing a run nobody has registered for', () => {
    it('offers a hard delete, and deletes it once confirmed', () => {
      render({ registrationCount: 0 });
      click('event-remove');

      expect(query('remove-delete')).not.toBeNull();
      expect(query('remove-cancel-run')).toBeNull();

      click('remove-delete');
      const request = httpMock.expectOne('/api/admin/events/7');
      expect(request.request.method).toBe('DELETE');
      request.flush(null);
      fixture.detectChanges();

      expect(changes).toBe(1);
    });
  });

  describe('removing a run people have registered for', () => {
    it('withholds delete, offers cancel, and says how many people are registered', () => {
      render({ registrationCount: 12 });
      click('event-remove');

      expect(query('remove-delete')).toBeNull();
      expect(query('remove-cancel-run')).not.toBeNull();
      expect(query('remove-confirm')?.textContent).toContain('12');
    });

    it('cancels the run once confirmed, and announces the change', () => {
      render({ registrationCount: 12 });
      click('event-remove');
      click('remove-cancel-run');

      const request = httpMock.expectOne('/api/admin/events/7/cancel');
      expect(request.request.method).toBe('POST');
      request.flush({ ...SCHEDULED, status: 'CANCELLED', registrationCount: 12 });
      fixture.detectChanges();

      expect(changes).toBe(1);
    });

    it('shows the server message if the cancel is refused, and announces nothing', () => {
      render({ registrationCount: 12 });
      click('event-remove');
      click('remove-cancel-run');

      httpMock.expectOne('/api/admin/events/7/cancel').flush(
        { errors: { event: 'This run has been cancelled and can no longer be changed.' } },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(text()).toContain('can no longer be changed');
      expect(changes).toBe(0);
    });

    it('keeping the run makes no request', () => {
      render({ registrationCount: 12 });
      click('event-remove');
      click('remove-dismiss');

      expect(query('remove-confirm')).toBeNull();
      expect(changes).toBe(0);
    });
  });
});
