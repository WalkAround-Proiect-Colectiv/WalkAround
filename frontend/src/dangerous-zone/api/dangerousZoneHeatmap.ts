import type { DangerousZoneHeatmapResponse } from '../dangerousZone.types';

export async function requestDangerousZonesHeatmap(apiBaseUrl: string, fetcher: typeof fetch = fetch): Promise<DangerousZoneHeatmapResponse[]> {
    if (!apiBaseUrl) return [];

    const url = `${apiBaseUrl.replace(/\/$/, '')}/api/v1/dangerous-zones`;
    const response = await fetcher(url, { method: 'GET' });
    if (!response.ok) {
        throw new Error(`Danger zones API returned HTTP ${response.status}. Check that the Application API and routing service are running.`);
    }
    if (!response.headers.get('content-type')?.toLowerCase().includes('json')) {
        throw new Error(`Danger zones API returned a non-JSON response. Check EXPO_PUBLIC_API_BASE_URL (${url}) and the Application API route.`);
    }

    let data: unknown;
    try {
        data = await response.json();
    } catch {
        throw new Error('Danger zones API returned invalid JSON.');
    }
    if (!Array.isArray(data)) {
        throw new Error('Danger zones API returned an invalid heatmap payload.');
    }
    return data.filter((zone): zone is DangerousZoneHeatmapResponse =>
        !!zone && typeof zone === 'object' &&
        typeof zone.h3Index === 'string' &&
        typeof zone.intensity === 'number' && Number.isFinite(zone.intensity) &&
        Array.isArray(zone.topCategories) && zone.topCategories.every((category: unknown) => typeof category === 'string')
    );
}
