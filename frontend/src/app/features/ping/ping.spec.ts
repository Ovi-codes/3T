import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { Ping } from './ping';

describe('Ping', () => {
  let fixture: ComponentFixture<Ping>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Ping],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Ping);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders the status and version the backend returns', async () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/ping')
      .flush({ status: 'ok', appVersion: '0.0.1' });

    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Backend says: ok (v0.0.1)');
  });

  it('shows an error message when the backend is unreachable', async () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/ping')
      .error(new ProgressEvent('network error'));

    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('backend unreachable');
  });
});