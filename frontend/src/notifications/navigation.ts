import type { ImperativeRouter } from 'expo-router';
import type { NotificationTarget } from './api/inbox';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Only registered internal destinations can be opened. Unknown keys stay inert. */
export function openNotificationTarget(target: NotificationTarget | null, router: ImperativeRouter): boolean {
  if (!target || !target.params) return false;
  const { key, params } = target;

  if (key === 'trip_group' && UUID.test(params.groupId ?? '')) {
    router.push({ pathname: '/trip-groups', params: { groupId: params.groupId } });
    return true;
  }

  if (key === 'group_plan' && UUID.test(params.groupId ?? '') && UUID.test(params.planId ?? '')) {
    router.push({ pathname: '/trip-groups', params: {
      groupId: params.groupId, planId: params.planId, tab: 'plan',
    } });
    return true;
  }

  if (key === 'event' && /^[A-Za-z0-9_.:-]{1,64}$/.test(params.eventKey ?? '')
      && typeof params.localityKey === 'string' && params.localityKey.length <= 200
      && params.localityKey.length > 0) {
    router.push({ pathname: '/events/details', params: {
      eventKey: params.eventKey, localityKey: params.localityKey,
    } });
    return true;
  }

  return false;
}
