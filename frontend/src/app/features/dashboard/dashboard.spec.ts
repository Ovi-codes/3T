import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';

import { Dashboard } from './dashboard';
import { MyRegistrations } from '../account/account.service';

const EMPTY: MyRegistrations = { upcoming: [], past: [] };

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  function click(testid: string): void {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>(`[data-testid="${testid}"]`)!
      .click();
    fixture.detectChanges();
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`);
  }

  async function flush(runs: MyRegistrations): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/registrations').flush(runs);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function run(overrides: Partial<MyRegistrations['upcoming'][number]> = {}) {
    return {
      registrationId: 1,
      eventId: 1,
      eventName: 'Tineretului parkrun',
      startDateTime: '2026-09-05T06:00:00Z',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
      cancelled: false,
      finishTimeSeconds: null,
      ...overrides,
    };
  }

  function section(testid: 'section-upcoming' | 'section-past'): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`)!;
  }

  it('renders the two buckets from the API, each in its own section (CS-4/CS-5)', async () => {
    await flush({
      upcoming: [run({ registrationId: 10, eventId: 10, eventName: 'Autumn 5k' })],
      past: [run({ registrationId: 20, eventId: 20, eventName: 'Summer 5k' })],
    });

    const upcoming = section('section-upcoming');
    const past = section('section-past');
    expect(upcoming.querySelectorAll('[data-testid="upcoming-item"]').length).toBe(1);
    expect(upcoming.textContent).toContain('Autumn 5k');
    // The past run belongs under Past, not Upcoming.
    expect(upcoming.textContent).not.toContain('Summer 5k');
    expect(past.querySelectorAll('[data-testid="past-item"]').length).toBe(1);
    expect(past.textContent).toContain('Summer 5k');
  });

  it('marks the soonest upcoming run as the next one, and only that one', async () => {
    await flush({
      upcoming: [
        run({ registrationId: 1, eventName: 'Soonest' }),
        run({ registrationId: 2, eventName: 'Later' }),
      ],
      past: [],
    });

    const items = section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]');
    expect(items[0].textContent).toContain('Next');
    expect(items[1].textContent).not.toContain('Next');
  });

  it('badges a called-off upcoming run, and hands "Next" to the soonest run still on (#58)', async () => {
    await flush({
      upcoming: [
        run({ registrationId: 1, eventName: 'Called off', cancelled: true }),
        run({ registrationId: 2, eventName: 'Later' }),
      ],
      past: [],
    });

    const items = section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]');
    expect(items[0].textContent).toContain('Cancelled');
    expect(items[0].textContent).not.toContain('Next');
    expect(items[1].textContent).toContain('Next');
  });

  it('says a called-off past run was cancelled, not attended (#58)', async () => {
    await flush({ upcoming: [], past: [run({ cancelled: true })] });

    const item = section('section-past').querySelector('[data-testid="past-item"]')!;
    expect(item.textContent).toContain('Cancelled');
    expect(item.textContent).not.toContain('Attended');
  });

  it('shows the upcoming empty state while past still lists runs', async () => {
    await flush({ upcoming: [], past: [run()] });

    expect(section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]').length).toBe(0);
    expect(section('section-upcoming').textContent).toContain('No upcoming runs yet');
    expect(section('section-past').querySelectorAll('[data-testid="past-item"]').length).toBe(1);
  });

  it('shows the past empty state while upcoming still lists runs', async () => {
    await flush({ upcoming: [run()], past: [] });

    expect(section('section-past').querySelectorAll('[data-testid="past-item"]').length).toBe(0);
    expect(section('section-past').textContent).toContain('No past runs yet');
    expect(section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]').length).toBe(1);
  });

  it('shows an error message when the runs cannot be loaded', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/registrations').error(new ProgressEvent('network error'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not load your runs');
  });

  describe('Finish times (#43)', () => {
    function pastItem(): HTMLElement {
      return section('section-past').querySelector('[data-testid="past-item"]')!;
    }

    it('shows a recorded time on its past run, as a clock time', async () => {
      await flush({ upcoming: [], past: [run({ finishTimeSeconds: 1471 })] });

      expect(pastItem().querySelector('[data-testid="finish-time"]')!.textContent!.trim()).toBe('24:31');
    });

    function type(testid: string, value: string): void {
      const input = query(testid) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    async function respond(request: TestRequest, body: object): Promise<void> {
      request.flush(body);
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('records a time typed as mm:ss, sending seconds, and shows it on the run', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });
      expect(query('finish-time')).toBeNull();

      click('finish-time-add');
      type('finish-time-input', '24:31');
      click('finish-time-save');

      const request = httpMock.expectOne('/api/me/registrations/20/finish-time');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ finishTimeSeconds: 1471 });
      await respond(request, run({ registrationId: 20, finishTimeSeconds: 1471 }));

      expect(query('finish-time-input')).toBeNull();
      expect(query('finish-time')!.textContent!.trim()).toBe('24:31');
    });

    it('corrects a recorded time, starting from the one already there', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20, finishTimeSeconds: 1471 })] });

      click('finish-time-edit');
      expect((query('finish-time-input') as HTMLInputElement).value).toBe('24:31');
      type('finish-time-input', '25:02');
      click('finish-time-save');

      const request = httpMock.expectOne('/api/me/registrations/20/finish-time');
      expect(request.request.body).toEqual({ finishTimeSeconds: 1502 });
      await respond(request, run({ registrationId: 20, finishTimeSeconds: 1502 }));

      expect(query('finish-time')!.textContent!.trim()).toBe('25:02');
    });

    it('asks for mm:ss when the time cannot be read, without saving', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });

      click('finish-time-add');
      type('finish-time-input', '24');
      click('finish-time-save');

      // No PUT is made — afterEach's verify() would fail on a pending one.
      expect(query('finish-time-error')!.textContent).toContain('mm:ss');
      expect(query('finish-time-input')!.getAttribute('aria-invalid')).toBe('true');
    });

    it('refuses a time outside the 10:00 to 1:00:00 window, without saving', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });

      click('finish-time-add');
      type('finish-time-input', '8:00');
      click('finish-time-save');
      expect(query('finish-time-error')!.textContent).toContain('10:00');

      type('finish-time-input', '1:00:01');
      click('finish-time-save');
      expect(query('finish-time-error')!.textContent).toContain('1:00:00');
    });

    it("shows the server's reason when it refuses the time, and keeps the form open", async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });

      click('finish-time-add');
      type('finish-time-input', '24:31');
      click('finish-time-save');
      httpMock
        .expectOne('/api/me/registrations/20/finish-time')
        .flush(
          { errors: { finishTimeSeconds: 'You can add a time once the run has taken place.' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(query('finish-time-error')!.textContent).toContain('once the run has taken place');
      expect((query('finish-time-save') as HTMLButtonElement).disabled).toBe(false);
    });

    it('says something went wrong when the save fails without a reason', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });

      click('finish-time-add');
      type('finish-time-input', '24:31');
      click('finish-time-save');
      httpMock
        .expectOne('/api/me/registrations/20/finish-time')
        .error(new ProgressEvent('network error'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(query('finish-time-error')!.textContent).toContain('Something went wrong');
    });

    it('discards an unsaved time, leaving the run as it was', async () => {
      await flush({ upcoming: [], past: [run({ registrationId: 20 })] });

      click('finish-time-add');
      type('finish-time-input', '24:31');
      click('finish-time-discard');

      // Nothing was sent — afterEach's verify() would fail on a pending PUT.
      expect(query('finish-time-input')).toBeNull();
      expect(query('finish-time-add')).not.toBeNull();
    });

    it('offers no time entry on a cancelled run — it never took place', async () => {
      await flush({ upcoming: [], past: [run({ cancelled: true })] });

      expect(query('finish-time-add')).toBeNull();
    });

    // Opening and closing the form swaps the focused button out of the DOM; focus must follow rather
    // than fall to <body> (a keyboard / screen-reader user would be stranded).
    describe('focus management (a11y)', () => {
      beforeEach(() => document.body.appendChild(fixture.nativeElement));
      afterEach(() => fixture.nativeElement.remove());

      async function settle(): Promise<void> {
        await fixture.whenStable();
        fixture.detectChanges();
      }

      it('moves focus into the time input when the form opens', async () => {
        await flush({ upcoming: [], past: [run({ registrationId: 20 })] });
        click('finish-time-add');
        await settle();

        expect(document.activeElement).toBe(query('finish-time-input'));
      });

      it('returns focus to "Add your time" when the form is discarded', async () => {
        await flush({ upcoming: [], past: [run({ registrationId: 20 })] });
        click('finish-time-add');
        click('finish-time-discard');
        await settle();

        expect(document.activeElement).toBe(query('finish-time-add'));
      });

      it('moves focus to "Edit" once the time is saved', async () => {
        await flush({ upcoming: [], past: [run({ registrationId: 20 })] });
        click('finish-time-add');
        type('finish-time-input', '24:31');
        click('finish-time-save');
        await respond(
          httpMock.expectOne('/api/me/registrations/20/finish-time'),
          run({ registrationId: 20, finishTimeSeconds: 1471 }),
        );
        await settle();

        expect(document.activeElement).toBe(query('finish-time-edit'));
      });
    });
  });

  describe('Your data (GDPR)', () => {
    it('exports the account data as a downloadable file', async () => {
      const url = 'blob:fake';
      const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue(url);
      const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
      const clickAnchor = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockReturnValue(undefined);

      await flush(EMPTY);
      click('export-data');

      const request = httpMock.expectOne('/api/me/export');
      expect(request.request.method).toBe('GET');
      request.flush(new Blob(['{}'], { type: 'application/json' }));
      await fixture.whenStable();

      expect(createObjectURL).toHaveBeenCalledOnce();
      expect(clickAnchor).toHaveBeenCalledOnce();
      expect(revokeObjectURL).toHaveBeenCalledWith(url);
    });

    it('shows an error if the export fails, without breaking the page', async () => {
      await flush(EMPTY);
      click('export-data');

      httpMock.expectOne('/api/me/export').error(new ProgressEvent('network error'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not prepare your download');
    });

    it('deletes the account only after confirmation, then leaves for home', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      await flush(EMPTY);
      // The destructive request is not made until the user confirms.
      click('delete-account');
      expect(query('delete-confirm')).not.toBeNull();

      click('delete-confirm-yes');
      const request = httpMock.expectOne('/api/me');
      expect(request.request.method).toBe('DELETE');
      request.flush(null);
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith('/');
    });

    it('cancelling the delete makes no request and hides the confirmation', async () => {
      await flush(EMPTY);
      click('delete-account');
      click('delete-cancel');

      expect(query('delete-confirm')).toBeNull();
      // No DELETE was issued — afterEach's verify() would fail if one were left pending.
    });

    it('shows an error if the delete fails', async () => {
      await flush(EMPTY);
      click('delete-account');
      click('delete-confirm-yes');

      httpMock.expectOne('/api/me').error(new ProgressEvent('network error'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not delete your account');
    });

    // Opening the confirmation removes the button that had focus; without a deliberate move, focus
    // falls to <body> and a keyboard / screen-reader user is stranded. These check it doesn't.
    describe('focus management (a11y)', () => {
      // Attach to the document so focus() registers as the active element.
      beforeEach(() => document.body.appendChild(fixture.nativeElement));
      afterEach(() => fixture.nativeElement.remove());

      it('moves focus into the confirmation when the delete flow opens', async () => {
        await flush(EMPTY);
        click('delete-account');
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.activeElement).toBe(query('delete-confirm'));
      });

      it('returns focus to the Delete button when the confirmation is cancelled', async () => {
        await flush(EMPTY);
        click('delete-account');
        click('delete-cancel');
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.activeElement).toBe(query('delete-account'));
      });
    });
  });
});
