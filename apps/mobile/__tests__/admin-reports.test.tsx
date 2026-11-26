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
  userNickname: null,
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
