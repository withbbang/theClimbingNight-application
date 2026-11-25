import { act, fireEvent, screen } from 'expo-router/testing-library';

import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { MembershipReviewItemResponse } from '@/types/api/membership';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/membership-requests?status=PENDING&page=0&size=20';
const APPROVE = 'POST /membership-requests/1/approve';
const REJECT = 'POST /membership-requests/1/reject';
const BULK = 'POST /membership-requests/bulk-approve';
const candidate = (id: number, note: string | null = null) => ({
  id,
  somoimNickname: '별이',
  note,
  source: 'IMPORT' as const,
});
const request = (
  over: Partial<MembershipReviewItemResponse> = {},
): MembershipReviewItemResponse => ({
  id: 1,
  user: { id: 21, nickname: '앱별이', role: 'USER' },
  somoimNickname: '별이',
  status: 'PENDING',
  rejectReason: null,
  createdAt: '2026-11-24T02:00:00Z',
  candidates: [candidate(3)],
  bulkApprovable: true,
  ...over,
});
const list = (items: unknown[], totalPages = 1) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages });
const choice = (name: string) => screen.getByRole('radio', { name });
const body = (server: ReturnType<typeof mockServer>) =>
  server.calls.find((c) => c.method === 'POST')?.body;

beforeEach(() => startAs('ADMIN'));

it('shows the nicknames and approves with the single candidate', async () => {
  let done = false;
  const server = mockServer({
    [LIST]: () => list(done ? [] : [request()]),
    [APPROVE]: () => {
      done = true;
      return ok({ id: 1 });
    },
  });
  await renderApp('/admin/requests');
  expect(screen.getByText('앱별이')).toBeOnTheScreen();
  expect(screen.getByText('소모임 닉네임: 별이')).toBeOnTheScreen();
  await press('앱별이 승인');
  expect(body(server)).toEqual({ rosterMemberId: 3 });
  expect(screen.getByText('기다리는 신청이 없어요')).toBeOnTheScreen();
});

it('requires choosing when there are several candidates', async () => {
  const server = mockServer({
    [LIST]: list([request({ candidates: [candidate(3, '1기'), candidate(4, '2기')] })]),
    [APPROVE]: ok({ id: 1 }),
  });
  await renderApp('/admin/requests');
  expect(button('앱별이 승인')).toBeDisabled();
  await fireEvent.press(choice('앱별이 명부: 별이, 2기'));
  await press('앱별이 승인');
  expect(body(server)).toEqual({ rosterMemberId: 4 });
});

it('makes a new roster entry without candidates', async () => {
  const server = mockServer({
    [LIST]: list([request({ candidates: [], bulkApprovable: false })]),
    [APPROVE]: ok({ id: 1 }),
  });
  await renderApp('/admin/requests');
  await press('앱별이 승인');
  expect(body(server)).toEqual({ createNew: true });
});

it('approves with another roster entry found by search', async () => {
  const SEARCH = `/roster-members?q=${encodeURIComponent('별 이&')}&linked=false&page=0&size=20`;
  const server = mockServer({
    [LIST]: list([request()]),
    [SEARCH]: list([{ ...candidate(9, '옛 닉네임'), somoimNickname: '별 이&', userId: null }]),
    [APPROVE]: ok({ id: 1 }),
  });
  await renderApp('/admin/requests');
  await press('앱별이 다른 명부 찾기');
  await type('명부 검색', ' ');
  await type('명부 검색', '별');
  await type('명부 검색', ' 별 이& ');
  expect(server.calls.some((c) => c.path.startsWith('/roster'))).toBe(false);
  await act(async () => {
    await jest.advanceTimersByTimeAsync(300);
  });
  await flush();
  expect(server.calls.filter((c) => c.path.startsWith('/roster')).map((c) => c.path)).toEqual([
    SEARCH,
  ]);
  await fireEvent.press(choice('앱별이 명부: 별 이&, 옛 닉네임'));
  await press('앱별이 승인');
  expect(body(server)).toEqual({ rosterMemberId: 9 });
});

it('rejects with a trimmed reason, not a blank one', async () => {
  const server = mockServer({ [LIST]: list([request()]), [REJECT]: ok({ id: 1 }) });
  await renderApp('/admin/requests');
  await press('앱별이 거절');
  expect(screen.getByLabelText('거절 사유').props.maxLength).toBe(200);
  await type('거절 사유', ' ');
  expect(button('앱별이 거절 확정')).toBeDisabled();
  await type('거절 사유', ' 명부에 없어요 ');
  await press('앱별이 거절 확정');
  expect(body(server)).toEqual({ reason: '명부에 없어요' });
});

it('bulk-approves after confirming and shows the result', async () => {
  const server = mockServer({
    [LIST]: list([request()]),
    [BULK]: ok({ approvedCount: 12, skippedCount: 1 }),
  });
  await renderApp('/admin/requests');
  await press('일괄 승인');
  expect(server.count(BULK)).toBe(0);
  await press('일괄 승인 확정');
  expect(screen.getByText('12건 승인, 1건 건너뜀')).toBeOnTheScreen();
});

it('says it may still be running when bulk approval times out', async () => {
  const server = mockServer({ [LIST]: list([request()]) });
  await renderApp('/admin/requests');
  await press('일괄 승인');
  await press('일괄 승인 확정');
  expect(
    screen.getByText('처리 중일 수 있어요. 잠시 뒤 목록을 새로고침해 주세요.'),
  ).toBeOnTheScreen();
  expect(server.count(LIST)).toBe(2);
});

it.each([
  [1, true],
  [2, false],
])('bulk approval without approvable ones on %s page(s): disabled %s', async (pages, disabled) => {
  mockServer({ [LIST]: list([request({ bulkApprovable: false })], pages) });
  await renderApp('/admin/requests');
  if (disabled) {
    expect(button('일괄 승인')).toBeDisabled();
  } else {
    expect(button('일괄 승인')).toBeEnabled();
  }
});

it('locks the other cards while sending', async () => {
  let finish = (_: Reply) => {};
  const server = mockServer({
    [LIST]: list([request(), request({ id: 2, user: { id: 22, nickname: '달이', role: 'USER' } })]),
    [APPROVE]: () => new Promise<Reply>((resolve) => (finish = resolve)),
  });
  await renderApp('/admin/requests');
  await press('앱별이 승인');
  expect(button('달이 승인')).toBeDisabled();
  expect(button('일괄 승인')).toBeDisabled();
  await act(async () => finish(ok({ id: 1 })));
  await flush();
  expect(server.count(APPROVE)).toBe(1);
  expect(button('달이 승인')).toBeEnabled();
});

it('shows the server message and drops a request handled elsewhere', async () => {
  let done = false;
  mockServer({
    [LIST]: () => list(done ? [] : [request()]),
    [APPROVE]: () => {
      done = true;
      return fail(409, 'MEMBERSHIP_REQUEST_ALREADY_REVIEWED', '이미 처리된 신청입니다.');
    },
  });
  await renderApp('/admin/requests');
  await press('앱별이 승인');
  expect(screen.getByText('이미 처리된 신청입니다.')).toBeOnTheScreen();
  expect(screen.queryByText('앱별이')).toBeNull();
});
