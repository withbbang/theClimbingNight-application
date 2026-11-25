import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
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
  const answer = globalThis.fetch;
  globalThis.fetch = jest.fn((url: string, init: RequestInit) =>
    url.endsWith('/bulk-approve')
      ? new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
        })
      : answer(url, init),
  ) as unknown as typeof fetch;
  await press('일괄 승인');
  await press('일괄 승인 확정');
  await act(async () => {
    await jest.advanceTimersByTimeAsync(15_000);
  });
  await flush();
  expect(
    screen.getByText('처리 중일 수 있어요. 잠시 뒤 목록을 새로고침해 주세요.'),
  ).toBeOnTheScreen();
  expect(server.count(LIST)).toBe(2);
});

it('says the network is down when bulk approval cannot be sent', async () => {
  mockServer({ [LIST]: list([request()]) });
  await renderApp('/admin/requests');
  await press('일괄 승인');
  await press('일괄 승인 확정');
  expect(screen.getByText('네트워크 연결을 확인한 뒤 다시 시도해 주세요.')).toBeOnTheScreen();
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

it('keeps showing the picked roster entry after the search changes', async () => {
  const found = { ...candidate(9, '옛 닉네임'), somoimNickname: '달', userId: null };
  const SEARCH = (q: string) =>
    `/roster-members?q=${encodeURIComponent(q)}&linked=false&page=0&size=20`;
  const server = mockServer({
    [LIST]: list([request()]),
    [SEARCH('달')]: list([found]),
    [SEARCH('없음')]: list([]),
    [APPROVE]: ok({ id: 1 }),
  });
  await renderApp('/admin/requests');
  await press('앱별이 다른 명부 찾기');
  await type('명부 검색', '달');
  await act(async () => {
    await jest.advanceTimersByTimeAsync(300);
  });
  await flush();
  await fireEvent.press(choice('앱별이 명부: 달, 옛 닉네임'));
  expect(choice('앱별이 명부: 달, 옛 닉네임')).toBeChecked();
  await type('명부 검색', '없음');
  await act(async () => {
    await jest.advanceTimersByTimeAsync(300);
  });
  await flush();
  expect(choice('앱별이 명부: 달, 옛 닉네임')).toBeChecked();
  await press('앱별이 승인');
  expect(body(server)).toEqual({ rosterMemberId: 9 });
});

it('does not preselect an entry claimed by two requests', async () => {
  mockServer({ [LIST]: list([request({ bulkApprovable: false })]) });
  await renderApp('/admin/requests');
  expect(choice('앱별이 명부: 별이')).not.toBeChecked();
  expect(button('앱별이 승인')).toBeDisabled();
  expect(
    screen.getByText('다른 신청도 이 명부를 기다려요. 누구인지 확인해 주세요.'),
  ).toBeOnTheScreen();
});

it('keeps the first request in flight when another is pressed before a re-render', async () => {
  let finish = (_: Reply) => {};
  const server = mockServer({
    [LIST]: list([request(), request({ id: 2, user: { id: 22, nickname: '달이', role: 'USER' } })]),
    [APPROVE]: () => new Promise<Reply>((resolve) => (finish = resolve)),
  });
  await renderApp('/admin/requests');
  const first = button('앱별이 승인')!.props.onClick;
  const second = button('달이 승인')!.props.onClick;
  await act(async () => {
    first({ stopPropagation() {} });
    second({ stopPropagation() {} });
  });
  await act(async () => finish(fail(409, 'ROSTER_ALREADY_LINKED', '이미 연결된 명부입니다.')));
  await flush();
  expect(server.calls.filter((c) => c.method === 'POST')).toHaveLength(1);
  expect(screen.getByText('이미 연결된 명부입니다.')).toBeOnTheScreen();
});

it('says so when every match is already on the card', async () => {
  mockServer({
    [LIST]: list([request()]),
    [`/roster-members?q=${encodeURIComponent('별')}&linked=false&page=0&size=20`]: list([
      { ...candidate(3), userId: null },
    ]),
  });
  await renderApp('/admin/requests');
  await press('앱별이 다른 명부 찾기');
  await type('명부 검색', '별');
  await act(async () => {
    await jest.advanceTimersByTimeAsync(300);
  });
  await flush();
  expect(screen.getByText('다른 명부가 없어요')).toBeOnTheScreen();
});

it('keeps the choice and the reason when the card is mounted again', async () => {
  const items = [request()];
  let shown = true;
  mockServer({ [LIST]: () => list(shown ? items : []) });
  await renderApp('/admin/requests');
  await fireEvent.press(choice('앱별이 새 명부 항목'));
  await press('앱별이 거절');
  await type('거절 사유', '확인 중');
  for (const next of [false, true]) {
    shown = next;
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ['membership-requests'] });
    });
    await flush();
  }
  expect(choice('앱별이 새 명부 항목')).toBeChecked();
  await press('앱별이 거절');
  expect(screen.getByLabelText('거절 사유').props.value).toBe('확인 중');
});

it('asks again when the candidates change under an untouched card', async () => {
  // given
  let linked = false;
  const server = mockServer({
    [LIST]: () =>
      list([request({ candidates: linked ? [] : [candidate(3)], bulkApprovable: false })]),
    [APPROVE]: ok({ id: 1 }),
  });
  await renderApp('/admin/requests');
  // when
  linked = true;
  await act(async () => {
    await queryClient.invalidateQueries({ queryKey: ['membership-requests'] });
  });
  await flush();
  // then
  expect(choice('앱별이 새 명부 항목')).not.toBeChecked();
  expect(button('앱별이 승인')).toBeDisabled();
  await fireEvent.press(choice('앱별이 새 명부 항목'));
  await press('앱별이 승인');
  expect(body(server)).toEqual({ createNew: true });
});
