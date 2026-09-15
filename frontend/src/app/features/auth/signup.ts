import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { AuthService } from './auth.service';
import { toFormErrors } from '../../core/form-errors';
import { Poster } from '../../shared/poster/poster';

/**
 * A name has to look like a name: at least one letter, so "12345" (only digits) is rejected even
 * though it clears the minimum length. Empty is left to the required validator. Mirrors the
 * registration form's rule so the two name inputs behave identically.
 */
function nameNotOnlyNumbers(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  return value && /^\d+$/.test(value) ? { onlyDigits: true } : null;
}

/**
 * Stricter than Angular's Validators.email (which accepts "a@a"): require a domain with a dot and a
 * real extension, matching the register form and the backend's expectation.
 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** The shortest password the backend accepts (BCrypt caps the top end at 72). */
const MIN_PASSWORD = 8;

/**
 * Increment 3 (CS-2): create an account. Validates email + password client-side, posts through the
 * auth service (which the server logs straight in), and on success routes to the dashboard. Server
 * field errors — chiefly a taken email — surface against the same inputs, exactly as the register
 * form does.
 */
@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule, RouterLink, Poster],
  templateUrl: './signup.html',
  styleUrl: './auth.css',
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), nameNotOnlyNumbers]],
    email: ['', [Validators.required, Validators.pattern(EMAIL_PATTERN)]],
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD)]],
  });

  /** The message to show under a field: server error first, else the client-side rule. */
  protected controlError(field: 'name' | 'email' | 'password'): string | null {
    const server = this.fieldErrors()[field];
    if (server) {
      return server;
    }
    const control = this.form.controls[field];
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      if (field === 'name') {
        return 'Enter your name.';
      }
      return field === 'email' ? 'Enter your email.' : 'Choose a password.';
    }
    if (field === 'name') {
      if (control.hasError('minlength')) {
        return 'Name must be at least 3 characters.';
      }
      if (control.hasError('onlyDigits')) {
        return 'Name can’t be only numbers.';
      }
    }
    if (field === 'email' && control.hasError('pattern')) {
      return 'Enter a valid email address.';
    }
    if (field === 'password' && control.hasError('minlength')) {
      return `Password must be at least ${MIN_PASSWORD} characters.`;
    }
    return null;
  }

  protected submit(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.fieldErrors.set({});
    const { name, email, password } = this.form.getRawValue();

    this.auth.signup(name!, email!, password!).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (response: HttpErrorResponse) => {
        this.submitting.set(false);
        const { fieldErrors, formError } = toFormErrors(response);
        this.fieldErrors.set(fieldErrors);
        this.formError.set(formError);
      },
    });
  }
}
