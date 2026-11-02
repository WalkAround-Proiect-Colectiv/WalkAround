import type { RegisteredUser } from '../../register/api/registration';

export type Session = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: RegisteredUser;
};

export type MobileSession = { session: Session; refreshToken: string };
