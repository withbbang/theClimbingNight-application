import { Linking } from 'react-native';

import { act, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { sessionStore } from '@/lib/session-store';
import { signOut } from '@/lib/sign-out';
import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { summary } from '@/test-utils/meetups';
import { button, press } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/meetups?from=2026-11-24&to=2027-01-25';
const NOTICES = '/notices?page=0&size=3';
const notices = ok({
  items: [{ id: 1, title: '11월 정기총회', pinned: true, publishedAt: '2026-11-20T03:00:00Z' }],
  page: 0,
  size: 3,
  totalElements: 1,
  totalPages: 1,
});
const settings = (somoimJoinUrl: string | null) => ok({ somoimJoinUrl, smartstoreUrl: null });
const JOIN = '동호회 가입하기';

let openURL: jest.SpyInstance;
beforeEach(async () => {
  openURL = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
  await startAs('MEMBER');
});
afterEach(() => openURL.mockRestore());

it('shows up to three open upcoming meetups to a MEMBER', async () => {
  mockServer({
    [LIST]: ok([
      summary({ id: 1, title: '시작한 벙', startsAt: '2026-11-24T03:00:00Z' }),
      summary({ id: 2, title: '취소된 모임', status: 'CANCELED' }),
      summary({ id: 3, title: '첫째' }),
      summary({ id: 4, title: '둘째' }),
      summary({ id: 5, title: '셋째' }),
      summary({ id: 6, title: '넷째' }),
    ]),
  });
  await renderApp('/');
  expect(['첫째', '둘째', '셋째'].every((t) => screen.queryByText(t))).toBe(true);
  expect(['시작한 벙', '취소된 모임', '넷째'].some((t) => screen.queryByText(t))).toBe(false);
});

it('opens the detail when a card is pressed', async () => {
  mockServer({ [LIST]: ok([summary()]) });
  const app = await renderApp('/');
  await press(/화요 정기벙/);
  expect(app.getPathname()).toBe('/meetups/1');
});

it('shows notices and no meetups to a guest', async () => {
  await startAs(null);
  const server = mockServer({ [NOTICES]: notices });
  await renderApp('/');
  expect(screen.getByText('11월 정기총회')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/meetups'))).toBe(false);
});

it.each([null, 'USER'] as const)('opens the somoim link for %p', async (role) => {
  await startAs(role);
  mockServer({ '/app-settings': settings('https://somoim.example/join') });
  await renderApp('/');
  await press(JOIN);
  expect(openURL).toHaveBeenCalledWith('https://somoim.example/join');
});

it('hides the join button for a MEMBER', async () => {
  mockServer({ '/app-settings': settings('https://somoim.example/join') });
  await renderApp('/');
  expect(button(JOIN)).toBeNull();
});

it('hides the join button without a link', async () => {
  await startAs(null);
  mockServer({ '/app-settings': settings(null) });
  await renderApp('/');
  expect(button(JOIN)).toBeNull();
});

it('shows a message when the link cannot open', async () => {
  await startAs(null);
  openURL.mockRejectedValue(new Error('no app'));
  mockServer({ '/app-settings': settings('https://somoim.example/join') });
  await renderApp('/');
  await press(JOIN);
  expect(screen.getByText('링크를 열지 못했어요.')).toBeOnTheScreen();
});

it('still shows notices when the meetups request fails', async () => {
  mockServer({
    [LIST]: fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.'),
    [NOTICES]: notices,
  });
  await renderApp('/');
  expect(screen.getByText('11월 정기총회')).toBeOnTheScreen();
  expect(screen.getByText('서버에 문제가 있습니다.')).toBeOnTheScreen();
});

it('drops a meetup that starts while the screen stays open', async () => {
  await startAs('MEMBER');
  mockServer({
    [LIST]: ok([summary({ startsAt: '2026-11-24T03:01:00Z', endsAt: '2026-11-24T05:00:00Z' })]),
  });
  await renderApp('/');
  expect(screen.getByText('화요 정기벙')).toBeOnTheScreen();
  await act(async () => jest.advanceTimersByTimeAsync(61_000));
  expect(screen.queryByText('화요 정기벙')).toBeNull();
});

it('keeps refreshing notices after signing out', async () => {
  let title = '옛 공지';
  mockServer({
    [NOTICES]: () =>
      ok({
        items: [{ id: 1, title, pinned: false, publishedAt: '2026-11-20T03:00:00Z' }],
        page: 0,
        size: 3,
        totalElements: 1,
        totalPages: 1,
      }),
    'POST /auth/logout': { status: 204 },
  });
  await renderApp('/');
  expect(screen.getByText('옛 공지')).toBeOnTheScreen();
  await act(async () => signOut());
  await flush();
  expect(sessionStore.getState().status).toBe('guest');
  title = '새 공지';
  await act(async () => queryClient.invalidateQueries({ queryKey: ['notices'] }));
  await flush();
  expect(screen.getByText('새 공지')).toBeOnTheScreen();
});

it('does not stay loading when signed out while notices load', async () => {
  let first = true;
  mockServer({
    [NOTICES]: () => {
      if (first) {
        first = false;
        return new Promise<Reply>(() => {});
      }
      return notices;
    },
    'POST /auth/logout': { status: 204 },
  });
  await renderApp('/');
  await act(async () => signOut());
  await flush();
  expect(screen.getByText('11월 정기총회')).toBeOnTheScreen();
});

it('opens a notice from home', async () => {
  mockServer({ [NOTICES]: notices });
  const app = await renderApp('/');
  await press('고정된 공지, 11월 정기총회');
  expect(app.getPathname()).toBe('/notices/1');
});

it('opens all notices from home', async () => {
  mockServer({ [NOTICES]: notices });
  const app = await renderApp('/');
  await press('공지 모두 보기');
  expect(app.getPathname()).toBe('/notices');
});

it('hides all notices when there are none', async () => {
  mockServer({
    [NOTICES]: ok({ items: [], page: 0, size: 3, totalElements: 0, totalPages: 0 }),
  });
  await renderApp('/');
  expect(screen.getByText('아직 공지가 없어요')).toBeOnTheScreen();
  expect(button('공지 모두 보기')).toBeNull();
});

it.each([
  [null, true],
  ['USER', true],
  ['MEMBER', false],
] as const)('introduces the club to %p: %p', async (role, shown) => {
  await startAs(role);
  mockServer({ '/app-settings': settings(null) });
  const app = await renderApp('/');
  expect(button('동호회 소개') !== null).toBe(shown);
  if (shown) {
    await press('동호회 소개');
    expect(app.getPathname()).toBe('/about');
  }
});
