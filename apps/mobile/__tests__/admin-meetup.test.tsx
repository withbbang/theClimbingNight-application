import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { detail } from '@/test-utils/meetups';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const D1 = '/meetups/1';
const NOW = new Date('2026-11-24T03:00:00Z');

beforeEach(() => startAs('ADMIN'));

async function fillNew() {
  await type('제목', ' 새 벙 ');
  await type('장소', '더클라임');
  await fireEvent.press(screen.getByLabelText('11월 25일(수)'));
  await type('시작 시각', '2200');
  await type('종료 시각', '0100');
  await type('정원', '10');
}

it('opens a meetup with the defaults and replaces the form with the detail', async () => {
  const server = mockServer({
    'POST /meetups': ok(detail({ meetup: { id: 5 } }).meetup),
    '/meetups/5': ok(detail({ meetup: { id: 5, title: '새 벙' } })),
  });
  const app = await renderApp('/admin');
  await press('벙 열기');
  await fillNew();
  expect(screen.getByText('다음 날 01:00에 끝나요.')).toBeOnTheScreen();
  await press('벙 열기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
    title: '새 벙',
    placeName: '더클라임',
    description: null,
    startsAt: '2026-11-25T13:00:00.000Z',
    endsAt: '2026-11-25T16:00:00.000Z',
    capacity: 10,
    lateCancelHours: 24,
    participantsVisible: true,
  });
  expect(app.getPathname()).toBe('/meetups/5');
  await press('뒤로');
  expect(app.getPathname()).toBe('/admin');
});

it('limits the lengths', async () => {
  mockServer({});
  await renderApp('/admin/meetups/new');
  expect(screen.getByLabelText('제목').props.maxLength).toBe(100);
  expect(screen.getByLabelText('장소').props.maxLength).toBe(100);
  expect(screen.getByLabelText('설명 (선택)').props.maxLength).toBe(5000);
});

it('shows field errors without a request', async () => {
  const server = mockServer({});
  await renderApp('/admin/meetups/new');
  await type('정원', '501');
  await press('벙 열기');
  expect(screen.getByText('제목을 입력해 주세요.')).toBeOnTheScreen();
  expect(screen.getByText('정원은 1명에서 500명까지예요.')).toBeOnTheScreen();
  expect(server.count('POST /meetups')).toBe(0);
});

it('shows the server message and keeps the form', async () => {
  mockServer({
    'POST /meetups': fail(400, 'MEETUP_START_IN_PAST', '시작 시각은 지금 이후여야 합니다.'),
  });
  await renderApp('/admin/meetups/new');
  await fillNew();
  await press('벙 열기');
  expect(screen.getByText('시작 시각은 지금 이후여야 합니다.')).toBeOnTheScreen();
  expect(screen.getByLabelText('제목').props.value).toBe(' 새 벙 ');
});

it('fills the edit form from a fresh fetch and keeps typing during a refetch', async () => {
  queryClient.setQueryData(['meetups', 'detail', 1], detail());
  let capacity = 20;
  const server = mockServer({
    [D1]: () => ok(detail({ meetup: { capacity } })),
    'PUT /meetups/1': ok(detail().meetup),
  });
  const app = await renderApp('/admin/meetups/1/edit');
  expect(screen.getByLabelText('정원').props.value).toBe('20');
  await type('정원', '15');
  capacity = 30;
  await act(async () => {
    await queryClient.invalidateQueries({ queryKey: ['meetups'] });
  });
  await flush();
  expect(screen.getByLabelText('정원').props.value).toBe('15');
  await press('저장');
  expect(server.calls.find((c) => c.method === 'PUT')?.body).toEqual({
    title: '화요 정기벙',
    placeName: '더클라임 연남',
    description: '가볍게 풀어요',
    startsAt: '2026-11-26T10:30:00.000Z',
    endsAt: '2026-11-26T13:30:00.000Z',
    capacity: 15,
    lateCancelHours: 24,
    participantsVisible: true,
  });
  expect(app.getPathname()).toBe(D1);
});

it('does not show a stale edit form when the fetch fails', async () => {
  queryClient.setQueryData(['meetups', 'detail', 1], detail());
  mockServer({ [D1]: fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') });
  await renderApp('/admin/meetups/1/edit');
  expect(screen.queryByLabelText('정원')).toBeNull();
  expect(button('다시 시도')).not.toBeNull();
});

it('keeps the edit form when a later refetch fails', async () => {
  let reply: Reply = ok(detail());
  mockServer({ [D1]: () => reply });
  await renderApp('/admin/meetups/1/edit');
  reply = fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류');
  await act(async () => {
    await queryClient.invalidateQueries({ queryKey: ['meetups'] });
  });
  await flush();
  expect(screen.getByLabelText('정원').props.value).toBe('12');
});

it('copies with only a new schedule, a week later', async () => {
  const server = mockServer({
    [D1]: ok(detail()),
    'POST /meetups/1/copy': ok(detail({ meetup: { id: 6 } }).meetup),
    '/meetups/6': ok(detail({ meetup: { id: 6 } })),
  });
  const app = await renderApp('/admin/meetups/1/copy');
  await press('이 날짜로 열기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
    startsAt: '2026-12-03T10:30:00.000Z',
    endsAt: '2026-12-03T13:30:00.000Z',
  });
  expect(app.getPathname()).toBe('/meetups/6');
});

it('starts the copy from today when a week later has passed', async () => {
  const server = mockServer({
    [D1]: ok(
      detail({ meetup: { startsAt: '2026-10-01T10:30:00Z', endsAt: '2026-10-01T13:30:00Z' } }),
    ),
    'POST /meetups/1/copy': ok(detail({ meetup: { id: 6 } }).meetup),
  });
  await renderApp('/admin/meetups/1/copy');
  await press('이 날짜로 열기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toMatchObject({
    startsAt: '2026-11-24T10:30:00.000Z',
  });
});

it('cancels only after confirming', async () => {
  const server = mockServer({
    [D1]: ok(detail()),
    'POST /meetups/1/cancel': ok(detail({ meetup: { status: 'CANCELED' } }).meetup),
  });
  await renderApp(D1);
  await press('벙 취소');
  expect(server.count('POST /meetups/1/cancel')).toBe(0);
  await press('벙 취소 확정');
  expect(server.count('POST /meetups/1/cancel')).toBe(1);
  expect(server.count(`GET ${D1}`)).toBe(2);
});

it.each([
  ['upcoming', {}, true, true, true],
  ['started', { startsAt: NOW.toISOString() }, false, true, true],
  [
    'ended',
    { startsAt: '2026-11-23T10:00:00Z', endsAt: '2026-11-23T12:00:00Z' },
    false,
    false,
    true,
  ],
  ['canceled', { status: 'CANCELED' as const }, false, false, true],
  ['imported', { capacity: null }, true, true, false],
])('shows the admin actions of a meetup: %s', async (_, meetup, edit, cancel, copy) => {
  mockServer({ [D1]: ok(detail({ meetup })) });
  await renderApp(D1);
  expect(button('신청자 관리')).not.toBeNull();
  expect(button('벙 수정') !== null).toBe(edit);
  expect(button('벙 취소') !== null).toBe(cancel);
  expect(button('벙 복제') !== null).toBe(copy);
});

it.each([
  ['신청자 관리', '/admin/meetups/1/applications'],
  ['벙 수정', '/admin/meetups/1/edit'],
  ['벙 복제', '/admin/meetups/1/copy'],
])('opens %s', async (label, path) => {
  mockServer({ [D1]: ok(detail()) });
  const app = await renderApp(D1);
  await press(label);
  expect(app.getPathname()).toBe(path);
});

it('hides the admin actions from a MEMBER', async () => {
  await startAs('MEMBER');
  mockServer({ [D1]: ok(detail()) });
  await renderApp(D1);
  expect(button('신청자 관리')).toBeNull();
});

it('hides the open button of the tab from a MEMBER', async () => {
  await startAs('MEMBER');
  mockServer({});
  await renderApp('/meetups');
  expect(button('벙 열기')).toBeNull();
});

it('opens the form from the meetups tab', async () => {
  mockServer({});
  const app = await renderApp('/meetups');
  await press('벙 열기');
  expect(app.getPathname()).toBe('/admin/meetups/new');
});

it.each(['edit', 'copy'])('does not request for a bad id: %s', async (screenName) => {
  const server = mockServer({});
  await renderApp(`/admin/meetups/abc/${screenName}`);
  expect(screen.getByText('벙을 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/meetups/'))).toBe(false);
});
