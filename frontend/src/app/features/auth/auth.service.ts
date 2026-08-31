import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';

/** The signed-in account — mirrors the backend AccountResponse (id + email, nothing sensitive). */
export interface Account {
  id: number;
  email: string;
}

/**
 * Holds session state for the app and talks to the auth API. The session itself lives in an
 * httpOnly cookie the browser sends automatically (same-origin via the dev proxy), so this service
 * never sees a token — it only tracks *who* is signed in for the UI and the route guard.
 *
 * `user` is three-valued: `undefined` = not checked yet, `null` = checked and signed out, an
 * `Account` = signed in. The guard uses the distinction to know whether it must ask the server.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _user = signal<Account | null | undefined>(undefined);
  /** Current account, or null when signed out, or undefined before the first check. */
  readonly user = this._user.asReadonly();
  /** Convenience for templates: a definite signed-in boolean. */
  readonly isSignedIn = computed(() => !!this._user());

  /** CS-2: create the account; the server logs the new user in and returns them. */
  signup(email: string, password: string): Observable<Account> {
    return this.http
      .post<Account>('/api/auth/signup', { email, password })
      .pipe(tap((account) => this._user.set(account)));
  }

  /** CS-3: sign in with existing credentials. */
  login(email: string, password: string): Observable<Account> {
    return this.http
      .post<Account>('/api/auth/login', { email, password })
      .pipe(tap((account) => this._user.set(account)));
  }

  /** End the session server-side and locally. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this._user.set(null)));
  }

  /**
   * Resolve the current account from the session cookie. A 401 is the normal "signed out" answer,
   * not an error, so it settles `user` to null rather than throwing.
   */
  loadCurrentUser(): Observable<Account | null> {
    return this.http.get<Account>('/api/auth/me').pipe(
      map((account) => {
        this._user.set(account);
        return account;
      }),
      catchError(() => {
        this._user.set(null);
        return of(null);
      }),
    );
  }
}
