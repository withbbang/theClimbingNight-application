import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { fail, flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { InquiryAdminResponse } from '@/types/api/inquiry';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = (status: string) => `/inquiries?status=${status}&page=0&size=20`;
const inquiry = (over: Partial<InquiryAdminResponse> = {}): InquiryAdminResponse => ({
  id: 1,
  title: '회비 문의',
  body: '언제까지 내나요',
  status: 'OPEN',
  answer: null,
  answeredAt: null,
  createdAt: '2026-11-24T02:00:00Z',
  userId: 21,
  nickname: '별이',
  ...over,
});
const answered = (over: Partial<InquiryAdminResponse> = {}) =>
  inquiry({ status: 'ANSWERED', answer: '월말', answeredAt: '2026-11-24T02:30:00Z', ...over });
const list = (items: InquiryAdminResponse[]) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });

beforeEach(() => startAs('ADMIN'));

it('starts with unanswered inquiries and switches to answered', async () => {
  mockServer({
    [LIST('OPEN')]: list([inquiry({ nickname: null })]),
    [LIST('ANSWERED')]: list([answered({ id: 2, title: '답한 문의' })]),
  });
  await renderApp('/admin/inquiries');
  expect(screen.getByText('회비 문의')).toBeOnTheScreen();
  expect(screen.getByText(/탈퇴한 회원/)).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole('tab', { name: '답변 완료' }));
  await flush();
  expect(screen.getByText('답한 문의')).toBeOnTheScreen();
  expect(screen.queryByText('회비 문의')).toBeNull();
});

it('answers with trimmed text and returns to the refreshed list', async () => {
  let done = false;
  const server = mockServer({
    [LIST('OPEN')]: () => list(done ? [] : [inquiry()]),
    '/inquiries/1': ok(inquiry()),
    'PUT /inquiries/1/answer': () => {
      done = true;
      return ok(answered());
    },
  });
  const app = await renderApp('/admin/inquiries');
  await press('회비 문의, 별이, 11월 24일(화) 11:00');
  expect(screen.getByText('언제까지 내나요')).toBeOnTheScreen();
  await type('답변', ' 월말까지예요 ');
  await press('답변 보내기');
  expect(server.calls.find((c) => c.method === 'PUT')?.body).toEqual({ answer: '월말까지예요' });
  expect(app.getPathname()).toBe('/admin/inquiries');
  expect(screen.getByText('답변을 기다리는 문의가 없어요')).toBeOnTheScreen();
});

it('says no push is sent again when editing an answer', async () => {
  mockServer({ '/inquiries/1': ok(answered()) });
  await renderApp('/admin/inquiries/1');
  expect(screen.getByText('답변을 고쳐도 알림은 다시 가지 않아요.')).toBeOnTheScreen();
  expect(screen.getByLabelText('답변').props.value).toBe('월말');
});

it('limits to 2000 and rejects blanks', async () => {
  const server = mockServer({ '/inquiries/1': ok(inquiry()) });
  await renderApp('/admin/inquiries/1');
  expect(screen.getByLabelText('답변').props.maxLength).toBe(2000);
  await type('답변', ' ');
  await press('답변 보내기');
  expect(screen.getByText('답변을 입력해 주세요.')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.method === 'PUT')).toBe(false);
});

it('keeps the text when sending fails', async () => {
  mockServer({
    '/inquiries/1': ok(inquiry()),
    'PUT /inquiries/1/answer': fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류'),
  });
  await renderApp('/admin/inquiries/1');
  await type('답변', '월말');
  await press('답변 보내기');
  expect(screen.getByText('서버 오류')).toBeOnTheScreen();
  expect(screen.getByLabelText('답변').props.value).toBe('월말');
});

it('starts from a fresh answer and keeps typing during a refetch', async () => {
  queryClient.setQueryData(['inquiries', 'admin', 'detail', 1], inquiry());
  mockServer({ '/inquiries/1': ok(answered()) });
  await renderApp('/admin/inquiries/1');
  expect(screen.getByLabelText('답변').props.value).toBe('월말');
  await type('답변', '고치는 중');
  await act(async () => {
    await queryClient.invalidateQueries({ queryKey: ['inquiries'] });
  });
  await flush();
  expect(screen.getByLabelText('답변').props.value).toBe('고치는 중');
});

it('shows not found for a bad id without a request', async () => {
  const server = mockServer({});
  await renderApp('/admin/inquiries/abc');
  expect(screen.getByText('문의를 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/inquiries'))).toBe(false);
});

it('shows not found on 404', async () => {
  mockServer({ '/inquiries/1': fail(404, 'COMMON_NOT_FOUND', '없음') });
  await renderApp('/admin/inquiries/1');
  expect(screen.getByText('문의를 찾을 수 없어요')).toBeOnTheScreen();
});
