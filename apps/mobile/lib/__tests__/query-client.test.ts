import { QueryObserver } from '@tanstack/react-query';

import { ApiError, networkError } from '@/lib/api-error';
import { clearSessionCache, queryClient } from '@/lib/query-client';

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

it.each([['notices', 'recent'], ['app-settings'], ['posts'], ['posts', 1]])(
  'keeps an observer of public %p working after the session cache is cleared',
  async (...queryKey) => {
    const queryFn = jest.fn(async () => 1);
    const observer = new QueryObserver(queryClient, { queryKey, queryFn });
    const unsubscribe = observer.subscribe(() => {});
    await queryClient.getQueryCache().find({ queryKey })?.promise;
    await clearSessionCache();
    expect(queryFn).toHaveBeenCalledTimes(2);
    await queryClient.invalidateQueries({ queryKey });
    expect(queryFn).toHaveBeenCalledTimes(3);
    unsubscribe();
  },
);

it('removes everything else with the session', async () => {
  queryClient.setQueryData(['users', 'me'], 1);
  queryClient.setQueryData(['inquiries', 'me', 1], 1);
  queryClient.setQueryData(['meetups', 'detail', 1], 1);
  await clearSessionCache();
  expect(queryClient.getQueryCache().getAll()).toEqual([]);
});

it('drops what the previous session saw in a public feed query', async () => {
  // given
  queryClient.setQueryData(['posts'], 'signed-in feed');
  queryClient.setQueryData(['posts', 1], 'signed-in post');

  // when
  await clearSessionCache();

  // then
  expect(queryClient.getQueryData(['posts'])).toBeUndefined();
  expect(queryClient.getQueryData(['posts', 1])).toBeUndefined();
});

it('removes my in-progress posts with the session', async () => {
  // given
  queryClient.setQueryData(['posts', 'mine', 'in-progress'], 1);

  // when
  await clearSessionCache();

  // then
  expect(queryClient.getQueryCache().find({ queryKey: ['posts', 'mine', 'in-progress'] })).toBe(
    undefined,
  );
});
