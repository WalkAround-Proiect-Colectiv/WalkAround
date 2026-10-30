import { useEffect, useRef } from 'react';
import { environment } from '../../config/environment';
import type { GoogleAuthButtonProps } from './GoogleAuthButton.types';

type GoogleIdentityServices = {
  accounts: {
    id: {
      initialize: (options: { client_id: string; callback: (result: { credential: string }) => void; auto_select: boolean }) => void;
      renderButton: (element: HTMLElement, options: { type: string; theme: string; size: string; text: string; width: number }) => void;
    };
  };
};
type GoogleAuthState = {
  scriptPromise?: Promise<void>;
  clientId?: string;
  onCredential?: (credential: string) => void;
};

declare global {
  interface Window {
    google?: GoogleIdentityServices;
    __walkaroundGoogleAuth?: GoogleAuthState;
  }
}

// Keep the SDK lifecycle page-scoped, including during Expo Fast Refresh.
function getGoogleState(): GoogleAuthState {
  return window.__walkaroundGoogleAuth ??= {};
}

function loadGoogle(): Promise<void> {
  if (window.google) return Promise.resolve();
  const state = getGoogleState();
  if (!state.scriptPromise) {
    state.scriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        script.remove();
        state.scriptPromise = undefined;
        reject(new Error('Google could not be loaded'));
      };
      document.head.appendChild(script);
    });
  }
  return state.scriptPromise;
}

export default function GoogleAuthButton(props: GoogleAuthButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const latest = useRef(props);
  useEffect(() => { latest.current = props; }, [props]);

  useEffect(() => {
    if (!environment.googleWebClientId) return;
    let active = true;
    const state = getGoogleState();
    const onCredential = (credential: string) => {
      if (active && !latest.current.disabled) latest.current.onCredential(credential);
    };
    loadGoogle().then(() => {
      if (!active || !container.current || !window.google) return;
      if (state.clientId && state.clientId !== environment.googleWebClientId) {
        throw new Error('Reload the page after changing the Google client ID');
      }
      state.onCredential = onCredential;
      if (!state.clientId) {
        window.google.accounts.id.initialize({
          client_id: environment.googleWebClientId,
          auto_select: false,
          callback: ({ credential }) => state.onCredential?.(credential),
        });
        state.clientId = environment.googleWebClientId;
      }
      container.current.replaceChildren();
      window.google.accounts.id.renderButton(container.current, {
        type: 'standard', theme: 'outline', size: 'large', text: props.intent === 'login' ? 'signin_with' : 'signup_with', width: 280,
      });
    }).catch(() => {
      if (active) latest.current.onError('Could not load Google. Check your connection and reload.');
    });
    return () => {
      active = false;
      if (state.onCredential === onCredential) state.onCredential = undefined;
    };
  }, [props.intent]);

  return <div
    aria-disabled={props.disabled}
    inert={props.disabled}
    style={{
      position: 'relative', width: 280, maxWidth: '100%', height: 48, flexShrink: 0,
      opacity: props.disabled ? 0.5 : 1, pointerEvents: props.disabled ? 'none' : 'auto',
    }}
  >
    {/* Google's fallback and personalized iframe must not resize the surrounding card. */}
    <div ref={container} style={{
      position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center',
    }} />
  </div>;
}
