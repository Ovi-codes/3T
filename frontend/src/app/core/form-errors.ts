import { HttpErrorResponse } from '@angular/common/http';

/** Shown when the request fails without a readable field-error envelope (network, unexpected body). */
const GENERIC_ERROR = 'Something went wrong — please try again.';

/** The split a form renders: per-field messages, plus one whole-form message. */
export interface FormErrors {
  fieldErrors: Record<string, string>;
  formError: string | null;
}

/**
 * The single reader of the backend error envelope `{ errors: { <field>: <message> } }`. When the
 * envelope is present its entries become `fieldErrors` and `formError` is null; when it's missing or
 * not an object (a network failure, an unexpected body) `fieldErrors` is empty and `formError`
 * carries the generic fallback. Pure — no HttpClient, no RxJS — so a component maps the few
 * form-level keys it treats specially (`credentials`, `eventId`) on top of the result itself.
 */
export function toFormErrors(err: HttpErrorResponse): FormErrors {
  const errors = err.error?.errors as Record<string, string> | undefined;
  if (errors && typeof errors === 'object') {
    return { fieldErrors: { ...errors }, formError: null };
  }
  return { fieldErrors: {}, formError: GENERIC_ERROR };
}
