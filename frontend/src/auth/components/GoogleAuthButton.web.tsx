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
declare global {
  interface Window { google?: GoogleIdentityServices }
}

let scriptPromise: Promise<void> | undefined;
function loadGoogle(): Promise<void> {
  if (window.google) return Promise.resolve();
  if (!scriptPromise) {
    scriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        script.remove();
        scriptPromise = undefined;
        reject(new Error('Google could not be loaded'));
      };
      document.head.appendChild(script);
    });
  }
  return scriptPromise;
}

export default function GoogleAuthButton(props: GoogleAuthButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const latest = useRef(props);
  useEffect(() => { latest.current = props; }, [props]);

  useEffect(() => {
    if (!environment.googleWebClientId) return;
    let active = true;
    loadGoogle().then(() => {
      if (!active || !container.current || !window.google) return;
      window.google.accounts.id.initialize({
        client_id: environment.googleWebClientId,
        auto_select: false,
        callback: ({ credential }) => {
          if (active && !latest.current.disabled) latest.current.onCredential(credential);
        },
      });
      window.google.accounts.id.renderButton(container.current, {
        type: 'standard', theme: 'outline', size: 'large', text: props.intent === 'login' ? 'signin_with' : 'signup_with', width: 280,
      });
    }).catch(() => {
      if (active) latest.current.onError('Could not load Google. Check your connection and reload.');
    });
    return () => { active = false; };
  }, [props.intent]);

  return <div
    ref={container}
    aria-disabled={props.disabled}
    inert={props.disabled}
    style={{ minHeight: 44, opacity: props.disabled ? 0.5 : 1, pointerEvents: props.disabled ? 'none' : 'auto' }}
  />;
}
