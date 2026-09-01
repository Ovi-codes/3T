import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from './features/auth/auth.service';

/**
 * The app shell. Carries the persistent header (brand + a single auth action) that sits above every
 * route. On startup it resolves the current session once, so the header shows the right action on
 * any page — including the public ones the route guard never runs on.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly auth = inject(AuthService);

  /** undefined until the session check lands, then null (signed out) or the account. */
  protected readonly user = this.auth.user;

  constructor() {
    // Fire-and-forget: settles `user` to the account or null; the header reacts to the signal.
    this.auth.loadCurrentUser().subscribe();
  }
}
