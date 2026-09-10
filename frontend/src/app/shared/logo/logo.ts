import { Component } from '@angular/core';

/**
 * The 3T Run wordmark — "3T" in orange, "RUN" in ink, tucked together (charter design direction,
 * issue #39). An inline SVG so the mark is a real logo asset: it scales cleanly, colours from the
 * tokens, and (as role="img" with an accessible name) reads as one image to assistive tech rather
 * than as low-contrast text. Inline, so it renders in the document's Archivo face.
 */
@Component({
  selector: 'app-logo',
  templateUrl: './logo.html',
  styleUrl: './logo.css',
})
export class Logo {}
