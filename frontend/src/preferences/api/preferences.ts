import { authenticatedRequest } from '../../login/api/session';
import { ApiError } from '../../login/api/http';

export type PreferenceOption = { id: string; label: string };
export type PreferenceQuestion = { id: string; title: string; options: PreferenceOption[] };
export type Preferences = { questions: PreferenceQuestion[]; selectedOptionIds: string[] };

export async function getPreferences(): Promise<Preferences> {
  try { return await authenticatedRequest<Preferences>('/api/users/me/preferences'); }
  catch (error) {
    if (error instanceof ApiError && error.status >= 500) throw new Error('Could not load your preferences. Please try again.');
    throw error;
  }
}

export async function savePreferences(selectedOptionIds: string[]): Promise<Preferences> {
  try {
    return await authenticatedRequest<Preferences>('/api/users/me/preferences', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ selectedOptionIds }),
    });
  } catch (error) {
    if (error instanceof ApiError && error.status >= 500) throw new Error('Could not save your preferences. Please try again.');
    if (error instanceof ApiError && error.status === 400) {
      throw new Error(error.detail ?? 'Choose from the available options and try again.');
    }
    throw error;
  }
}
