import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { fail, flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { summary } from '@/test-utils/meetups';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/meetups?from=2026-11-24&to=2027-01-25';
const NOVEMBER = '/meetups?from=2026-11-01&to=2026-11-30';
const DECEMBER = '/meetups?from=2026-12-01&to=2026-12-31';

beforeEach(() => startAs('MEMBER'));

async function press(role: 'button' | 'tab', name: string | RegExp) {
  await fireEvent.press(screen.getByRole(role, { name }));
  await flush();
}

it('requests 62 days from the Seoul today', async () => {
  await startAs('MEMBER', '2026-11-23T15:00:00Z');
  const server = mockServer({ [LIST]: ok([]) });
  await renderApp('/meetups');
  expect(server.count(LIST)).toBe(1);
});

it('shows counts and my status', async () => {
  mockServer({ [LIST]: ok([summary({ waitlistCount: 3, myStatus: 'CONFIRMED' })]) });
  await renderApp('/meetups');
  expect(screen.getByText('화요 정기벙')).toBeOnTheScreen();
  expect(screen.getByText('확정 8/12')).toBeOnTheScreen();
  expect(screen.getByText('대기 3')).toBeOnTheScreen();
  expect(screen.getByLabelText('내 상태: 확정')).toBeOnTheScreen();
});

it('shows only the confirmed count without a capacity', async () => {
  mockServer({ [LIST]: ok([summary({ capacity: null })]) });
  await renderApp('/meetups');
  expect(screen.getByText('확정 8')).toBeOnTheScreen();
});

it('marks canceled meetups and hides ended ones', async () => {
  mockServer({
    [LIST]: ok([
      summary({
        id: 1,
        title: '끝난 벙',
        startsAt: '2026-11-24T00:00:00Z',
        endsAt: '2026-11-24T03:00:00Z',
      }),
      summary({ id: 2, title: '없어진 벙', status: 'CANCELED' }),
    ]),
  });
  await renderApp('/meetups');
  expect(screen.queryByText('끝난 벙')).toBeNull();
  expect(screen.getByText('없어진 벙')).toBeOnTheScreen();
  expect(screen.getByText('취소된 벙')).toBeOnTheScreen();
});

it('groups the list by Seoul date', async () => {
  mockServer({
    [LIST]: ok([
      summary({ id: 1, startsAt: '2026-11-25T14:59:00Z', endsAt: '2026-11-25T16:00:00Z' }),
      summary({ id: 2, startsAt: '2026-11-25T15:00:00Z', endsAt: '2026-11-25T16:00:00Z' }),
    ]),
  });
  await renderApp('/meetups');
  expect(screen.getByText('11월 25일(수)')).toBeOnTheScreen();
  expect(screen.getByText('11월 26일(목)')).toBeOnTheScreen();
});

it('shows an empty notice', async () => {
  mockServer({ [LIST]: ok([]) });
  await renderApp('/meetups');
  expect(screen.getByText('예정된 벙이 없어요')).toBeOnTheScreen();
});

it('offers a retry when the first request fails', async () => {
  const server = mockServer({
    [LIST]: fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.'),
  });
  await renderApp('/meetups');
  expect(screen.getByText('서버에 문제가 있습니다.')).toBeOnTheScreen();
  await press('button', '다시 시도');
  expect(server.count(LIST)).toBe(2);
});

it('keeps the list when a refetch fails', async () => {
  let failing = false;
  mockServer({
    [LIST]: () =>
      failing ? fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.') : ok([summary()]),
  });
  await renderApp('/meetups');
  failing = true;
  await act(async () => {
    await queryClient.refetchQueries({ queryKey: ['meetups'] });
  });
  expect(screen.getByText('화요 정기벙')).toBeOnTheScreen();
});

it('asks the server for the visible month in the calendar', async () => {
  const server = mockServer({ [LIST]: ok([]), [NOVEMBER]: ok([]), [DECEMBER]: ok([]) });
  await renderApp('/meetups');
  await press('tab', '달력');
  expect(server.count(NOVEMBER)).toBe(1);
  await press('button', '다음 달');
  expect(screen.getByText('2026년 12월')).toBeOnTheScreen();
  expect(server.count(DECEMBER)).toBe(1);
  expect(screen.getByRole('button', { name: '12월 1일(화)' })).toBeSelected();
});

it('shows the meetups of the pressed day, including past ones', async () => {
  mockServer({
    [LIST]: ok([]),
    [NOVEMBER]: ok([
      summary({
        title: '지난 벙',
        startsAt: '2026-11-03T10:00:00Z',
        endsAt: '2026-11-03T12:00:00Z',
      }),
      summary({ id: 2, startsAt: '2026-11-03T11:00:00Z', endsAt: '2026-11-03T12:00:00Z' }),
    ]),
  });
  await renderApp('/meetups');
  await press('tab', '달력');
  expect(screen.getByText('이날은 벙이 없어요')).toBeOnTheScreen();
  await press('button', '11월 3일(화), 벙 2개');
  expect(screen.getByText('지난 벙')).toBeOnTheScreen();
});

it('does not call the API for a USER', async () => {
  await startAs('USER');
  const server = mockServer({});
  await renderApp('/meetups');
  expect(server.calls.filter((c) => c.path.startsWith('/meetups'))).toEqual([]);
  expect(screen.getByText('동호회원 인증이 필요해요')).toBeOnTheScreen();
});

it('shows verification when membership was just revoked', async () => {
  mockServer({
    [LIST]: fail(403, 'AUTH_FORBIDDEN', '권한이 없습니다.'),
    '/users/me': ok({ id: 1, nickname: '별이', role: 'USER' }),
  });
  await renderApp('/meetups');
  await flush();
  expect(screen.getByText('동호회원 인증이 필요해요')).toBeOnTheScreen();
});

it('updates and then drops a card as time passes on an open screen', async () => {
  mockServer({
    [LIST]: ok([summary({ startsAt: '2026-11-24T03:01:00Z', endsAt: '2026-11-24T03:02:00Z' })]),
  });
  await renderApp('/meetups');
  expect(screen.queryByText('진행 중')).toBeNull();
  await act(async () => jest.advanceTimersByTimeAsync(61_000));
  expect(screen.getByText('진행 중')).toBeOnTheScreen();
  await act(async () => jest.advanceTimersByTimeAsync(61_000));
  expect(screen.queryByText('화요 정기벙')).toBeNull();
});
