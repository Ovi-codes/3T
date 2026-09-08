import { HttpErrorResponse } from '@angular/common/http';

import { toFormErrors } from './form-errors';

/** Build an HttpErrorResponse with the given parsed body, as HttpClient hands it to an error callback. */
function errorWith(body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ error: body, status: 400, statusText: 'Bad Request' });
}

describe('toFormErrors', () => {
  it('reads the field-error envelope into fieldErrors and leaves formError null', () => {
    const result = toFormErrors(errorWith({ errors: { email: 'Taken.', name: 'Required.' } }));

    expect(result.fieldErrors).toEqual({ email: 'Taken.', name: 'Required.' });
    expect(result.formError).toBeNull();
  });

  it('copies the envelope rather than aliasing it', () => {
    const envelope = { errors: { email: 'Taken.' } };
    const result = toFormErrors(errorWith(envelope));

    result.fieldErrors['email'] = 'mutated';
    expect(envelope.errors.email).toBe('Taken.');
  });

  it('falls back to the generic form error when there is no error body (network failure)', () => {
    const result = toFormErrors(
      new HttpErrorResponse({ error: new ProgressEvent('error'), status: 0, statusText: 'Unknown Error' }),
    );

    expect(result.fieldErrors).toEqual({});
    expect(result.formError).toBe('Something went wrong — please try again.');
  });

  it('falls back to the generic form error when the body has no errors object', () => {
    const result = toFormErrors(errorWith({ message: 'Internal Server Error' }));

    expect(result.fieldErrors).toEqual({});
    expect(result.formError).toBe('Something went wrong — please try again.');
  });

  it('falls back to the generic form error when errors is null', () => {
    const result = toFormErrors(errorWith({ errors: null }));

    expect(result.fieldErrors).toEqual({});
    expect(result.formError).toBe('Something went wrong — please try again.');
  });
});
