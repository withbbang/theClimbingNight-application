import { createElement, type ReactNode } from 'react';

import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { useUserPosts, useUserProfile } from '@/hooks/use-user-profile';
import { profileApi } from '@/lib/profile-api';
import { sessionStore } from '@/lib/session-store';
import { tokens } from '@/test-utils/tokens';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('@/lib/profile-api', () => ({
  profileApi: { profile: jest.fn(), posts: jest.fn() },
}));

let client: QueryClient;
afterEach(() => client.clear());

async function setup<T>(hook: () => T, signedIn = true) {
  await sessionStore.signOut();
  if (signedIn) {
    await sessionStore.signIn(tokens('1', 'MEMBER'));
  }
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return renderHook(hook, { wrapper });
}

beforeEach(() => jest.resetAllMocks());

it('fetches the profile under users/{id}/profile', async () => {
  const profile = {
    id: 7,
    nickname: '달이',
    bio: null,
    profileImage: null,
    postCount: 0,
    mine: false,
  };
  jest.mocked(profileApi.profile).mockResolvedValue(profile);

  const { result } = await setup(() => useUserProfile(7));

  await waitFor(() => expect(result.current.data).toEqual(profile));
  expect(client.getQueryData(['users', 7, 'profile'])).toEqual(profile);
});

it('does not fetch while signed out', async () => {
  await setup(() => useUserProfile(7), false);

  expect(profileApi.profile).not.toHaveBeenCalled();
});

it('pages the grid under users/{id}/posts with the cursor', async () => {
  jest
    .mocked(profileApi.posts)
    .mockResolvedValueOnce({
      items: [{ id: 1, thumbnailUrl: 't1', mediaCount: 1, status: null }],
      nextCursor: 'c1',
    })
    .mockResolvedValueOnce({
      items: [{ id: 2, thumbnailUrl: 't2', mediaCount: 1, status: null }],
      nextCursor: null,
    });

  const { result } = await setup(() => useUserPosts(7));
  await waitFor(() => expect(result.current.data).toHaveLength(1));
  await result.current.fetchNextPage();

  await waitFor(() => expect(result.current.data?.map((p) => p.id)).toEqual([1, 2]));
  expect(profileApi.posts).toHaveBeenLastCalledWith(7, 'c1');
});
