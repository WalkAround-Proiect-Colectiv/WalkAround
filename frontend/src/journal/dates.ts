export function isDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function addNights(start: string, nights: number): string {
  if (!isDate(start) || !Number.isInteger(nights) || nights < 1 || nights > 365) return '';
  const date = new Date(`${start}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + nights);
  return date.toISOString().slice(0, 10);
}

export function departureWindow(from: string, until: string, today: string) {
  const minimum = from.slice(0, 10) > today ? from.slice(0, 10) : today;
  const maximum = until.slice(0, 10);
  return { minimum, maximum, available: minimum <= maximum };
}

export function dateError(today: string, start: string, end: string): string | null {
  if (!isDate(start) || start < today) return 'Choose a start date of today or later.';
  if (!isDate(end) || end < start) return 'Choose an end date on or after the start date.';
  return null;
}

export function dateLabel(value: string): string {
  return isDate(value) ? new Date(`${value}T12:00:00Z`).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' }) : 'Choose date';
}