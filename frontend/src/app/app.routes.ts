import { Routes } from '@angular/router';
import { Events } from './features/events/events';
import { Ping } from './features/ping/ping';

export const routes: Routes = [
  { path: '', component: Events },
  // The Increment 0 skeleton smoke view — kept as a backend-reachability check.
  { path: 'ping', component: Ping },
];