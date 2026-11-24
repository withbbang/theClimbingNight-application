import { ApiError } from '@/lib/api-error';
import { SocialSignInError } from '@/lib/social/types';

export function errorMessage(error: unknown): string {
  return error instanceof ApiError || error instanceof SocialSignInError
    ? error.message
    : '문제가 생겼습니다. 잠시 후 다시 시도해 주세요.';
}
