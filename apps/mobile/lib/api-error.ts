export interface FieldError {
  field: string;
  reason: string;
}

export class ApiError extends Error {
  readonly name = 'ApiError';

  constructor(
    readonly code: string,
    message: string,
    /** HTTP 상태. 응답을 받지 못했으면 0. */
    readonly status: number,
    readonly fields?: FieldError[],
  ) {
    super(message);
  }
}

export function networkError() {
  return new ApiError('CLIENT_NETWORK', '네트워크 연결을 확인한 뒤 다시 시도해 주세요.', 0);
}

export function badResponseError(status: number) {
  return new ApiError(
    'CLIENT_BAD_RESPONSE',
    '서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해 주세요.',
    status,
  );
}

export function isNotFound(error: unknown) {
  return error instanceof ApiError && error.status === 404;
}
