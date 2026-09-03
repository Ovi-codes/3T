import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * The privacy policy — a static page stating what we collect. No non-essential cookies
 * are set, so there is no consent banner.
 */
@Component({
  selector: 'app-privacy',
  imports: [RouterLink],
  templateUrl: './privacy.html',
  styleUrl: './privacy.css',
})
export class Privacy {}
