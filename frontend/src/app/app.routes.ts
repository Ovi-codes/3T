import { Routes } from '@angular/router';
import { Events } from './features/events/events';
import { Register } from './features/register/register';
import { Ping } from './features/ping/ping';

export const routes: Routes = [
  { path: '', component: Events },
  // Register for one upcoming run (core loop).
  { path: 'register/:eventId', component: Register },
  // The Increment 0 skeleton smoke view — kept as a backend-reachability check.
  { path: 'ping', component: Ping },
];