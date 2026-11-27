import { useCallback, useEffect, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { environment } from '../config/environment';
import { getUnreadCount } from './api/inbox';
import { subscribeToInboxChanges } from './liveInbox';

export function useMapInboxNotice(userId: string | null) {
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [showPopup, setShowPopup] = useState(false);

  useFocusEffect(useCallback(() => {
    let active = true;
    let latestRequest = 0;
    let entryPopupPending = true;

    setUnreadCount(null);
    setShowPopup(false);

    if (!userId) return () => { active = false; };

    const refresh = () => {
      const requestId = ++latestRequest;
      getUnreadCount()
        .then(count => {
          if (!active || requestId !== latestRequest) return;
          if (!Number.isSafeInteger(count) || count < 0) return;

          setUnreadCount(count);
          if (entryPopupPending) {
            setShowPopup(count > 0);
            entryPopupPending = false;
          } else if (count === 0) {
            setShowPopup(false);
          }
        })
        .catch(() => {
          if (active && requestId === latestRequest) {
            setUnreadCount(null);
            setShowPopup(false);
          }
        });
    };

    const unsubscribe = subscribeToInboxChanges(refresh);
    refresh();

    return () => {
      active = false;
      unsubscribe();
      setShowPopup(false);
    };
  }, [userId]));

  useEffect(() => {
    if (!showPopup) return;
    const timeout = setTimeout(() => setShowPopup(false), environment.notificationDisplayMs);
    return () => clearTimeout(timeout);
  }, [showPopup]);

  return { unreadCount, showPopup, dismissPopup: () => setShowPopup(false) };
}
