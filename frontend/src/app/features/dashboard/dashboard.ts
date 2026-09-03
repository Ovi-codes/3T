import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { AccountService, MyRegistrations } from '../account/account.service';

/**
 * Increment 4: the runner's own runs, split into the ones ahead (CS-4) and the ones done (CS-5).
 * Calls GET /api/me/registrations — already split and ordered by the server (upcoming soonest-first,
 * past most-recent-first) — and renders each bucket with its own empty state.
 *
 * It only renders behind the auth guard, so `user()` is set and the request carries the session
 * cookie by the time this shows. An anonymous visitor never reaches here — the guard sends them to
 * /login (CS-6, UI side).
 */
@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;

  /** null while the request is in flight; the two buckets once it lands. */
  protected readonly runs = signal<MyRegistrations | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.account.getMyRegistrations().subscribe({
      next: (response) => this.runs.set(response),
      error: () => this.error.set('We could not load your runs. Please try again shortly.'),
    });
  }

  protected logout(): void {
    // Either way, land on the login page — the local session is cleared regardless.
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }

  // --- GDPR: the user's control over their own data (charter §7) ---

  /** True while the export download is being fetched, so the button can show progress. */
  protected readonly exporting = signal(false);
  /** Set if the export fails, shown next to the button rather than blanking the page. */
  protected readonly exportError = signal<string | null>(null);

  /** Whether the "permanently delete" confirmation is showing. */
  protected readonly confirmingDelete = signal(false);
  /** True while the account is being deleted. */
  protected readonly deleting = signal(false);
  /** Set if the delete fails. */
  protected readonly deleteError = signal<string | null>(null);

  /** Fetch the account's data and hand it to the browser as a file to save. */
  protected exportData(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.exportError.set(null);
    this.account.exportData().subscribe({
      next: (blob) => {
        this.exporting.set(false);
        this.saveBlob(blob, 'threet-run-my-data.json');
      },
      error: () => {
        this.exporting.set(false);
        this.exportError.set('We could not prepare your download. Please try again shortly.');
      },
    });
  }

  protected startDelete(): void {
    this.deleteError.set(null);
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  /** Erase the account and its data, then leave for the home page — the session is gone. */
  protected confirmDelete(): void {
    if (this.deleting()) {
      return;
    }
    this.deleting.set(true);
    this.deleteError.set(null);
    this.account.deleteAccount().subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.deleting.set(false);
        this.deleteError.set('We could not delete your account. Please try again shortly.');
      },
    });
  }

  /** Trigger a browser download of the given blob under the given filename. */
  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
