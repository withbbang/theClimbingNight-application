import { act, fireEvent, screen } from 'expo-router/testing-library';

import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { detail } from '@/test-utils/meetups';
import { button, press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { MeetupApplicationAdminResponse } from '@/types/api/meetup';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const PATH = '/admin/meetups/1/applications';
const LIST = '/meetups/1/applications';
const ENDED = { startsAt: '2026-11-23T10:00:00Z', endsAt: '2026-11-23T12:00:00Z' };

const row = (
  over: Partial<MeetupApplicationAdminResponse> = {},
): MeetupApplicationAdminResponse => ({
  id: 11,
  rosterMemberId: 101,
  somoimNickname: '별이',
  userId: 21,
  status: 'CONFIRMED',
  appliedAt: '2026-11-20T03:00:00Z',
  confirmedAt: '2026-11-20T03:00:00Z',
  canceledAt: null,
  lateCancel: false,
  cancelReason: null,
  attendanceId: null,
  attendanceResult: null,
  lateCancelPenalized: false,
  penaltyTotal: 0,
  penaltyRecent: 0,
  ...over,
});
const late = (over: Partial<MeetupApplicationAdminResponse> = {}) =>
  row({
    status: 'CANCELED',
    canceledAt: '2026-11-23T03:00:00Z',
    lateCancel: true,
    cancelReason: '부상',
    ...over,
  });
const recorded = (over: Partial<MeetupApplicationAdminResponse> = {}) =>
  row({ attendanceId: 51, attendanceResult: 'ATTENDED', ...over });
const tab = (name: string) => screen.getByRole('radio', { name });

beforeEach(() => startAs('ADMIN'));

it('groups applicants in the server order', async () => {
  mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: ok([
      row({ id: 1, somoimNickname: '나' }),
      row({ id: 2, somoimNickname: '가' }),
      row({ id: 3, somoimNickname: '다', status: 'WAITLISTED', confirmedAt: null }),
      row({ id: 4, somoimNickname: '라', status: 'REMOVED', canceledAt: '2026-11-21T03:00:00Z' }),
    ]),
  });
  await renderApp(PATH);
  const texts = screen.getAllByText(/^(확정 2|대기 1|취소·내보냄 1|[가나다라])$/);
  expect(texts.map((t) => t.props.children)).toEqual([
    '확정 2',
    '나',
    '가',
    '대기 1',
    '다',
    '취소·내보냄 1',
    '라',
  ]);
});

it('shows an empty notice', async () => {
  mockServer({ '/meetups/1': ok(detail()), [LIST]: ok([]) });
  await renderApp(PATH);
  expect(screen.getByText('아직 신청자가 없어요')).toBeOnTheScreen();
});

it('shows the penalty counts only when there are some', async () => {
  mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: ok([row({ id: 1, penaltyTotal: 3, penaltyRecent: 1 }), row({ id: 2 })]),
  });
  await renderApp(PATH);
  expect(screen.getAllByText(/블랙리스트 \d/)).toHaveLength(1);
  expect(screen.getByText('블랙리스트 3회 (최근 3개월 1회)')).toBeOnTheScreen();
});

it('removes an applicant after confirming', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: ok([row()]),
    'POST /meetup-applications/11/remove': { status: 204 },
  });
  await renderApp(PATH);
  await press('별이 내보내기');
  expect(server.count('POST /meetup-applications/11/remove')).toBe(0);
  await press('별이 내보내기 확정');
  expect(server.count('POST /meetup-applications/11/remove')).toBe(1);
  expect(server.count(`GET ${LIST}`)).toBe(2);
});

it('does not offer removing after the start', async () => {
  mockServer({ '/meetups/1': ok(detail({ meetup: ENDED })), [LIST]: ok([row()]) });
  await renderApp(PATH);
  expect(button('별이 내보내기')).toBeNull();
});

it('shows a late cancel and adds a penalty after confirming', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: ok([late()]),
    'POST /meetup-applications/11/penalties': ok({ id: 1 }),
  });
  await renderApp(PATH);
  expect(screen.getByText('늦은 취소')).toBeOnTheScreen();
  expect(screen.getByText('사유: 부상')).toBeOnTheScreen();
  await press('별이 블랙리스트에 기록');
  await press('별이 기록 확정');
  expect(server.count('POST /meetup-applications/11/penalties')).toBe(1);
  expect(server.count(`GET ${LIST}`)).toBe(2);
});

it.each([
  ['penalized', late({ lateCancelPenalized: true }), {}],
  ['a canceled meetup', late(), { status: 'CANCELED' as const }],
  ['not late', row({ status: 'CANCELED', canceledAt: '2026-11-21T03:00:00Z' }), {}],
])('offers no penalty: %s', async (_, application, meetup) => {
  mockServer({ '/meetups/1': ok(detail({ meetup })), [LIST]: ok([application]) });
  await renderApp(PATH);
  expect(screen.getByText('별이')).toBeOnTheScreen();
  expect(button('별이 블랙리스트에 기록')).toBeNull();
});

it('corrects to EXCUSED at once, and not to the result already selected', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail({ meetup: ENDED })),
    [LIST]: ok([recorded()]),
    'PUT /attendances/51': ok({ id: 51, result: 'EXCUSED' }),
  });
  await renderApp(PATH);
  await fireEvent.press(tab('별이 참석'));
  await flush();
  expect(server.count('PUT /attendances/51')).toBe(0);
  await fireEvent.press(tab('별이 불참'));
  await flush();
  expect(server.calls.filter((c) => c.method === 'PUT').map((c) => c.body)).toEqual([
    { result: 'EXCUSED' },
  ]);
});

it('asks before marking NO_SHOW', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail({ meetup: ENDED })),
    [LIST]: ok([recorded()]),
    'PUT /attendances/51': ok({ id: 51, result: 'NO_SHOW' }),
  });
  await renderApp(PATH);
  await fireEvent.press(tab('별이 노쇼'));
  expect(screen.getByText('블랙리스트에 1회 기록돼요.')).toBeOnTheScreen();
  expect(server.count('PUT /attendances/51')).toBe(0);
  await press('별이 노쇼 확정');
  expect(server.calls.find((c) => c.method === 'PUT')?.body).toEqual({ result: 'NO_SHOW' });
  expect(screen.queryByText('블랙리스트에 1회 기록돼요.')).toBeNull();
});

it('can step back from NO_SHOW', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail({ meetup: ENDED })),
    [LIST]: ok([recorded()]),
  });
  await renderApp(PATH);
  await fireEvent.press(tab('별이 노쇼'));
  await press('별이 돌아가기');
  expect(screen.queryByText('블랙리스트에 1회 기록돼요.')).toBeNull();
  expect(server.count('PUT /attendances/51')).toBe(0);
});

it('waits for the attendance record after the end', async () => {
  mockServer({ '/meetups/1': ok(detail({ meetup: ENDED })), [LIST]: ok([row()]) });
  await renderApp(PATH);
  expect(screen.getByText('참석 기록을 준비하고 있어요')).toBeOnTheScreen();
  expect(screen.queryByRole('radio')).toBeNull();
});

it('corrects two people independently', async () => {
  let finish = (_: Reply) => {};
  const server = mockServer({
    '/meetups/1': ok(detail({ meetup: ENDED })),
    [LIST]: ok([
      recorded(),
      recorded({ id: 12, rosterMemberId: 102, somoimNickname: '달이', attendanceId: 52 }),
    ]),
    'PUT /attendances/51': () => new Promise<Reply>((resolve) => (finish = resolve)),
    'PUT /attendances/52': ok({ id: 52, result: 'EXCUSED' }),
  });
  await renderApp(PATH);
  await fireEvent.press(tab('별이 불참'));
  await fireEvent.press(tab('달이 불참'));
  await flush();
  expect(server.count('PUT /attendances/52')).toBe(1);
  await act(async () => finish(ok({ id: 51, result: 'EXCUSED' })));
  await flush();
});

it('keeps the server message and refetches when stale', async () => {
  let stale = false;
  mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: () =>
      ok([stale ? row({ status: 'CANCELED', canceledAt: '2026-11-24T02:59:00Z' }) : row()]),
    'POST /meetup-applications/11/remove': () => {
      stale = true;
      return fail(409, 'MEETUP_APPLICATION_NOT_ACTIVE', '이미 취소되었거나 내보낸 신청입니다.');
    },
  });
  await renderApp(PATH);
  await press('별이 내보내기');
  await press('별이 내보내기 확정');
  expect(screen.getByText('취소·내보냄 1')).toBeOnTheScreen();
  expect(screen.getByText('이미 취소되었거나 내보낸 신청입니다.')).toBeOnTheScreen();
});

it('opens the penalties of the applicant', async () => {
  mockServer({ '/meetups/1': ok(detail()), [LIST]: ok([row()]) });
  const app = await renderApp(PATH);
  await press('별이 블랙리스트 보기');
  expect(app.getPathname()).toBe('/admin/penalties/101');
});

it('does not request for a bad id', async () => {
  const server = mockServer({});
  await renderApp('/admin/meetups/abc/applications');
  expect(screen.getByText('벙을 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/meetups/'))).toBe(false);
});

it.each([
  ['내보내기', '내보내기 확정', 'POST /meetup-applications/11/remove', row()],
  ['블랙리스트에 기록', '기록 확정', 'POST /meetup-applications/11/penalties', late()],
])('does not offer %s again while the list is refetched', async (label, confirm, path, item) => {
  let sent = false;
  mockServer({
    '/meetups/1': ok(detail()),
    [LIST]: () => (sent ? new Promise<Reply>(() => {}) : ok([item])),
    [path]: () => {
      sent = true;
      return label === '내보내기' ? { status: 204 } : ok({ id: 1 });
    },
  });
  await renderApp(PATH);
  await press(`별이 ${label}`);
  await press(`별이 ${confirm}`);
  expect(sent).toBe(true);
  expect(button(`별이 ${label}`)).toBeNull();
});
