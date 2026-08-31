import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/**
 * Increment 3 leaves this a stub — reaching it is what CS-2/CS-3 assert. Increment 4 fills it with
 * the user's upcoming and past runs. It only renders behind the auth guard, so `user()` is set by
 * the time it shows.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;

  protected logout(): void {
    // Either way, land on the login page — the local session is cleared regardless.
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
