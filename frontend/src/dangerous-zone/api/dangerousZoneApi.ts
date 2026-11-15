import { getSession, refreshSession } from '../../login/api/session';
import { environment } from '../../config/environment';
import type { DangerousZoneRequestPayload } from '../dangerousZone.types';
import type { DangerousZoneHeatmapResponse } from '../dangerousZone.types';
import { requestDangerousZonesHeatmap } from './dangerousZoneHeatmap';

function requireApiBaseUrl(): string {
    if (!environment.apiBaseUrl) {
        throw new Error('Application API is not configured. Set EXPO_PUBLIC_API_BASE_URL in frontend/.env.local.');
    }
    return environment.apiBaseUrl;
}

export async function reportDangerousZone(payload: DangerousZoneRequestPayload): Promise<void> {
    const apiBaseUrl = requireApiBaseUrl();
    let session = getSession();
    if (!session || Date.parse(session.expiresAt) <= Date.now() + 30000)
    {
        session = await refreshSession();
    }
    if (!session) throw new Error('Please sign in again.');
    const response = await fetch(`${apiBaseUrl}/api/v1/dangerous-zones`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${session.accessToken}`
        },
        body: JSON.stringify(payload)
    });
    if (!response.ok)
    {
        throw new Error('Error when reporting dangerous zone!');
    }
}

export async function fetchDangerousZonesHeatmap(): Promise<DangerousZoneHeatmapResponse[]> {
    return requestDangerousZonesHeatmap(environment.apiBaseUrl);
}