export interface GroupPlanSavedNotice {
  groupId: string;
  planId: string;
}

type Listener = (notice: GroupPlanSavedNotice) => void;

const listeners = new Set<Listener>();

export function subscribeToGroupPlanSaves(listener: Listener): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function publishGroupPlanSaved(notice: GroupPlanSavedNotice): void {
  listeners.forEach((listener) => listener(notice));
}
