/**
 * Finish times (#43) travel as whole seconds; runners read and type them as a clock time. These two
 * pure helpers are the only place that converts between the two.
 */

/** `m:ss`, `mm:ss` or `h:mm:ss` — seconds always two digits, minutes two digits once hours lead. */
const CLOCK_TIME = /^(?:(\d+):(\d{2})|(\d{1,2})):(\d{2})$/;

/**
 * Read a typed finish time as whole seconds, or null when it isn't a clock time. Whether the value
 * is a sensible 5k time is a separate check — this only reads the format.
 */
export function parseFinishTime(text: string): number | null {
  const match = CLOCK_TIME.exec(text.trim());
  if (!match) {
    return null;
  }
  const [, hoursPart, minutesAfterHours, minutesAlone, secondsPart] = match;
  const hours = hoursPart === undefined ? 0 : Number(hoursPart);
  const minutes = Number(minutesAfterHours ?? minutesAlone);
  const seconds = Number(secondsPart);
  if (seconds > 59 || (hoursPart !== undefined && minutes > 59)) {
    return null;
  }
  return hours * 3600 + minutes * 60 + seconds;
}

/** Write whole seconds as `m:ss` under an hour, `h:mm:ss` from an hour up. */
export function formatFinishTime(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${ss}` : `${minutes}:${ss}`;
}
