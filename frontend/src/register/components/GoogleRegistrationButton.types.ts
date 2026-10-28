export type GoogleRegistrationButtonProps = {
  disabled: boolean;
  onCredential: (idToken: string) => void;
  onError: (message: string) => void;
};
