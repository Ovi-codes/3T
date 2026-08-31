import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from './auth.service';

/**
 * Increment 3 (CS-3): sign in. Both fields are only required here — the server answers a wrong
 * email or password with one generic message (it never says which was wrong), which we show as a
 * whole-form error rather than pinning it to a field. On success, route to the dashboard.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './auth.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected controlError(field: 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return field === 'email' ? 'Enter your email.' : 'Enter your password.';
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
    const { email, password } = this.form.getRawValue();

    this.auth.login(email!, password!).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (response: HttpErrorResponse) => {
        this.submitting.set(false);
        const errors = response.error?.errors as Record<string, string> | undefined;
        this.formError.set(errors?.['credentials'] ?? 'Something went wrong — please try again.');
      },
    });
  }
}
