import type { APIRequestContext } from '@playwright/test';

const BACKEND = 'http://localhost:8080';

/**
 * The CSRF header a direct API write needs. The browser-driven tests get it for free (Angular's
 * HttpClient echoes the `XSRF-TOKEN` cookie), but a spec that seeds data through Playwright's
 * `request` fixture must do the same itself, or the backend answers 403.
 *
 * The token is read from the request context's cookie jar on every call, because login and logout
 * replace it. If the jar has no token yet, one GET makes the backend set it.
 */
export async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  let token = await currentToken(request);
  if (!token) {
    await request.get(`${BACKEND}/api/auth/me`); // any response carries the cookie, even a 401
    token = await currentToken(request);
  }
  if (!token) {
    throw new Error('The backend set no XSRF-TOKEN cookie — is the CSRF token layer running?');
  }
  return { 'X-XSRF-TOKEN': token };
}

async function currentToken(request: APIRequestContext): Promise<string | undefined> {
  const { cookies } = await request.storageState();
  return cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
}
