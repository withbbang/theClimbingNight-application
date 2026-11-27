import { fireEvent, screen } from 'expo-router/testing-library';

import { flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const FIRST = '/posts/5/likes?size=20';
const NEXT = '/posts/5/likes?size=20&cursor=c1';

it('lists likers newest first', async () => {
  // given
  await startAs('USER');
  mockServer({
    '/posts/5': ok(post({ id: 5, likeCount: 2 })),
    [FIRST]: ok({
      items: [
        { userId: 9, nickname: '구름' },
        { userId: 3, nickname: '바위' },
      ],
      nextCursor: null,
    }),
  });
  const app = await renderApp('/posts/5');

  // when
  await press('좋아요 2개');

  // then
  expect(app.getPathname()).toBe('/posts/5/likes');
  const names = screen.getAllByTestId('liker-name').map((n) => n.props.children);
  expect(names).toEqual(['구름', '바위']);
});

it('loads the next page at the end', async () => {
  // given
  await startAs('USER');
  const server = mockServer({
    [FIRST]: ok({ items: [{ userId: 9, nickname: '구름' }], nextCursor: 'c1' }),
    [NEXT]: ok({ items: [{ userId: 3, nickname: '바위' }], nextCursor: null }),
  });
  await renderApp('/posts/5/likes');

  // when
  await fireEvent(screen.getByLabelText('좋아요 누른 사람'), 'endReached');
  await flush();

  // then
  expect(server.count(NEXT)).toBe(1);
  expect(screen.getByText('바위')).toBeOnTheScreen();
});

it('shows an empty message', async () => {
  // given
  await startAs('USER');
  mockServer({ [FIRST]: ok({ items: [], nextCursor: null }) });

  // when
  await renderApp('/posts/5/likes');

  // then
  expect(screen.getByText('아직 좋아요가 없어요')).toBeOnTheScreen();
});

it('hides the like count line when nobody liked', async () => {
  // given
  await startAs('USER');
  mockServer({ '/posts/5': ok(post({ id: 5, likeCount: 0 })) });

  // when
  await renderApp('/posts/5');

  // then
  expect(screen.queryByRole('button', { name: /^좋아요 \d+개$/ })).toBeNull();
});
