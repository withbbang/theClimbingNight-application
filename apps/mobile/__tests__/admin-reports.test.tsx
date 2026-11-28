import { screen } from 'expo-router/testing-library';

import { mockServer, ok, renderApp } from '@/test-utils/app';
import { media, post } from '@/test-utils/posts';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { ReportTargetResponse } from '@/types/api/moderation';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/report-targets?page=0&size=20';
const target = (over: Partial<ReportTargetResponse> = {}): ReportTargetResponse => ({
  targetType: 'POST',
  targetId: 5,
  targetStatus: 'VISIBLE',
  reportCount: 3,
  reasons: { SPAM: 2, OTHER: 1 },
  details: ['같은 글을 계속 올려요'],
  lastReportedAt: '2026-11-24T02:00:00Z',
  post: post({ id: 5, media: [media(51)] }),
  quarantineUrls: [],
  comment: null,
  userNickname: null,
  userProfileImageUrl: null,
  userBio: null,
  cleanupFailed: false,
  ...over,
});
const list = (items: unknown[]) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });

beforeEach(() => startAs('ADMIN'));

it('removes the row after 기각', async () => {
  // given
  let done = false;
  const server = mockServer({
    [LIST]: () => list(done ? [] : [target()]),
    'POST /report-targets/POST/5/actions': () => {
      done = true;
      return { status: 204 };
    },
  });
  await renderApp('/admin/reports');
  expect(screen.getByText('신고 3건')).toBeOnTheScreen();
  expect(screen.getByText('스팸·광고 2, 기타 1')).toBeOnTheScreen();

  // when
  await press('달이의 게시물 기각');
  await press('달이의 게시물 기각 확정');

  // then
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({ action: 'DISMISS' });
  expect(screen.queryByText('신고 3건')).toBeNull();
});

it('warns when the photos could not be cleaned up', async () => {
  // given
  mockServer({
    [LIST]: list([
      target({
        targetStatus: 'HIDDEN_PENDING',
        cleanupFailed: true,
        quarantineUrls: ['https://q.test/1'],
      }),
    ]),
  });

  // when
  await renderApp('/admin/reports');

  // then
  expect(
    screen.getByText('사진 정리에 실패했어요. 공개 사본이 남아 있을 수 있어요.'),
  ).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '달이의 게시물 복원' })).toBeOnTheScreen();
});

it('offers only 기각 for a reported user', async () => {
  // given
  mockServer({
    [LIST]: list([
      target({
        targetType: 'USER',
        targetId: 7,
        targetStatus: 'ACTIVE',
        post: null,
        userNickname: '달이',
      }),
    ]),
  });

  // when
  await renderApp('/admin/reports');

  // then
  expect(screen.getByRole('button', { name: '달이 사용자 기각' })).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: /숨기기|복원/ })).toBeNull();
});

it.each([
  ['FAILED', '처리 실패'],
  ['DRAFT', '게시 전'],
])('offers only 기각 for a %s post that cannot be hidden or restored', async (status, label) => {
  // given: 서버가 숨김·복원을 404로 거절하는 상태
  mockServer({ [LIST]: list([target({ targetStatus: status })]) });

  // when
  await renderApp('/admin/reports');

  // then
  expect(screen.getByText(label)).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '달이의 게시물 기각' })).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: /숨기기|복원/ })).toBeNull();
});

const commentTarget = (status: string, withComment = true) =>
  target({
    targetType: 'COMMENT',
    targetId: 9,
    targetStatus: status,
    post: withComment ? post({ id: 5, media: [media(51)] }) : null,
    comment: withComment
      ? { id: 9, postId: 5, author: { id: 3, nickname: '별이', profileImageUrl: null }, body: '신고된 댓글 내용' }
      : null,
  });

it('shows comment body and hide/dismiss for a visible comment', async () => {
  // given
  mockServer({ [LIST]: list([commentTarget('VISIBLE')]) });

  // when
  await renderApp('/admin/reports');

  // then
  expect(screen.getByText('신고된 댓글 내용')).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '별이의 댓글 숨기기' })).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '별이의 댓글 기각' })).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: /복원/ })).toBeNull();
});

it('offers restore for an auto-hidden comment', async () => {
  // given
  mockServer({ [LIST]: list([commentTarget('HIDDEN_PENDING')]) });

  // when
  await renderApp('/admin/reports');

  // then
  expect(screen.getByRole('button', { name: '별이의 댓글 숨기기' })).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '별이의 댓글 복원' })).toBeOnTheScreen();
  expect(screen.getByRole('button', { name: '별이의 댓글 기각' })).toBeOnTheScreen();
});

it('offers only dismiss for a deleted comment', async () => {
  // given
  mockServer({ [LIST]: list([commentTarget('DELETED', false)]) });

  // when
  await renderApp('/admin/reports');

  // then
  expect(screen.getByRole('button', { name: '9번 댓글 기각' })).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: /숨기기|복원/ })).toBeNull();
});

const userTarget = (over: Partial<ReportTargetResponse> = {}) =>
  target({
    targetType: 'USER',
    targetId: 7,
    targetStatus: 'ACTIVE',
    post: null,
    userNickname: '달이',
    ...over,
  });
const ACT = 'POST /report-targets/USER/7/actions';

it('offers only 기각 for a user without photo or bio', async () => {
  mockServer({ [LIST]: list([userTarget()]) });
  await renderApp('/admin/reports');
  expect(screen.queryByRole('button', { name: /내리기|지우기/ })).toBeNull();
});

it('shows bio and 자기소개 지우기 only when the user has a bio', async () => {
  const server = mockServer({
    [LIST]: list([userTarget({ userBio: '벽 타는 밤이 좋아요' })]),
    [ACT]: { status: 204 },
  });
  await renderApp('/admin/reports');
  expect(screen.getByText('벽 타는 밤이 좋아요')).toBeOnTheScreen();
  expect(screen.queryByRole('button', { name: /프로필 사진 내리기|둘 다/ })).toBeNull();

  await press('달이 사용자 자기소개 지우기');
  await press('달이 사용자 자기소개 지우기 확정');

  expect(server.calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([
    { action: 'CLEAR_BIO' },
  ]);
});

it('offers 프로필 사진 내리기 only when the user has a photo', async () => {
  const server = mockServer({
    [LIST]: list([userTarget({ userProfileImageUrl: 'https://r2.test/p.jpg' })]),
    [ACT]: { status: 204 },
  });
  await renderApp('/admin/reports');
  expect(screen.queryByRole('button', { name: /자기소개 지우기|둘 다/ })).toBeNull();

  await press('달이 사용자 프로필 사진 내리기');
  await press('달이 사용자 프로필 사진 내리기 확정');

  expect(server.calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([
    { action: 'REMOVE_PROFILE_IMAGE' },
  ]);
});

const both = () =>
  userTarget({ userProfileImageUrl: 'https://r2.test/p.jpg', userBio: '벽 타는 밤이 좋아요' });

it('sends both actions in order for 둘 다 내리기', async () => {
  const server = mockServer({ [LIST]: list([both()]), [ACT]: { status: 204 } });
  await renderApp('/admin/reports');

  await press('달이 사용자 둘 다 내리기');
  await press('달이 사용자 둘 다 내리기 확정');

  expect(server.calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([
    { action: 'REMOVE_PROFILE_IMAGE' },
    { action: 'CLEAR_BIO' },
  ]);
  expect(screen.getByRole('button', { name: '달이 사용자 기각' })).toBeOnTheScreen();
});

it('does not send CLEAR_BIO when the first request fails', async () => {
  const server = mockServer({ [LIST]: list([both()]), [ACT]: { status: 500 } });
  await renderApp('/admin/reports');

  await press('달이 사용자 둘 다 내리기');
  await press('달이 사용자 둘 다 내리기 확정');

  expect(server.calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([
    { action: 'REMOVE_PROFILE_IMAGE' },
  ]);
});
