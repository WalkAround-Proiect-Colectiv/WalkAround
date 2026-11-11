export type RouteInstruction = {
  instruction: string;
  distanceMeters: number;
  coordinate: [number, number];
  type?: number;
  verbalAlert?: string;
  verbalPre?: string;
};

function cleanInstructionText(value: string | undefined, fallback: string): string {
  const candidate = value && value.trim().length > 0 ? value.trim() : fallback.trim();
  return candidate.replace(/\s+/g, ' ');
}

function lowerCaseFirstLetter(text: string): string {
  if (!text) return text;
  return text.charAt(0).toLowerCase() + text.slice(1);
}

export function formatDistance(meters: number): string {
  const safeMeters = Number.isFinite(meters) ? meters : 10;
  const rounded = Math.max(10, Math.round(safeMeters / 10) * 10);
  return `${rounded} meters`;
}

export function approachPhrase(maneuver: RouteInstruction, meters: number): string {
  const alert = cleanInstructionText(maneuver.verbalAlert, maneuver.instruction);
  if (!alert) return '';
  return `In ${formatDistance(meters)}, ${lowerCaseFirstLetter(alert)}`;
}

export function immediatePhrase(maneuver: RouteInstruction): string {
  return cleanInstructionText(maneuver.verbalPre, maneuver.instruction);
}

export function startPhrase(first?: RouteInstruction | null): string {
  if (!first) return 'Proceed along the highlighted route.';
  const text = cleanInstructionText(first.verbalPre, first.instruction);
  return text || 'Proceed along the highlighted route.';
}

export function arrivalPhrase(): string {
  return 'You have arrived at your destination';
}
