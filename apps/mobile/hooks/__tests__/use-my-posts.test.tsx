import { createElement, type ReactNode } from 'react';

import { act, renderHook } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { useDeleteMyPost, useRefreshFeed } from '@/hooks/use-my-posts';
import { sessionStore } from '@/lib/session-store';
import { deletePost } from '@/lib/post-api';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/post-api', () => ({ deletePost: jest.fn(), fetchMyInProgress: jest.fn() }));

let client: QueryClient;
afterEach(() => client.clear());

async function setup<T>(hook: () => T) {
  await sessionStore.signOut();
  await sessionStore.signIn(tokens('1', 'MEMBER'));
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = jest.spyOn(client, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  const rendered = await renderHook(hook, { wrapper });
  return { ...rendered, invalidate };
}

beforeEach(() => {
  jest.mocked(deletePost).mockReset().mockResolvedValue(undefined as never);
});

it('refreshes my profile and grid after deleting a post', async () => {
  // given
  const { result, invalidate } = await setup(() => useDeleteMyPost());

  // when
  await act(async () => {
    await result.current.mutateAsync(5);
  });

  // then
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['posts'] });
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1] });
});

it('refreshes my profile and grid when a pending post finishes processing', async () => {
  // given
  const { result, invalidate } = await setup(() => useRefreshFeed());

  // when
  await act(async () => {
    await result.current();
  });

  // then
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 1] });
});
