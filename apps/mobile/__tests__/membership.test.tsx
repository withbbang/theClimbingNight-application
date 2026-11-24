import { screen } from 'expo-router/testing-library';

import { fail, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { MembershipRequestResponse } from '@/types/api/membership';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const ME = '/membership-requests/me';
const POST = 'POST /membership-requests';
const FIELD = '소모임 닉네임';
const none: Reply = { status: 204 };
const request = (over: Partial<MembershipRequestResponse> = {}): MembershipRequestResponse => ({
  id: 3,
  somoimNickname: '별이',
  status: 'PENDING',
  rejectReason: null,
  createdAt: '2026-11-24T02:00:00Z',
  reviewedAt: null,
  ...over,
});

beforeEach(() => startAs('USER'));

it('submits the trimmed nickname from the form shown without a request', async () => {
  let applied = false;
  const server = mockServer({
    [ME]: () => (applied ? ok(request()) : none),
    [POST]: () => {
      applied = true;
      return ok(request());
    },
  });
  await renderApp('/membership');
  await type(FIELD, ' 별이 ');
  await press('인증 신청하기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({ somoimNickname: '별이' });
  expect(screen.getByText('운영진이 확인하고 있어요')).toBeOnTheScreen();
});

it('rejects blank and limits to 50 characters', async () => {
  const server = mockServer({ [ME]: none });
  await renderApp('/membership');
  expect(screen.getByLabelText(FIELD).props.maxLength).toBe(50);
  await type(FIELD, '   ');
  await press('인증 신청하기');
  expect(server.count(POST)).toBe(0);
  expect(screen.getByText('소모임 닉네임을 입력해 주세요.')).toBeOnTheScreen();
});

it('shows pending without a form', async () => {
  mockServer({ [ME]: ok(request()) });
  await renderApp('/membership');
  expect(screen.getByText('운영진이 확인하고 있어요')).toBeOnTheScreen();
  expect(screen.queryByLabelText(FIELD)).toBeNull();
});

it('shows the reject reason and the form again', async () => {
  mockServer({ [ME]: ok(request({ status: 'REJECTED', rejectReason: '닉네임을 찾지 못했어요' })) });
  await renderApp('/membership');
  expect(screen.getByText('닉네임을 찾지 못했어요')).toBeOnTheScreen();
  expect(screen.getByLabelText(FIELD)).toBeOnTheScreen();
});

it('shows the form when a past approval was revoked', async () => {
  mockServer({ [ME]: ok(request({ status: 'APPROVED' })) });
  await renderApp('/membership');
  expect(screen.getByLabelText(FIELD)).toBeOnTheScreen();
});

it('tells a MEMBER that verification is done', async () => {
  await startAs('MEMBER');
  mockServer({});
  await renderApp('/membership');
  expect(screen.getByText('동호회원 인증을 마쳤어요')).toBeOnTheScreen();
});

it('refetches when the server says it is already pending', async () => {
  const server = mockServer({
    [ME]: none,
    [POST]: fail(409, 'MEMBERSHIP_REQUEST_PENDING', '이미 심사 중인 신청이 있습니다.'),
  });
  await renderApp('/membership');
  await type(FIELD, '별이');
  await press('인증 신청하기');
  expect(screen.getByText('이미 심사 중인 신청이 있습니다.')).toBeOnTheScreen();
  expect(server.count('GET ' + ME)).toBe(2);
});

it('updates the role when the server says it is already a member', async () => {
  let done = false;
  mockServer({
    [ME]: none,
    '/users/me': () => ok({ id: 1, nickname: '별이', role: done ? 'MEMBER' : 'USER' }),
    [POST]: () => {
      done = true;
      return fail(409, 'MEMBERSHIP_ALREADY_MEMBER', '이미 동호회원입니다.');
    },
  });
  await renderApp('/membership');
  await type(FIELD, '별이');
  await press('인증 신청하기');
  expect(await screen.findByText('동호회원 인증을 마쳤어요')).toBeOnTheScreen();
});

it('asks a guest to log in', async () => {
  await startAs(null);
  mockServer({});
  await renderApp('/membership');
  expect(screen.getByRole('button', { name: '로그인하기' })).toBeOnTheScreen();
});

it('updates the role by itself when the request was just approved', async () => {
  let me = 0;
  mockServer({
    [ME]: ok(request({ status: 'APPROVED' })),
    '/users/me': () => ok({ id: 1, nickname: '별이', role: me++ === 0 ? 'USER' : 'MEMBER' }),
  });
  await renderApp('/membership');
  expect(await screen.findByText('동호회원 인증을 마쳤어요')).toBeOnTheScreen();
  expect(me).toBe(2);
});
