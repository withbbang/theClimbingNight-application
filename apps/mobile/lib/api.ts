import { ApiError, badResponseError, networkError, type FieldError } from '@/lib/api-error';
import { config } from '@/lib/config';
import { sessionStore } from '@/lib/session-store';
import type { TokenResponse } from '@/types/api/auth';

const TIMEOUT_MS = 15_000;

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface RequestOptions {
  method?: Method;
  body?: unknown;
}

interface ApiOptions extends RequestOptions {
  /** 기본 true. 로그인 API처럼 토큰을 보내지 않아야 하면 false. */
  auth?: boolean;
}

type Envelope =
  | { success: true; data: unknown }
  | { success: false; error: { code: string; message: string; fields?: FieldError[] } };

let onSessionExpired: () => void = () => {};

/** 리프레시까지 거부되어 로그아웃했을 때 한 번 불린다. */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

function isAuthRequired(error: unknown) {
  return error instanceof ApiError && error.status === 401 && error.code === 'AUTH_REQUIRED';
}

async function request<T>(path: string, options: RequestOptions, token?: string): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let status: number;
  let text: string;
  try {
    const response = await fetch(`${config.apiUrl}/api/v1${path}`, {
      method: options.method ?? 'GET',
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
    status = response.status;
    text = await response.text();
  } catch {
    throw networkError();
  } finally {
    clearTimeout(timer);
  }
  if (status === 204) {
    return undefined as T;
  }
  let envelope: Envelope | null;
  try {
    envelope = JSON.parse(text) as Envelope | null;
  } catch {
    throw badResponseError(status);
  }
  if (envelope?.success === true) {
    return envelope.data as T;
  }
  if (envelope?.success === false && envelope.error) {
    const { code, message, fields } = envelope.error;
    throw new ApiError(code, message, status, fields);
  }
  throw badResponseError(status);
}

let refreshing: { generation: number; promise: Promise<void> } | null = null;

/** 리프레시 토큰은 한 번만 쓸 수 있으므로 동시에 하나만 보낸다. */
function refresh(generation: number, refreshToken: string): Promise<void> {
  if (refreshing?.generation === generation) {
    return refreshing.promise;
  }
  const promise = (async () => {
    try {
      const tokens = await request<TokenResponse>('/auth/refresh', {
        method: 'POST',
        body: { refreshToken },
      });
      await sessionStore.updateTokens(tokens, generation);
    } catch (error) {
      if (isAuthRequired(error) && sessionStore.getGeneration() === generation) {
        await sessionStore.signOut();
        onSessionExpired();
      }
      throw error;
    }
  })();
  const current = { generation, promise };
  refreshing = current;
  const clear = () => {
    if (refreshing === current) {
      refreshing = null;
    }
  };
  promise.then(clear, clear);
  return promise;
}

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { auth = true, ...rest } = options;
  const session = sessionStore.getState();
  if (!auth || session.status !== 'authenticated') {
    return request<T>(path, rest);
  }
  const generation = sessionStore.getGeneration();
  const token = session.tokens.accessToken;
  try {
    return await request<T>(path, rest, token);
  } catch (error) {
    if (!isAuthRequired(error)) {
      throw error;
    }
    const before = sessionStore.getState();
    if (sessionStore.getGeneration() !== generation || before.status !== 'authenticated') {
      throw error;
    }
    if (before.tokens.accessToken === token) {
      await refresh(generation, before.tokens.refreshToken);
    }
    // 갱신하는 동안 로그아웃하거나 다른 계정으로 바뀌었으면 그 세션으로 다시 보내지 않는다.
    const after = sessionStore.getState();
    if (sessionStore.getGeneration() !== generation || after.status !== 'authenticated') {
      throw error;
    }
    return request<T>(path, rest, after.tokens.accessToken);
  }
}
