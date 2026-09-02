import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Privacy } from './privacy';

describe('Privacy', () => {
  let fixture: ComponentFixture<Privacy>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Privacy],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Privacy);
    fixture.detectChanges();
  });

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the policy page', () => {
    expect(el().querySelector('[data-testid="privacy"]')).not.toBeNull();
    expect(el().querySelector('h1')!.textContent).toContain('Privacy policy');
  });

  it('states what is collected, the cookie stance, and the rights', () => {
    const text = el().textContent!;
    expect(text).toContain('name');
    expect(text).toContain('email');
    // The no-tracking-cookies stance that justifies having no consent banner.
    expect(text).toContain('no analytics');
    expect(text).toContain('Download your data');
    expect(text).toContain('Delete your account');
  });

  it('links to the dashboard where the data controls live', () => {
    const link = el().querySelector<HTMLAnchorElement>('a[href="/dashboard"]');
    expect(link).not.toBeNull();
  });
});
