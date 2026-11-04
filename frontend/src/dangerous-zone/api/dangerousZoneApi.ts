import { getSession, refreshSession } from '../../login/api/session';
import type { DangerousZoneRequestPayload } from '../dangerousZone.types';
import type { DangerousZoneHeatmapResponse } from '../dangerousZone.types';

export async function reportDangerousZone(payload: DangerousZoneRequestPayload): Promise<void> {
    let session = getSession();
    if (!session || Date.parse(session.expiresAt) <= Date.now() + 30000)
    {
        session = await refreshSession();
    }
    if (!session) throw new Error('Please sign in again.');
    const response = await fetch(`${process.env.EXPO_PUBLIC_API_BASE_URL}/api/v1/dangerous-zones`, {
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
    const response = await fetch(`${process.env.EXPO_PUBLIC_API_BASE_URL}/api/v1/dangerous-zones`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    });
    if (!response.ok) {
        throw new Error('Error when retrieving dangerous zones');
    }
    const text = await response.text();
    return text ? JSON.parse(text) : [];
}