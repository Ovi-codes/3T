import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
  });
});
