/**
 * The one place the admin forms agree on how a run's start is entered and read back.
 *
 * Runs happen in Bucharest and are always entered as Bucharest wall-clock time, whatever timezone
 * the admin's browser is in. The server owns the conversion — it reads a zone-less `YYYY-MM-DDTHH:mm`
 * as Europe/Bucharest — so these helpers only have to produce that string, and to turn a stored
 * instant back into the same three fields the form shows.
 */

/** The timezone every run is scheduled in (charter: V1 is Bucharest-only). */
const EVENT_ZONE = 'Europe/Bucharest';

/** Selectable hours (00–23), pre-rendered as two-digit option values. */
export const HOURS: readonly string[] = Array.from({ length: 24 }, (_, hour) =>
  String(hour).padStart(2, '0'),
);

/** The minutes a run normally starts on. Editing can widen this — see {@link minuteOptions}. */
export const QUARTER_HOURS: readonly string[] = ['00', '15', '30', '45'];

/** The date + time fields of the admin form, as the inputs hold them. */
export interface StartFields {
  /** `YYYY-MM-DD`, the format a native date input uses. */
  date: string;
  /** Two-digit hour, `00`–`23`. */
  hour: string;
  /** Two-digit minute. */
  minute: string;
}

/**
 * Split a stored instant (`2026-10-01T18:30:00+03:00`) into the date, hour and minute of the same
 * moment **in Bucharest** — so an admin in any timezone edits the time the runners will turn up at.
 * `en-CA` is used purely because it formats dates as `YYYY-MM-DD`.
 */
export function toStartFields(startDateTime: string): StartFields {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: EVENT_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(startDateTime));
  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((candidate) => candidate.type === type)?.value ?? '';

  return {
    date: `${part('year')}-${part('month')}-${part('day')}`,
    hour: part('hour'),
    minute: part('minute'),
  };
}

/** The zone-less local date-time the API expects; the server reads it as Bucharest time. */
export function toLocalDateTime({ date, hour, minute }: StartFields): string {
  return `${date}T${hour}:${minute}`;
}

/**
 * The minutes to offer in the select. Normally the quarter hours — but an event already starting on
 * some other minute keeps its own value in the list, so opening the edit form can never silently
 * round its start to the nearest quarter.
 */
export function minuteOptions(minute: string): readonly string[] {
  if (!minute || QUARTER_HOURS.includes(minute)) {
    return QUARTER_HOURS;
  }
  return [...QUARTER_HOURS, minute].sort();
}
