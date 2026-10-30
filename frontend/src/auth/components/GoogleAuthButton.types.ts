export type GoogleAuthButtonProps = {
  intent?: 'login' | 'register';
  disabled: boolean;
  onCredential: (idToken: string) => void;
  onError: (message: string) => void;
};
