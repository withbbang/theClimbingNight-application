import { act, fireEvent, screen } from 'expo-router/testing-library';

import { clearSessionCache } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { post } from '@/test-utils/posts';
import { startAs } from '@/test-utils/session';
import { tokens } from '@/test-utils/tokens';

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

it('refetches the feed when the session changes', async () => {
  // given: 비로그인에게는 3개만, 로그인하면 커서까지 준다.
  await startAs(null);
  const signedIn = () => sessionStore.getState().status === 'authenticated';
  const server = mockServer({
    [FIRST]: () => ok({ items: three, nextCursor: signedIn() ? 'c1' : null }),
    [NEXT]: ok({ items: [post({ id: 4, body: '4번째 글' })], nextCursor: null }),
  });
  await renderApp('/feed');
  expect(screen.getByText(LOGIN_CARD)).toBeOnTheScreen();

  // when: 로그인 화면이 하는 대로 세션을 바꾸고 캐시를 비운다.
  await act(async () => {
    await sessionStore.signIn(tokens('2'));
    await clearSessionCache();
  });
  await flush();
  await reachEnd();

  // then
  expect(server.count(FIRST)).toBe(2);
  expect(screen.getByText('4번째 글')).toBeOnTheScreen();
  expect(screen.queryByText(LOGIN_CARD)).toBeNull();

  // when: 로그아웃하면
  await act(async () => {
    await sessionStore.signOut();
    await clearSessionCache();
  });
  await flush();

  // then: 로그인 상태에서 받은 글이 남지 않는다.
  expect(server.count(FIRST)).toBe(3);
  expect(screen.queryByText('4번째 글')).toBeNull();
  expect(screen.getByText(LOGIN_CARD)).toBeOnTheScreen();
});

describe('my posts in progress', () => {
  const MINE = '/posts/mine/in-progress';

  it('shows the + button only when signed in', async () => {
    // given
    await startAs(null);
    const server = mockServer({ [FIRST]: ok({ items: three, nextCursor: null }) });

    // when
    await renderApp('/feed');

    // then: 비로그인은 올리기 버튼이 없고 내 글을 묻지 않는다.
    expect(screen.queryByRole('button', { name: '사진 올리기' })).toBeNull();
    expect(server.count(MINE)).toBe(0);
  });

  it('shows the + button and my failed post with a delete button', async () => {
    // given
    await startAs('USER');
    const server = mockServer({
      [FIRST]: ok({ items: three, nextCursor: null }),
      [MINE]: ok([post({ id: 9, status: 'FAILED', mine: true, publishedAt: null })]),
      'DELETE /posts/9': { status: 204 },
    });
    await renderApp('/feed');
    expect(screen.getByRole('button', { name: '사진 올리기' })).toBeOnTheScreen();
    expect(screen.getByText('올리지 못했어요')).toBeOnTheScreen();

    // when
    await fireEvent.press(screen.getByRole('button', { name: '올리지 못한 글 삭제' }));
    await flush();
    await fireEvent.press(screen.getByRole('button', { name: '올리지 못한 글 삭제 확정' }));
    await flush();

    // then
    expect(server.count('DELETE /posts/9')).toBe(1);
    // 204로 끝나도 성공이라 확인 칸이 닫힌다. (다시 눌러 한 번 더 지우지 않는다)
    expect(screen.queryByRole('button', { name: '올리지 못한 글 삭제 확정' })).toBeNull();
  });

  it('polls every 10 seconds while a post is pending and refreshes the feed when done', async () => {
    // given: 처음 두 번은 정리 중이고, 세 번째에 끝난다.
    await startAs('USER');
    let polls = 0;
    const server = mockServer({
      [FIRST]: ok({ items: three, nextCursor: null }),
      [MINE]: () => {
        polls += 1;
        return ok(polls < 3 ? [post({ id: 9, status: 'PENDING', mine: true })] : []);
      },
    });
    await renderApp('/feed');
    expect(screen.getByText('사진 정리 중…')).toBeOnTheScreen();
    const feedLoads = server.count(FIRST);

    // when
    await act(async () => {
      await jest.advanceTimersByTimeAsync(10_000);
    });
    await flush();

    // then
    expect(server.count(MINE)).toBe(2);
    expect(server.count(FIRST)).toBe(feedLoads);

    // when: 정리가 끝나면
    await act(async () => {
      await jest.advanceTimersByTimeAsync(10_000);
    });
    await flush();

    // then: 안내가 사라지고 피드를 다시 받으며 더 묻지 않는다.
    expect(screen.queryByText('사진 정리 중…')).toBeNull();
    expect(server.count(FIRST)).toBe(feedLoads + 1);
    await act(async () => {
      await jest.advanceTimersByTimeAsync(30_000);
    });
    expect(server.count(MINE)).toBe(3);
  });
});
