import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from './auth.service';

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
  imports: [ReactiveFormsModule, RouterLink],
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
    email: ['', [Validators.required, Validators.pattern(EMAIL_PATTERN)]],
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD)]],
  });

  /** The message to show under a field: server error first, else the client-side rule. */
  protected controlError(field: 'email' | 'password'): string | null {
    const server = this.fieldErrors()[field];
    if (server) {
      return server;
    }
    const control = this.form.controls[field];
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return field === 'email' ? 'Enter your email.' : 'Choose a password.';
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
    const { email, password } = this.form.getRawValue();

    this.auth.signup(email!, password!).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (response: HttpErrorResponse) => {
        this.submitting.set(false);
        const errors = response.error?.errors as Record<string, string> | undefined;
        if (errors && typeof errors === 'object') {
          this.fieldErrors.set(errors);
        } else {
          this.formError.set('Something went wrong — please try again.');
        }
      },
    });
  }
}
