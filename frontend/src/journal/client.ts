import { authenticatedRequest } from '../login/api/session';
import { createJournalApi } from './api';

export const journalApi = createJournalApi(authenticatedRequest);