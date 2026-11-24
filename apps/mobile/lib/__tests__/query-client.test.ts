import { ApiError, networkError } from '@/lib/api-error';
import { queryClient } from '@/lib/query-client';

const invalidate = jest.spyOn(queryClient, 'invalidateQueries');

beforeEach(() => {
  queryClient.clear();
  invalidate.mockClear();
});

async function failQuery(error: Error) {
  await queryClient
    .fetchQuery({ queryKey: ['x'], queryFn: () => Promise.reject(error), retry: false })
    .catch(() => {});
}

it('refetches my info when a query is forbidden', async () => {
  await failQuery(new ApiError('AUTH_FORBIDDEN', '권한이 없습니다.', 403));
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 'me'] });
});

it('refetches my info when a mutation is forbidden', async () => {
  await queryClient
    .getMutationCache()
    .build(queryClient, {
      mutationFn: () => Promise.reject(new ApiError('AUTH_FORBIDDEN', '권한이 없습니다.', 403)),
    })
    .execute(undefined)
    .catch(() => {});
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 'me'] });
});

it.each([new ApiError('COMMON_NOT_FOUND', '없음', 404), networkError()])(
  'does not refetch my info for %p',
  async (error) => {
    await failQuery(error);
    expect(invalidate).not.toHaveBeenCalled();
  },
);
