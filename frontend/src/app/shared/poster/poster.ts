import { Component, input } from '@angular/core';

/** Distinguishes each poster's wave gradient so multiple instances on a page don't collide. */
let posterSequence = 0;

/**
 * The signature "poster / hero field" — a dark ink block that carries display type, built from three
 * stacked layers (a diagonal weave, a top-left orange ember glow, and a wavy fade to solid ink).
 * Projected content (`<ng-content>`) sits above the layers. Used by the events hero, the
 * confirmation banner, and the auth side panels.
 */
@Component({
  selector: 'app-poster',
  templateUrl: './poster.html',
  styleUrl: './poster.css',
})
export class Poster {
  /** Unique gradient id per instance (the wave <svg> references it by url(#…)). */
  protected readonly gradientId = `poster-wave-${posterSequence++}`;

  /**
   * Where the wavy fade reaches solid ink, as a fraction (0–1) of the wave's height. The default
   * 0.5 is the standard poster (solid by mid-height); a higher value keeps the diagonal weave
   * visible further down before it fades to ink (used by the confirmation hero).
   */
  readonly solidStop = input(0.5);
}
