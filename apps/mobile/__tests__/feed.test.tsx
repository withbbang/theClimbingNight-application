import { fireEvent, screen } from 'expo-router/testing-library';

import { flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const FIRST = '/posts?size=10';
const NEXT = '/posts?size=10&cursor=c1';
const LOGIN_CARD = '더 많은 사진은 로그인하고 볼 수 있어요';
const three = [1, 2, 3].map((id) => post({ id, body: `${id}번째 글` }));

async function reachEnd() {
  await fireEvent(screen.getByLabelText('피드 게시물'), 'endReached');
  await flush();
}

it('shows the login card after three posts when signed out', async () => {
  // given
  await startAs(null);
  mockServer({ [FIRST]: ok({ items: three, nextCursor: null }) });

  // when
  await renderApp('/feed');

  // then
  expect(screen.getByText('3번째 글')).toBeOnTheScreen();
  expect(screen.getByText(LOGIN_CARD)).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '로그인하기' })).toBeOnTheScreen();
});

it('does not request a next page when signed out', async () => {
  // given: 서버가 실수로 커서를 주더라도
  await startAs(null);
  const server = mockServer({
    [FIRST]: ok({ items: three, nextCursor: 'c1' }),
    [NEXT]: ok({ items: [], nextCursor: null }),
  });
  await renderApp('/feed');

  // when
  await reachEnd();

  // then
  expect(server.count(NEXT)).toBe(0);
});

it('requests the next page at the end when signed in', async () => {
  // given
  await startAs('USER');
  const server = mockServer({
    [FIRST]: ok({ items: three, nextCursor: 'c1' }),
    [NEXT]: ok({ items: [post({ id: 4, body: '4번째 글' })], nextCursor: null }),
  });
  await renderApp('/feed');

  // when
  await reachEnd();

  // then
  expect(server.count(NEXT)).toBe(1);
  expect(screen.getByText('4번째 글')).toBeOnTheScreen();
  expect(screen.queryByText(LOGIN_CARD)).toBeNull();
});

it('shows the empty message when there are no posts', async () => {
  // given
  await startAs('USER');
  mockServer({ [FIRST]: ok({ items: [], nextCursor: null }) });

  // when
  await renderApp('/feed');

  // then
  expect(screen.getByText('아직 올라온 사진이 없어요')).toBeOnTheScreen();
});
