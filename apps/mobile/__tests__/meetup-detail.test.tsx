import { act, fireEvent, screen } from 'expo-router/testing-library';

import { fail, flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { detail } from '@/test-utils/meetups';
import { startAs } from '@/test-utils/session';
import type { MyApplicationResponse } from '@/types/api/meetup';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const APPLY = 'POST /meetups/1/applications';
const CANCEL = 'POST /meetups/1/applications/me/cancel';
const mine = (over: Partial<MyApplicationResponse> = {}): MyApplicationResponse => ({
  applicationId: 5,
  status: 'CONFIRMED',
  waitlistPosition: null,
  lateCancel: false,
  cancelReason: null,
  ...over,
});

beforeEach(() => startAs('MEMBER'));

async function press(name: string) {
  await fireEvent.press(screen.getByRole('button', { name }));
  await flush();
}
const button = (name: string) => screen.queryByRole('button', { name });

it('applies in one press and shows confirmed', async () => {
  let applied = false;
  const server = mockServer({
    '/meetups/1': () => ok(detail(applied ? { my: mine() } : {})),
    [APPLY]: () => {
      applied = true;
      return ok(mine());
    },
  });
  await renderApp('/meetups/1');
  await press('신청하기');
  expect(server.count(APPLY)).toBe(1);
  expect(screen.getByText('참여가 확정됐어요')).toBeOnTheScreen();
});

it('shows the waitlist position', async () => {
  mockServer({
    '/meetups/1': ok(detail({ my: mine({ status: 'WAITLISTED', waitlistPosition: 2 }) })),
  });
  await renderApp('/meetups/1');
  expect(screen.getByText('대기 2번')).toBeOnTheScreen();
});

it('sends one request when pressed twice', async () => {
  const server = mockServer({ '/meetups/1': ok(detail()), [APPLY]: ok(mine()) });
  await renderApp('/meetups/1');
  const apply = screen.getByRole('button', { name: '신청하기' });
  await act(async () => {
    void fireEvent.press(apply);
    void fireEvent.press(apply);
  });
  await flush();
  expect(server.count(APPLY)).toBe(1);
});

it('shows the server message and refetches when the meetup is stale', async () => {
  const server = mockServer({
    '/meetups/1': ok(detail()),
    [APPLY]: fail(409, 'MEETUP_ALREADY_APPLIED', '이미 신청한 벙입니다.'),
  });
  await renderApp('/meetups/1');
  await press('신청하기');
  expect(screen.getByText('이미 신청한 벙입니다.')).toBeOnTheScreen();
  expect(server.count('GET /meetups/1')).toBe(2);
});

it('shows the roster message to an admin without a roster entry', async () => {
  await startAs('ADMIN');
  mockServer({
    '/meetups/1': ok(detail()),
    [APPLY]: fail(
      403,
      'MEETUP_ROSTER_REQUIRED',
      '동호회원 명부와 연결된 계정만 신청할 수 있습니다.',
    ),
  });
  await renderApp('/meetups/1');
  await press('신청하기');
  expect(screen.getByText('동호회원 명부와 연결된 계정만 신청할 수 있습니다.')).toBeOnTheScreen();
});

it('hides apply after the start, also while the screen stays open', async () => {
  mockServer({ '/meetups/1': ok(detail({ meetup: { startsAt: '2026-11-24T03:01:00Z' } })) });
  await renderApp('/meetups/1');
  expect(button('신청하기')).not.toBeNull();
  await act(async () => {
    await jest.advanceTimersByTimeAsync(61_000);
  });
  expect(button('신청하기')).toBeNull();
});

it('hides cancel after the start', async () => {
  mockServer({
    '/meetups/1': ok(detail({ my: mine(), meetup: { startsAt: '2026-11-24T03:00:00Z' } })),
  });
  await renderApp('/meetups/1');
  expect(button('신청 취소')).toBeNull();
});

it('does not set a timer longer than the platform allows', async () => {
  mockServer({ '/meetups/1': ok(detail({ meetup: { startsAt: '2027-03-01T03:00:00Z' } })) });
  const timeout = jest.spyOn(globalThis, 'setTimeout');
  await renderApp('/meetups/1');
  expect(timeout.mock.calls.every(([, ms]) => (ms ?? 0) <= 2 ** 31 - 1)).toBe(true);
  timeout.mockRestore();
});

it('shows no apply button when removed', async () => {
  mockServer({ '/meetups/1': ok(detail({ my: mine({ status: 'REMOVED' }) })) });
  await renderApp('/meetups/1');
  expect(screen.getByText('운영진이 내보낸 벙이에요')).toBeOnTheScreen();
  expect(button('신청하기')).toBeNull();
});

it('allows applying again after canceling', async () => {
  mockServer({ '/meetups/1': ok(detail({ my: mine({ status: 'CANCELED' }) })) });
  await renderApp('/meetups/1');
  expect(button('신청하기')).not.toBeNull();
});

it('lets an applicant of a canceled meetup cancel, without apply', async () => {
  mockServer({ '/meetups/1': ok(detail({ my: mine(), meetup: { status: 'CANCELED' } })) });
  await renderApp('/meetups/1');
  expect(screen.getByText('취소된 벙이에요')).toBeOnTheScreen();
  expect(button('신청 취소')).not.toBeNull();
  expect(button('신청하기')).toBeNull();
});

it('shows participants in order when visible', async () => {
  mockServer({
    '/meetups/1': ok(
      detail({
        participants: [
          { somoimNickname: '가람', status: 'CONFIRMED', order: 1 },
          { somoimNickname: '나래', status: 'WAITLISTED', order: 1 },
        ],
      }),
    ),
  });
  await renderApp('/meetups/1');
  expect(screen.getByLabelText('확정 1번 가람')).toBeOnTheScreen();
  expect(screen.getByLabelText('대기 1번 나래')).toBeOnTheScreen();
});

it('shows only counts when participants is null', async () => {
  mockServer({ '/meetups/1': ok(detail({ participants: null })) });
  await renderApp('/meetups/1');
  expect(screen.getByText('명단은 공개되지 않아요')).toBeOnTheScreen();
  expect(screen.getByText('확정 8/12')).toBeOnTheScreen();
});

const WARNING = '취소 기준 시각이 지났어요. 늦은 취소로 표시될 수 있어요.';

it.each([
  ['2026-11-25T03:00:00Z', 'CONFIRMED', true],
  ['2026-11-25T03:01:00Z', 'CONFIRMED', false],
  ['2026-11-25T03:00:00Z', 'WAITLISTED', false],
] as const)('for a start at %s and %s, the late warning is %s', async (startsAt, status, shown) => {
  mockServer({ '/meetups/1': ok(detail({ my: mine({ status }), meetup: { startsAt } })) });
  await renderApp('/meetups/1/cancel');
  expect(screen.queryByText(WARNING) !== null).toBe(shown);
});

it.each([
  ['  부상  ', { reason: '부상' }],
  ['   ', {}],
])('sends the reason %p as %p and returns to a refreshed detail', async (reason, body) => {
  let canceled = false;
  const server = mockServer({
    '/meetups/1': () => ok(detail({ my: mine({ status: canceled ? 'CANCELED' : 'CONFIRMED' }) })),
    [CANCEL]: () => {
      canceled = true;
      return ok(mine({ status: 'CANCELED' }));
    },
  });
  const app = await renderApp('/meetups/1');
  await press('신청 취소');
  expect(app.getPathname()).toBe('/meetups/1/cancel');
  await fireEvent.changeText(screen.getByLabelText('취소 사유 (선택)'), reason);
  await press('취소 확정');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual(body);
  expect(app.getPathname()).toBe('/meetups/1');
  expect(button('신청하기')).not.toBeNull();
});

it('limits the reason to 200 characters', async () => {
  mockServer({ '/meetups/1': ok(detail({ my: mine() })) });
  await renderApp('/meetups/1/cancel');
  expect(screen.getByLabelText('취소 사유 (선택)').props.maxLength).toBe(200);
});

it('keeps the cancel screen with the server message when canceling fails', async () => {
  mockServer({
    '/meetups/1': ok(detail({ my: mine() })),
    [CANCEL]: fail(409, 'MEETUP_ALREADY_STARTED', '이미 시작한 벙입니다.'),
  });
  const app = await renderApp('/meetups/1/cancel');
  await press('취소 확정');
  expect(app.getPathname()).toBe('/meetups/1/cancel');
  expect(screen.getByText('이미 시작한 벙입니다.')).toBeOnTheScreen();
});

it('shows not found without calling the API for a bad id', async () => {
  const server = mockServer({});
  await renderApp('/meetups/abc');
  expect(screen.getByText('벙을 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.filter((c) => c.path.startsWith('/meetups/'))).toEqual([]);
});

it('shows not found with a way home on 404', async () => {
  mockServer({ '/meetups/1': fail(404, 'COMMON_NOT_FOUND', '요청한 항목을 찾을 수 없습니다.') });
  const app = await renderApp('/meetups/1');
  expect(screen.getByText('벙을 찾을 수 없어요')).toBeOnTheScreen();
  await press('홈으로');
  expect(app.getPathname()).toBe('/');
});
