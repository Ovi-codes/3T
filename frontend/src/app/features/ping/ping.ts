import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

/** Shape of GET /api/ping — mirrors the backend PingResponse record. */
interface PingResponse {
  status: string;
  appVersion: string;
}

/**
 * Increment 0 walking skeleton: proves the browser can reach the backend.
 * Calls GET /api/ping (proxied to :8080 in dev) and renders the answer, so a
 * green "Backend says: ok (v0.0.1)" means Angular → HTTP → Spring → Postgres is wired.
 */
@Component({
  selector: 'app-ping',
  imports: [],
  templateUrl: './ping.html',
  styleUrl: './ping.css',
})
export class Ping {
  private readonly http = inject(HttpClient);

  /** null while the request is in flight; the response once it lands. */
  protected readonly ping = signal<PingResponse | null>(null);
  /** Populated only if the call fails, so the skeleton fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.http.get<PingResponse>('/api/ping').subscribe({
      next: (response) => this.ping.set(response),
      error: () => this.error.set('backend unreachable'),
    });
  }
}