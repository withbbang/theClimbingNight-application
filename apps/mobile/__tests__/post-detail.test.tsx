import { screen } from 'expo-router/testing-library';

import { fail, mockServer, ok, renderApp } from '@/test-utils/app';
import { comment } from '@/test-utils/comments';
import { media, post } from '@/test-utils/posts';
import { press, type } from '@/test-utils/screen';
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

it('blocks the author and goes back from the detail', async () => {
  // given: 피드에서 상세로 들어왔다
  await startAs('USER');
  const server = mockServer({
    '/posts?size=10': ok({ items: [post({ id: 5 })], nextCursor: null }),
    '/posts/mine/in-progress': ok([]),
    '/posts/5': ok(post({ id: 5 })),
    'POST /blocks': {
      status: 201,
      body: {
        success: true,
        data: { userId: 7, nickname: '달이', blockedAt: '2026-11-24T03:00:00Z' },
      },
    },
  });
  const app = await renderApp('/feed');
  await press('달이의 게시물 열기');
  expect(app.getPathname()).toBe('/posts/5');

  // when
  await press('달이의 게시물 메뉴');
  await press('달이님 차단');
  await press('차단 확정');

  // then
  expect(server.calls.find((c) => c.path === '/blocks')?.body).toEqual({ userId: 7 });
  expect(app.getPathname()).toBe('/feed');
});

describe('comments', () => {
  const comments = (items: unknown[], nextCursor: string | null = null) =>
    ok({ items, nextCursor });

  it('lists comments oldest first under the post', async () => {
    // given
    await startAs('USER');
    mockServer({
      '/posts/8': ok(post({ id: 8 })),
      '/posts/8/comments?size=20': comments([
        comment({ id: 1, body: '먼저 단 댓글' }),
        comment({ id: 2, body: '나중에 단 댓글', author: { id: 9, nickname: '구름' } }),
      ]),
    });

    // when
    await renderApp('/posts/8');

    // then
    expect(screen.getByText('오늘 초록 문제 완등')).toBeOnTheScreen();
    const order = screen
      .getAllByText(/먼저 단 댓글|나중에 단 댓글/)
      .map((node) => node.props.children);
    expect(order).toEqual(['먼저 단 댓글', '나중에 단 댓글']);
  });

  it('expands replies on demand', async () => {
    // given
    await startAs('USER');
    const server = mockServer({
      '/posts/8': ok(post({ id: 8 })),
      '/posts/8/comments?size=20': comments([comment({ id: 1, replyCount: 1 })]),
      '/posts/8/comments?size=20&parentId=1': comments([
        comment({ id: 3, parentId: 1, body: '답글이에요', replyToNickname: '바위' }),
      ]),
    });
    await renderApp('/posts/8');
    expect(screen.queryByText(/답글이에요/)).not.toBeOnTheScreen();

    // when
    await press('답글 1개 보기');

    // then
    expect(screen.getByText(/답글이에요/)).toBeOnTheScreen();
    expect(screen.getByText('@바위')).toBeOnTheScreen();
    expect(server.count('/posts/8/comments?size=20&parentId=1')).toBe(1);
  });

  it('lists comments on my hidden post', async () => {
    // given
    await startAs('USER');
    const server = mockServer({
      '/posts/8': ok(post({ id: 8, mine: true, status: 'HIDDEN' })),
      '/posts/8/comments?size=20': comments([comment({ id: 1, body: '남은 댓글' })]),
    });

    // when
    await renderApp('/posts/8');

    // then
    expect(screen.getByText('남은 댓글')).toBeOnTheScreen();
    expect(server.count('/posts/8/comments?size=20')).toBe(1);
  });

  it('asks signed-out users to log in instead of loading comments', async () => {
    // given: 최근 글은 비로그인도 본다
    await startAs(null);
    const server = mockServer({ '/posts/8': ok(post({ id: 8 })) });

    // when
    await renderApp('/posts/8');

    // then
    expect(screen.getByText('로그인하고 댓글을 볼 수 있어요')).toBeOnTheScreen();
    expect(server.calls.some((c) => c.path.includes('/comments'))).toBe(false);
  });

  it('appends a new reply under its parent', async () => {
    // given: 답글 하나가 달린 댓글을 펼쳐 둔다
    await startAs('USER');
    const server = mockServer({
      '/posts/8': ok(post({ id: 8 })),
      '/posts/8/comments?size=20': comments([comment({ id: 1, replyCount: 1 })]),
      '/posts/8/comments?size=20&parentId=1': comments([
        comment({
          id: 3,
          parentId: 1,
          body: '먼저 온 답글',
          replyToNickname: '바위',
          author: { id: 9, nickname: '구름' },
        }),
      ]),
      'POST /posts/8/comments': {
        status: 201,
        body: {
          success: true,
          data: comment({
            id: 9,
            parentId: 1,
            body: '새 답글이에요',
            replyToNickname: '바위',
            mine: true,
            deletable: true,
            author: { id: 2, nickname: '나' },
          }),
        },
      },
    });
    await renderApp('/posts/8');
    await press('답글 1개 보기');

    // when: 부모 댓글에 답글을 단다
    await press('바위의 댓글에 답글');
    expect(screen.getByText('바위님에게 답글')).toBeOnTheScreen();
    await type('댓글 입력', ' 새 답글이에요 ');
    await press('게시');

    // then
    expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
      body: '새 답글이에요',
      replyToCommentId: 1,
    });
    const replies = screen.getAllByText(/먼저 온 답글|새 답글이에요/);
    expect(replies).toHaveLength(2);
    expect(screen.getByText(/먼저 온 답글/)).toBe(replies[0]);
    expect(screen.getByText(/새 답글이에요/)).toBe(replies[1]);
    expect(screen.queryByText('바위님에게 답글')).not.toBeOnTheScreen();
    expect(screen.getByLabelText('댓글 입력').props.value).toBe('');
  });

  it('expands a collapsed parent after replying to it', async () => {
    // given: 답글이 아직 없는 댓글
    await startAs('USER');
    mockServer({
      '/posts/8': ok(post({ id: 8 })),
      '/posts/8/comments?size=20': comments([comment({ id: 1, replyCount: 0 })]),
      '/posts/8/comments?size=20&parentId=1': comments([
        comment({ id: 9, parentId: 1, body: '첫 답글이에요', replyToNickname: '바위' }),
      ]),
      'POST /posts/8/comments': {
        status: 201,
        body: {
          success: true,
          data: comment({ id: 9, parentId: 1, body: '첫 답글이에요', replyToNickname: '바위' }),
        },
      },
    });
    await renderApp('/posts/8');

    // when
    await press('바위의 댓글에 답글');
    await type('댓글 입력', '첫 답글이에요');
    await press('게시');

    // then
    expect(screen.getByText(/첫 답글이에요/)).toBeOnTheScreen();
  });

  it('hides the input on a hidden post', async () => {
    // given
    await startAs('USER');
    mockServer({
      '/posts/8': ok(post({ id: 8, mine: true, status: 'HIDDEN' })),
      '/posts/8/comments?size=20': comments([]),
    });

    // when
    await renderApp('/posts/8');

    // then
    expect(screen.queryByLabelText('댓글 입력')).not.toBeOnTheScreen();
  });

  it('shows the input on a visible post', async () => {
    // given
    await startAs('USER');
    mockServer({
      '/posts/8': ok(post({ id: 8 })),
      '/posts/8/comments?size=20': comments([]),
    });

    // when
    await renderApp('/posts/8');

    // then
    expect(screen.getByLabelText('댓글 입력')).toBeOnTheScreen();
  });
});
