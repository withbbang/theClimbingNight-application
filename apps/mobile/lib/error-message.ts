import { ApiError } from '@/lib/api-error';
import { SocialSignInError } from '@/lib/social/types';

export function errorMessage(error: unknown): string {
  return error instanceof ApiError || error instanceof SocialSignInError
    ? error.message
    : '문제가 생겼습니다. 잠시 후 다시 시도해 주세요.';
}

/** 응답을 읽지 못했지만 서버는 처리했을 수 있다. */
export function mayBeDone(error: unknown) {
  return (
    error instanceof ApiError &&
    (error.code === 'CLIENT_TIMEOUT' || error.code === 'CLIENT_BAD_RESPONSE')
  );
}

/** 보내기 전에 끊겼는지, 보낸 뒤에 끊겼는지 알 수 없다. */
export function isDisconnected(error: unknown) {
  return error instanceof ApiError && error.code === 'CLIENT_NETWORK';
}

/** 새로 만드는 요청은 다시 보내면 둘이 되므로 먼저 확인하게 한다. */
export function createErrorMessage(error: unknown) {
  if (isDisconnected(error)) {
    return '올라갔는지 확인하지 못했어요. 연결을 확인하고 목록을 본 뒤 다시 시도해 주세요.';
  }
  return mayBeDone(error)
    ? '이미 올라갔을 수 있어요. 목록을 확인한 뒤 다시 시도해 주세요.'
    : errorMessage(error);
}
