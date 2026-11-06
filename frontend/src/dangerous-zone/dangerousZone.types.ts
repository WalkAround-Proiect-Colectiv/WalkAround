export enum DangerousZoneCategory
{
    CRIME = 'CRIME',
    WILD_ANIMALS = 'WILD_ANIMALS',
    NO_LIGHTING = 'NO_LIGHTING',
    HARASSMENT_RISK='HARASSMENT_RISK',
    BAD_INFRASTRUCTURE = 'BAD_INFRASTRUCTURE',
}

export const CATEGORY_LABELS: Record<DangerousZoneCategory, string> = {
    CRIME: 'High crime rate',
    WILD_ANIMALS: 'Wild animals or stray dogs',
    NO_LIGHTING: 'No public lighting',
    HARASSMENT_RISK: 'People with bad intentions',
    BAD_INFRASTRUCTURE: 'Difficult area to traverse',
};

export type DangerousZoneRequestPayload = {
    lat: number;
    lng: number;
    category: DangerousZoneCategory;
    description?: string;
    is24_7: boolean;
    startTime?: string;
    endTime?: string;
};

export interface DangerousZoneHeatmapResponse {
    h3Index: string;
    intensity: number;
    topCategories: string[];
}