import type { SocialProvider } from '@/types/api/auth';

export type SocialResult = { type: 'success'; token: string } | { type: 'cancelled' };

export interface SocialAdapter {
  provider: SocialProvider;
  isAvailable(): boolean;
  /** 실패하면 SocialSignInError. 사용자가 닫으면 { type: 'cancelled' }. */
  signIn(): Promise<SocialResult>;
  signOut(): Promise<void>;
}

export class SocialSignInError extends Error {
  readonly name = 'SocialSignInError';

  constructor(message = '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.') {
    super(message);
  }
}

export const CANCELLED: SocialResult = { type: 'cancelled' };

/** 토큰이 비어 있으면 실패로 본다. */
export function success(token: string | null | undefined): SocialResult {
  if (!token) {
    throw new SocialSignInError();
  }
  return { type: 'success', token };
}

/** SDK 오류를 취소와 실패로 나눈다. cancelCodes에 있는 code면 취소다. */
export async function runSignIn(
  signIn: () => Promise<SocialResult>,
  cancelCodes: string[] = [],
): Promise<SocialResult> {
  try {
    return await signIn();
  } catch (error) {
    const code = (error as { code?: unknown } | null)?.code;
    if (typeof code === 'string' && cancelCodes.includes(code)) {
      return CANCELLED;
    }
    throw new SocialSignInError();
  }
}
