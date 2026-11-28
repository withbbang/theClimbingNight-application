import { screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { fail, mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { GridPostResponse, ProfileResponse } from '@/types/api/profile';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const profile = (over: Partial<ProfileResponse> = {}): ProfileResponse => ({
  id: 7,
  nickname: '달이',
  bio: null,
  profileImage: null,
  postCount: 0,
  mine: false,
  ...over,
});

const page = (items: GridPostResponse[]) => ok({ items, nextCursor: null });
const cell = (id: number): GridPostResponse => ({
  id,
  thumbnailUrl: `https://media.test/p/${id}/t.webp`,
  mediaCount: 1,
  status: null,
});

it('says the user does not exist on 404', async () => {
  // given
  await startAs('USER');
  mockServer({
    '/users/7/profile': fail(404, 'COMMON_NOT_FOUND', '찾을 수 없어요.'),
    '/users/7/posts?size=18': fail(404, 'COMMON_NOT_FOUND', '찾을 수 없어요.'),
  });

  // when
  await renderApp('/users/7');

  // then
  expect(screen.getByText('없는 사용자예요')).toBeOnTheScreen();
});

it('shows the empty grid message on someone else', async () => {
  // given
  await startAs('USER');
  mockServer({ '/users/7/profile': ok(profile()), '/users/7/posts?size=18': page([]) });

  // when
  await renderApp('/users/7');

  // then
  expect(screen.getByText('아직 올린 사진이 없어요')).toBeOnTheScreen();
  expect(screen.queryByText('첫 사진을 올려 보세요')).toBeNull();
});

it('invites me to post my first photo on my empty profile', async () => {
  // given
  await startAs('USER');
  mockServer({
    '/users/1/profile': ok(profile({ id: 1, nickname: '별이', mine: true })),
    '/users/1/posts?size=18': page([]),
    '/posts/mine/in-progress': ok([]),
  });

  // when
  const app = await renderApp('/users/1');
  await press('사진 올리기');

  // then
  expect(app.getPathname()).toBe('/posts/new');
});

it('shows my posts in progress above my grid', async () => {
  // given
  await startAs('USER');
  mockServer({
    '/users/1/profile': ok(profile({ id: 1, nickname: '별이', mine: true, postCount: 1 })),
    '/users/1/posts?size=18': page([cell(3)]),
    '/posts/mine/in-progress': ok([post({ id: 9, mine: true, status: 'PENDING' })]),
  });

  // when
  await renderApp('/users/1');

  // then
  expect(screen.getByText('사진 정리 중…')).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '게시물' })).toBeOnTheScreen();
});

it('does not ask for my posts in progress on someone else', async () => {
  // given
  await startAs('USER');
  const server = mockServer({
    '/users/7/profile': ok(profile()),
    '/users/7/posts?size=18': page([cell(3)]),
  });

  // when
  await renderApp('/users/7');

  // then
  expect(server.count('/posts/mine/in-progress')).toBe(0);
});

it('blocks the user after confirming and leaves the profile', async () => {
  // given
  await startAs('USER');
  const server = mockServer({
    '/users/7/profile': ok(profile()),
    '/users/7/posts?size=18': page([]),
    'POST /blocks': ok({ userId: 7, nickname: '달이', profileImageUrl: null }),
  });
  const app = await renderApp('/users/7');
  const invalidate = jest.spyOn(queryClient, 'invalidateQueries');

  // when
  await press('달이 프로필 메뉴');
  await press('차단하기');
  await press('차단 확정');

  // then
  expect(server.calls).toContainEqual({ method: 'POST', path: '/blocks', body: { userId: 7 } });
  expect(app.getPathname()).not.toBe('/users/7');
  expect(invalidate).toHaveBeenCalledWith({ queryKey: ['users', 7, 'profile'] });
  invalidate.mockRestore();
});
