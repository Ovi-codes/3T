import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * The privacy policy — a static page stating what we collect, why, how long we keep it, and the
 * rights a signed-in user can exercise from their dashboard (charter §7). No non-essential cookies
 * are set, so there is no consent banner; the page says so.
 */
@Component({
  selector: 'app-privacy',
  imports: [RouterLink],
  templateUrl: './privacy.html',
  styleUrl: './privacy.css',
})
export class Privacy {}
