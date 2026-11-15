export const REMINDER_OPTIONS: { minutes: number | null; label: string }[] = [
  { minutes: null, label: 'Off' },
  { minutes: 60, label: '1 hour before' },
  { minutes: 1440, label: '1 day before' },
  { minutes: 10080, label: '1 week before' },
];

export function reminderLabel(minutes: number | null): string {
  return REMINDER_OPTIONS.find(option => option.minutes === minutes)?.label
    ?? (minutes === null ? 'Off' : `${minutes} minutes before`);
}

/** "Fri 6 Nov, 20:00", or "Fri 6 Nov" for all-day events, in the device's time zone. */
export function formatEventDate(startsAt: string, allDay: boolean): string {
  const date = new Date(startsAt);
  if (Number.isNaN(date.getTime())) return '';
  const day = date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
  if (allDay) return day;
  const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  return `${day}, ${time}`;
}

/** Formatted event date/time for map popups and cards, falling back to provider `whenText`. */
export function formatEventWhen(
  startsAt: string,
  allDay: boolean,
  whenText?: string | null,
): string {
  const formatted = formatEventDate(startsAt, allDay);
  if (formatted) return formatted;
  return (whenText ?? '').trim();
}

/** Opens only https links, so provider data cannot trigger other URL schemes. */
export function isSafeLink(url: string | null | undefined): url is string {
  return typeof url === 'string' && /^https:\/\/[^\s]+$/.test(url);
}
