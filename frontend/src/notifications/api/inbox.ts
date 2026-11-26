import { authenticatedRequest } from '../../login/api/session';

export interface NotificationTarget {
  key: string;
  params: Record<string, string>;
}

export interface InboxNotification {
  id: string;
  category: string;
  title: string;
  body: string;
  target: NotificationTarget | null;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationPage {
  items: InboxNotification[];
  nextCursor: string | null;
}

export function listNotifications(before?: string): Promise<NotificationPage> {
  const query = before ? `?before=${encodeURIComponent(before)}` : '';
  return authenticatedRequest<NotificationPage>(`/api/notifications${query}`);
}

export function getUnreadCount(): Promise<number> {
  return authenticatedRequest<{ count: number }>('/api/notifications/unread-count')
    .then(result => result.count);
}

export function markNotificationRead(id: string): Promise<void> {
  return authenticatedRequest<void>(`/api/notifications/${encodeURIComponent(id)}/read`, {
    method: 'PUT',
  });
}
