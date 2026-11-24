import * as SecureStore from 'expo-secure-store';

import type { TokenResponse } from '@/types/api/auth';
import type { UserResponse } from '@/types/api/user';

export type SessionState =
  { status: 'loading' } | { status: 'guest' } | { status: 'authenticated'; tokens: TokenResponse };

const KEY = 'session';
const OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
};

function parse(value: string): TokenResponse | null {
  try {
    const parsed = JSON.parse(value) as Partial<TokenResponse> | null;
    const valid =
      typeof parsed?.accessToken === 'string' &&
      typeof parsed.refreshToken === 'string' &&
      typeof parsed.user?.id === 'number' &&
      typeof parsed.user.nickname === 'string' &&
      typeof parsed.user.role === 'string';
    return valid ? (parsed as TokenResponse) : null;
  } catch {
    return null;
  }
}

export function createSessionStore() {
  let state: SessionState = { status: 'loading' };
  // 로그인·로그아웃마다 올라간다. 예전 세션의 늦은 응답을 걸러내는 데 쓴다.
  let generation = 0;
  let writes: Promise<void> = Promise.resolve();
  const listeners = new Set<() => void>();

  function setState(next: SessionState) {
    state = next;
    listeners.forEach((listener) => listener());
  }

  // 저장소 쓰기는 호출한 순서대로 하나씩 한다. 실패해도 메모리의 세션은 유지한다.
  function enqueue(write: () => Promise<void>) {
    writes = writes.then(write).catch(() => {});
    return writes;
  }

  function persist(tokens: TokenResponse) {
    return enqueue(() => SecureStore.setItemAsync(KEY, JSON.stringify(tokens), OPTIONS));
  }

  function remove() {
    return enqueue(() => SecureStore.deleteItemAsync(KEY, OPTIONS));
  }

  return {
    getState: () => state,
    getGeneration: () => generation,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    async load() {
      let stored: string | null = null;
      try {
        stored = await SecureStore.getItemAsync(KEY, OPTIONS);
      } catch {
        // 읽지 못하면 로그인하지 않은 것으로 본다.
      }
      if (state.status !== 'loading') {
        return;
      }
      const tokens = stored === null ? null : parse(stored);
      setState(tokens ? { status: 'authenticated', tokens } : { status: 'guest' });
      if (stored !== null && !tokens) {
        await remove();
      }
    },
    signIn(tokens: TokenResponse) {
      generation += 1;
      setState({ status: 'authenticated', tokens });
      return persist(tokens);
    },
    async updateTokens(tokens: TokenResponse, expectedGeneration: number) {
      if (expectedGeneration !== generation || state.status !== 'authenticated') {
        return false;
      }
      setState({ status: 'authenticated', tokens });
      await persist(tokens);
      return true;
    },
    async updateUser(user: UserResponse) {
      if (state.status !== 'authenticated' || state.tokens.user.id !== user.id) {
        return;
      }
      const tokens = { ...state.tokens, user };
      setState({ status: 'authenticated', tokens });
      await persist(tokens);
    },
    signOut() {
      generation += 1;
      setState({ status: 'guest' });
      return remove();
    },
  };
}

export type SessionStore = ReturnType<typeof createSessionStore>;

export const sessionStore = createSessionStore();
