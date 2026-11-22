export type PoiComment = {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string;
  updatedAt: string;
};

export type CommentPage = { items: PoiComment[]; nextCursor: string | null };
type Sender = <Result>(path: string, options?: RequestInit) => Promise<Result>;

export function createCommentsApi(read: Sender, write: Sender) {
  const json = (method: string, body: unknown): RequestInit => ({
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  return {
    list(poiId: string, cursor?: string | null) {
      const params = new URLSearchParams({ poiId });
      if (cursor) params.set('cursor', cursor);
      return read<CommentPage>(`/api/poi-comments?${params}`);
    },
    create: (poiId: string, body: string) => write<PoiComment>('/api/poi-comments', json('POST', { poiId, body: body.trim() })),
    edit: (id: string, body: string) => write<PoiComment>(`/api/poi-comments/${encodeURIComponent(id)}`, json('PUT', { body: body.trim() })),
    remove: (id: string) => write<void>(`/api/poi-comments/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    report: (id: string, reason: string) => write<void>(`/api/poi-comments/${encodeURIComponent(id)}/reports`, json('POST', { reason: reason.trim() })),
  };
}

export function mergeComments(current: PoiComment[], incoming: PoiComment[]): PoiComment[] {
  const seen = new Set(current.map(comment => comment.id));
  return [...current, ...incoming.filter(comment => {
    if (seen.has(comment.id)) return false;
    seen.add(comment.id);
    return true;
  })];
}

export function commentError(error: unknown): string {
  const status = error && typeof error === 'object' && 'status' in error ? error.status : null;
  switch (status) {
    case 400: return 'Check your comment or report and try again.';
    case 401: return 'Please sign in again to continue.';
    case 403: return 'You do not have permission to change this comment.';
    case 404: return 'This place or comment is no longer available.';
    case 429: return 'Too many changes. Please wait a minute and try again.';
    default: return 'Comments are unavailable right now. Please try again.';
  }
}