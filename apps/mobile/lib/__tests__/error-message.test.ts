import { ApiError, badResponseError, networkError, timeoutError } from '@/lib/api-error';
import { createErrorMessage } from '@/lib/error-message';

const MAYBE = '이미 올라갔을 수 있어요. 목록을 확인한 뒤 다시 시도해 주세요.';

it.each([
  [timeoutError(), MAYBE],
  [badResponseError(502), MAYBE],
  [
    networkError(),
    '올라갔는지 확인하지 못했어요. 연결을 확인하고 목록을 본 뒤 다시 시도해 주세요.',
  ],
  [new ApiError('COMMON_INTERNAL_ERROR', '서버 오류', 500), '서버 오류'],
])('tells whether a create may have been saved: %s', (error, message) => {
  expect(createErrorMessage(error)).toBe(message);
});
