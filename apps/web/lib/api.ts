import { ApiError, badResponseError, networkError, timeoutError } from '@/lib/api-error';

const TIMEOUT_MS = 15_000;

type Envelope =
  { success: true; data: unknown } | { success: false; error: { code: string; message: string } };

/** 로그인 없는 조회만 한다. 같은 주소의 /api로 보낸다. */
export async function apiFetch<T>(path: string): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let status: number;
  let text: string;
  try {
    const response = await fetch(`/api/v1${path}`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    status = response.status;
    text = await response.text();
  } catch {
    throw controller.signal.aborted ? timeoutError() : networkError();
  } finally {
    clearTimeout(timer);
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
    throw new ApiError(envelope.error.code, envelope.error.message, status);
  }
  throw badResponseError(status);
}
