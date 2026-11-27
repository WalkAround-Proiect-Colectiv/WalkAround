type Listener = () => void;

const listeners = new Set<Listener>();

export function subscribeToInboxChanges(listener: Listener): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function publishInboxChanged(): void {
  listeners.forEach(listener => listener());
}
