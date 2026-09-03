import { Routes } from '@angular/router';
import { Events } from './features/events/events';
import { Register } from './features/register/register';
import { Signup } from './features/auth/signup';
import { Login } from './features/auth/login';
import { Dashboard } from './features/dashboard/dashboard';
import { authGuard } from './features/auth/auth.guard';
import { Privacy } from './features/privacy/privacy';
import { Ping } from './features/ping/ping';

export const routes: Routes = [
  { path: '', component: Events },
  // Register for one upcoming run (core loop).
  { path: 'register/:eventId', component: Register },
  // Accounts (Increment 3): sign up / sign in land on the dashboard.
  { path: 'signup', component: Signup },
  { path: 'login', component: Login },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  // GDPR (Increment 5): the privacy policy, linked from the footer.
  { path: 'privacy', component: Privacy },
  // The Increment 0 skeleton smoke view — kept as a backend-reachability check.
  { path: 'ping', component: Ping },
];
