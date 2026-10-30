import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { getSession, subscribe, refreshSession } from '../api/session';

const ReadyContext = createContext(false);
export function SessionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    refreshSession().catch(() => {}).finally(() => { if (active) setReady(true); });
    const subscription = AppState.addEventListener('change', state => {
      const session = getSession();
      if (state === 'active' && session && Date.parse(session.expiresAt) <= Date.now() + 30000) {
        refreshSession().catch(() => {});
      }
    });
    return () => { active = false; subscription.remove(); };
  }, []);
  return <ReadyContext.Provider value={ready}>{children}</ReadyContext.Provider>;
}

export function useSession() {
  const session = useSyncExternalStore(subscribe, getSession, () => null);
  return { session, ready: useContext(ReadyContext) };
}
