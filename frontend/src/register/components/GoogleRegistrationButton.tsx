import { useEffect, useRef } from 'react';
import { GoogleSignin, GoogleSigninButton, isSuccessResponse, isErrorWithCode, statusCodes } from '@react-native-google-signin/google-signin';
import { environment } from '../../config/environment';
import type { GoogleRegistrationButtonProps } from './GoogleRegistrationButton.types';

export default function GoogleRegistrationButton({ disabled, onCredential, onError }: GoogleRegistrationButtonProps) {
  const signingIn = useRef(false);
  useEffect(() => {
    GoogleSignin.configure({
      webClientId: environment.googleWebClientId,
      iosClientId: environment.googleIosClientId || undefined,
      offlineAccess: false,
    });
  }, []);

  async function signUp() {
    if (disabled || signingIn.current) return;
    signingIn.current = true;
    try {
      await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
      const result = await GoogleSignin.signIn();
      if (isSuccessResponse(result)) {
        if (result.data.idToken) onCredential(result.data.idToken);
        else onError('Google verification failed. Please try again.');
      }
    } catch (error) {
      if (!isErrorWithCode(error) || error.code !== statusCodes.SIGN_IN_CANCELLED) {
        onError('Could not connect to Google. Please try again.');
      }
    } finally {
      signingIn.current = false;
    }
  }

  return <GoogleSigninButton
    size={GoogleSigninButton.Size.Wide}
    color={GoogleSigninButton.Color.Light}
    disabled={disabled}
    onPress={signUp}
    accessibilityLabel="Sign up with Google"
    style={{ width: 280, height: 48 }}
  />;
}
