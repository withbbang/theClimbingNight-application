import { screen } from 'expo-router/testing-library';

import { fail, mockServer, ok, renderApp } from '@/test-utils/app';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { PenaltyResponse } from '@/types/api/penalty';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/penalties?page=0&size=20';
const ROSTER = '/roster-members/101/penalties';
const penalty = (over: Partial<PenaltyResponse> = {}): PenaltyResponse => ({
  id: 7,
  meetupId: 1,
  meetupTitle: '화요 정기벙',
  meetupStartsAt: '2026-11-17T10:30:00Z',
  type: 'NO_SHOW',
  active: true,
  revokeReason: null,
  ...over,
});
const roster = (penalties: PenaltyResponse[]) =>
  ok({
    rosterMemberId: 101,
    somoimNickname: '별이',
    totalCount: penalties.filter((p) => p.active).length,
    recentCount: 1,
    penalties,
  });
const summaries = (items: unknown[]) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });

beforeEach(() => startAs('ADMIN'));

it('lists people in the server order and opens the records', async () => {
  mockServer({
    [LIST]: summaries([
      { rosterMemberId: 102, somoimNickname: '달이', totalCount: 2, recentCount: 2 },
      { rosterMemberId: 101, somoimNickname: '별이', totalCount: 3, recentCount: 1 },
    ]),
    [ROSTER]: roster([penalty()]),
  });
  const app = await renderApp('/admin/penalties');
  expect(screen.getAllByText(/^(달이|별이)$/).map((t) => t.props.children)).toEqual([
    '달이',
    '별이',
  ]);
  await press('별이, 누적 3회, 최근 3개월 1회');
  expect(app.getPathname()).toBe('/admin/penalties/101');
  expect(screen.getByText('노쇼')).toBeOnTheScreen();
});

it('shows an empty notice', async () => {
  mockServer({ [LIST]: summaries([]) });
  await renderApp('/admin/penalties');
  expect(screen.getByText('기록이 있는 사람이 없어요')).toBeOnTheScreen();
});

it('shows active and revoked records, and offers no revoke on a revoked one', async () => {
  mockServer({
    [ROSTER]: roster([
      penalty(),
      penalty({ id: 8, type: 'LATE_CANCEL', active: false, revokeReason: '착오' }),
    ]),
  });
  await renderApp('/admin/penalties/101');
  expect(screen.getByText('누적 1회, 최근 3개월 1회')).toBeOnTheScreen();
  expect(screen.getByText('취소됨: 착오')).toBeOnTheScreen();
  expect(screen.getAllByRole('button', { name: '기록 취소' })).toHaveLength(1);
});

it('revokes with a trimmed reason after confirming', async () => {
  let revoked = false;
  const server = mockServer({
    [ROSTER]: () => roster([penalty(revoked ? { active: false, revokeReason: '착오' } : {})]),
    'POST /penalties/7/revoke': () => {
      revoked = true;
      return ok(penalty({ active: false, revokeReason: '착오' }));
    },
  });
  await renderApp('/admin/penalties/101');
  await press('기록 취소');
  expect(screen.getByLabelText('취소 사유').props.maxLength).toBe(200);
  expect(button('기록 취소 확정')).toBeDisabled();
  await type('취소 사유', ' ');
  expect(button('기록 취소 확정')).toBeDisabled();
  await type('취소 사유', ' 착오 ');
  await press('기록 취소 확정');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({ reason: '착오' });
  expect(screen.getByText('취소됨: 착오')).toBeOnTheScreen();
  expect(button('기록 취소')).toBeNull();
});

it('keeps the reason when it fails', async () => {
  mockServer({
    [ROSTER]: roster([penalty()]),
    'POST /penalties/7/revoke': fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류'),
  });
  await renderApp('/admin/penalties/101');
  await press('기록 취소');
  await type('취소 사유', '착오');
  await press('기록 취소 확정');
  expect(screen.getByText('서버 오류')).toBeOnTheScreen();
  expect(screen.getByLabelText('취소 사유').props.value).toBe('착오');
});

it('keeps the server message when another admin revoked first', async () => {
  let revoked = false;
  mockServer({
    [ROSTER]: () => roster([penalty(revoked ? { active: false, revokeReason: '먼저' } : {})]),
    'POST /penalties/7/revoke': () => {
      revoked = true;
      return fail(409, 'PENALTY_ALREADY_REVOKED', '이미 취소된 블랙리스트 기록입니다.');
    },
  });
  await renderApp('/admin/penalties/101');
  await press('기록 취소');
  await type('취소 사유', '착오');
  await press('기록 취소 확정');
  expect(screen.getByText('취소됨: 먼저')).toBeOnTheScreen();
  expect(screen.getByText('이미 취소된 블랙리스트 기록입니다.')).toBeOnTheScreen();
});

it('shows not found for a bad id without a request', async () => {
  const server = mockServer({});
  await renderApp('/admin/penalties/abc');
  expect(screen.getByText('명부에서 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/roster'))).toBe(false);
});

it('shows not found on 404', async () => {
  mockServer({ [ROSTER]: fail(404, 'COMMON_NOT_FOUND', '없음') });
  await renderApp('/admin/penalties/101');
  expect(screen.getByText('명부에서 찾을 수 없어요')).toBeOnTheScreen();
});
