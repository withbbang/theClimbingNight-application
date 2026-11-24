import { act, fireEvent, screen } from 'expo-router/testing-library';

import { flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { AttendanceRateResponse } from '@/types/api/attendance';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const path = (period: string, date: string) => `/attendance-rates/me?period=${period}&date=${date}`;
const NOVEMBER = path('MONTH', '2026-11-24');
const rate = (over: Partial<AttendanceRateResponse> = {}) =>
  ok({
    period: 'MONTH',
    from: '2026-11-01',
    to: '2026-11-30',
    attendedCount: 2,
    meetupCount: 3,
    rate: 66.7,
    ...over,
  });

beforeEach(() => startAs('MEMBER'));

async function choose(name: string) {
  await fireEvent.press(screen.getByRole('tab', { name }));
  await flush();
}

it('shows the rate, the counts and the server range of this month', async () => {
  const server = mockServer({ [NOVEMBER]: rate() });
  await renderApp('/attendance');
  expect(server.count(NOVEMBER)).toBe(1);
  expect(await screen.findByText('66.7%')).toBeOnTheScreen();
  expect(screen.getByText('3번 중 2번 참석')).toBeOnTheScreen();
  expect(screen.getByText('2026년 11월 1일 ~ 11월 30일')).toBeOnTheScreen();
});

it('shows no percentage when the rate is null', async () => {
  mockServer({ [NOVEMBER]: rate({ attendedCount: 0, meetupCount: 0, rate: null }) });
  await renderApp('/attendance');
  expect(screen.getByText('아직 기록된 벙이 없어요')).toBeOnTheScreen();
  expect(screen.queryByText(/%/)).toBeNull();
});

it('shows 0% when nothing was attended', async () => {
  mockServer({ [NOVEMBER]: rate({ attendedCount: 0, rate: 0 }) });
  await renderApp('/attendance');
  expect(screen.getByText('0%')).toBeOnTheScreen();
});

it('moves to the previous period but not past the current one', async () => {
  const server = mockServer({
    [NOVEMBER]: rate(),
    [path('MONTH', '2026-10-24')]: rate({ from: '2026-10-01', to: '2026-10-31' }),
  });
  await renderApp('/attendance');
  expect(screen.getByRole('button', { name: '다음 기간' })).toBeDisabled();
  await press('이전 기간');
  expect(server.count(path('MONTH', '2026-10-24'))).toBe(1);
  expect(screen.getByRole('button', { name: '다음 기간' })).not.toBeDisabled();
});

it('can come back from a period that failed to load', async () => {
  mockServer({ [NOVEMBER]: rate() });
  await renderApp('/attendance');
  await press('이전 기간');
  expect(screen.getByRole('button', { name: '다음 기간' })).not.toBeDisabled();
});

it('returns to today when the period changes', async () => {
  const server = mockServer({
    [NOVEMBER]: rate(),
    [path('MONTH', '2026-10-24')]: rate({ from: '2026-10-01', to: '2026-10-31' }),
    [path('YEAR', '2026-11-24')]: rate({ from: '2026-01-01', to: '2026-12-31' }),
  });
  await renderApp('/attendance');
  await press('이전 기간');
  await choose('연');
  expect(server.count(path('YEAR', '2026-11-24'))).toBe(1);
});

it('keeps the last selected period when responses arrive out of order', async () => {
  let finishWeek = (_: Reply) => {};
  mockServer({
    [NOVEMBER]: rate(),
    [path('WEEK', '2026-11-24')]: () => new Promise<Reply>((resolve) => (finishWeek = resolve)),
    [path('YEAR', '2026-11-24')]: rate({ rate: 50, meetupCount: 4 }),
  });
  await renderApp('/attendance');
  await choose('주');
  await choose('연');
  await act(async () => finishWeek(rate({ rate: 100 })));
  await flush();
  expect(await screen.findByText('50%')).toBeOnTheScreen();
  expect(screen.queryByText('100%')).toBeNull();
});
