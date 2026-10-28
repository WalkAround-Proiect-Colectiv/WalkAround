export type RegisteredUser = {
  id: string;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  profilePictureUrl: string | null;
  description: string | null;
};

export async function registerWithGoogle(
  apiBaseUrl: string,
  idToken: string,
  signal?: AbortSignal,
): Promise<RegisteredUser> {
  if (!apiBaseUrl) {
    throw new Error('Registration is unavailable. Please try again later.');
  }
  const response = await fetch(`${apiBaseUrl}/api/registrations/google`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
    signal,
  });
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Google did not return a valid registration request. Please try again.',
      401: 'Google verification failed. Please try again.',
      409: 'You already have a WalkAround account. Please sign in when login is available.',
      503: 'Google registration is currently unavailable. Please try again later.',
    };
    throw new Error(messages[response.status] ?? 'Registration failed. Please try again later.');
  }
  return response.json();
}
