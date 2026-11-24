import { useSyncExternalStore } from 'react';

import { sessionStore } from '@/lib/session-store';

export function useSession() {
  return useSyncExternalStore(sessionStore.subscribe, sessionStore.getState);
}
