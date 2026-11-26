import { screen } from 'expo-router/testing-library';

import { fail, mockServer, ok, renderApp } from '@/test-utils/app';
import { media, post } from '@/test-utils/posts';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

it('sends a guest to the login screen when the post needs sign-in', async () => {
  // given
  await startAs(null);
  mockServer({ '/posts/4': fail(401, 'AUTH_REQUIRED', '로그인이 필요합니다.') });

  // when
  const app = await renderApp('/posts/4');

  // then
  expect(app.getPathname()).toBe('/login');
});

it('shows the hidden notice on my hidden post', async () => {
  // given
  await startAs('USER');
  mockServer({
    '/posts/5': ok(
      post({
        id: 5,
        mine: true,
        status: 'HIDDEN',
        media: [media(51, { url: null, thumbnailUrl: null })],
      }),
    ),
  });

  // when
  await renderApp('/posts/5');

  // then
  expect(screen.getByText('운영 정책에 따라 숨겨졌어요')).toBeOnTheScreen();
  expect(screen.getByText('오늘 초록 문제 완등')).toBeOnTheScreen();
});

it('shows not found for a missing post', async () => {
  // given
  await startAs('USER');
  mockServer({ '/posts/6': fail(404, 'COMMON_NOT_FOUND', '찾을 수 없습니다.') });

  // when
  await renderApp('/posts/6');

  // then
  expect(screen.getByText('게시물을 찾을 수 없어요')).toBeOnTheScreen();
});
