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

  it('links to the dashboard where the data controls live', () => {
    const link = el().querySelector<HTMLAnchorElement>('a[href="/dashboard"]');
    expect(link).not.toBeNull();
  });
});
