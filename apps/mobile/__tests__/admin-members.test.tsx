import { act, screen } from 'expo-router/testing-library';

import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { MemberResponse } from '@/types/api/member';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const ALL = '/members?page=0&size=20';
const search = (q: string) => `/members?q=${encodeURIComponent(q)}&page=0&size=20`;
const member = (over: Partial<MemberResponse> = {}): MemberResponse => ({
  userId: 21,
  nickname: '앱별이',
  role: 'MEMBER',
  rosterMemberId: 101,
  somoimNickname: '별이',
  ...over,
});
const list = (items: MemberResponse[]) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });

async function settle() {
  await act(async () => {
    await jest.advanceTimersByTimeAsync(300);
  });
  await flush();
}

beforeEach(() => startAs('ADMIN'));

it('lists members and searches with the encoded query', async () => {
  const server = mockServer({
    [ALL]: list([member(), member({ userId: 22, nickname: '앱달이' })]),
    [search('달 &')]: list([member({ userId: 22, nickname: '앱달이' })]),
  });
  await renderApp('/admin/members');
  expect(screen.getByText('앱별이')).toBeOnTheScreen();
  await type('닉네임 검색', ' 달 & ');
  await settle();
  expect(server.count(search('달 &'))).toBe(1);
  expect(screen.queryByText('앱별이')).toBeNull();
  expect(screen.getByText('앱달이')).toBeOnTheScreen();
});

it('shows an empty notice', async () => {
  mockServer({ [ALL]: list([]) });
  await renderApp('/admin/members');
  expect(screen.getByText('찾는 회원이 없어요')).toBeOnTheScreen();
});

it('keeps the answer of the last query when responses arrive out of order', async () => {
  let finish = (_: Reply) => {};
  mockServer({
    [ALL]: list([member()]),
    [search('가')]: () => new Promise<Reply>((resolve) => (finish = resolve)),
    [search('나')]: list([member({ userId: 23, nickname: '나나' })]),
  });
  await renderApp('/admin/members');
  await type('닉네임 검색', '가');
  await settle();
  expect(screen.queryByText('앱별이')).toBeNull();
  await type('닉네임 검색', '나');
  await settle();
  await act(async () => finish(list([member({ userId: 24, nickname: '가가' })])));
  await flush();
  expect(screen.getByText('나나')).toBeOnTheScreen();
  expect(screen.queryByText('가가')).toBeNull();
});

it('revokes a MEMBER after confirming', async () => {
  let revoked = false;
  const server = mockServer({
    [ALL]: () => list(revoked ? [] : [member()]),
    'DELETE /members/21': () => {
      revoked = true;
      return { status: 204 };
    },
  });
  await renderApp('/admin/members');
  await press('앱별이 자격 해제');
  expect(server.count('DELETE /members/21')).toBe(0);
  expect(screen.getByText(/다가오는 벙 신청도 취소돼요/)).toBeOnTheScreen();
  await press('앱별이 자격 해제 확정');
  expect(server.count('DELETE /members/21')).toBe(1);
  expect(screen.queryByText('앱별이')).toBeNull();
});

it('offers no revoke for an ADMIN', async () => {
  mockServer({ [ALL]: list([member({ role: 'ADMIN' })]) });
  await renderApp('/admin/members');
  expect(screen.getByText('운영진')).toBeOnTheScreen();
  expect(button('앱별이 자격 해제')).toBeNull();
});

it('opens the penalties of a linked member only', async () => {
  mockServer({
    [ALL]: list([
      member(),
      member({ userId: 22, nickname: '앱달이', rosterMemberId: null, somoimNickname: null }),
    ]),
  });
  const app = await renderApp('/admin/members');
  expect(screen.getByText('명부와 연결되지 않았어요')).toBeOnTheScreen();
  expect(button('앱달이 블랙리스트 보기')).toBeNull();
  await press('앱별이 블랙리스트 보기');
  expect(app.getPathname()).toBe('/admin/penalties/101');
});

it('keeps the message and refetches when the member was already revoked', async () => {
  let revoked = false;
  mockServer({
    [ALL]: () => list(revoked ? [] : [member()]),
    'DELETE /members/21': () => {
      revoked = true;
      return fail(404, 'COMMON_NOT_FOUND', '요청한 항목을 찾을 수 없습니다.');
    },
  });
  await renderApp('/admin/members');
  await press('앱별이 자격 해제');
  await press('앱별이 자격 해제 확정');
  expect(screen.getByText('요청한 항목을 찾을 수 없습니다.')).toBeOnTheScreen();
  expect(screen.queryByText('앱별이')).toBeNull();
});

it('turns to the blocked notice after a 403', async () => {
  let demoted = false;
  mockServer({
    [ALL]: () => {
      demoted = true;
      return fail(403, 'AUTH_FORBIDDEN', '권한이 없습니다.');
    },
    '/users/me': () => ok({ id: 1, nickname: '별이', role: demoted ? 'MEMBER' : 'ADMIN' }),
  });
  await renderApp('/admin/members');
  await flush();
  expect(screen.getByText('운영진만 볼 수 있어요')).toBeOnTheScreen();
});
