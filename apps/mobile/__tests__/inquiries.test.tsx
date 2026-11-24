import { fireEvent, screen } from 'expo-router/testing-library';

import { fail, flush, mockServer, ok, renderApp } from '@/test-utils/app';
import { press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { InquiryResponse } from '@/types/api/inquiry';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const inquiry = (over: Partial<InquiryResponse> = {}): InquiryResponse => ({
  id: 1,
  title: '회비 문의',
  body: '언제까지 내나요',
  status: 'OPEN',
  answer: null,
  answeredAt: null,
  createdAt: '2026-11-24T02:00:00Z',
  ...over,
});
const page = (n: number, items: InquiryResponse[], totalPages: number) =>
  ok({ items, page: n, size: 20, totalElements: items.length, totalPages });
const PAGE = (n: number) => `/inquiries/me?page=${n}&size=20`;
const POST = 'POST /inquiries';

beforeEach(() => startAs('USER'));

it('loads the next page until the last one, showing an item once', async () => {
  const server = mockServer({
    [PAGE(0)]: page(0, [inquiry({ id: 2, title: '둘' }), inquiry({ id: 1, title: '하나' })], 2),
    [PAGE(1)]: page(1, [inquiry({ id: 1, title: '하나' }), inquiry({ id: 3, title: '셋' })], 2),
  });
  await renderApp('/inquiries');
  await fireEvent(screen.getByLabelText('문의 목록'), 'endReached');
  await flush();
  expect(screen.getByText('셋')).toBeOnTheScreen();
  expect(screen.getAllByText('하나')).toHaveLength(1);
  await fireEvent(screen.getByLabelText('문의 목록'), 'endReached');
  await flush();
  expect(server.calls.filter((c) => c.path.startsWith('/inquiries')).map((c) => c.path)).toEqual([
    PAGE(0),
    PAGE(1),
  ]);
});

it('shows an empty notice', async () => {
  mockServer({ [PAGE(0)]: page(0, [], 0) });
  await renderApp('/inquiries');
  expect(screen.getByText('아직 문의가 없어요')).toBeOnTheScreen();
});

it('creates an inquiry and returns to the refreshed list', async () => {
  let created = false;
  const server = mockServer({
    [PAGE(0)]: () => page(0, created ? [inquiry()] : [], 1),
    [POST]: () => {
      created = true;
      return ok(inquiry());
    },
  });
  const app = await renderApp('/inquiries');
  await press('문의하기');
  await type('제목', ' 회비 문의 ');
  await type('내용', ' 언제까지 내나요 ');
  await press('보내기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
    title: '회비 문의',
    body: '언제까지 내나요',
  });
  expect(app.getPathname()).toBe('/inquiries');
  expect(screen.getByText('회비 문의')).toBeOnTheScreen();
});

it('limits the lengths and rejects blanks', async () => {
  const server = mockServer({});
  await renderApp('/inquiries/new');
  expect(screen.getByLabelText('제목').props.maxLength).toBe(100);
  expect(screen.getByLabelText('내용').props.maxLength).toBe(2000);
  await type('제목', ' ');
  await press('보내기');
  expect(screen.getByText('제목을 입력해 주세요.')).toBeOnTheScreen();
  expect(screen.getByText('내용을 입력해 주세요.')).toBeOnTheScreen();
  expect(server.count(POST)).toBe(0);
});

it('keeps the text when sending fails', async () => {
  mockServer({ [POST]: fail(500, 'COMMON_INTERNAL_ERROR', '서버에 문제가 있습니다.') });
  await renderApp('/inquiries/new');
  await type('제목', '회비');
  await type('내용', '언제');
  await press('보내기');
  expect(screen.getByText('서버에 문제가 있습니다.')).toBeOnTheScreen();
  expect(screen.getByLabelText('내용').props.value).toBe('언제');
});

it('shows that the answer is awaited', async () => {
  mockServer({ '/inquiries/me/1': ok(inquiry()) });
  await renderApp('/inquiries/1');
  expect(screen.getByText('답변을 기다리고 있어요')).toBeOnTheScreen();
});

it('shows the answer', async () => {
  mockServer({
    '/inquiries/me/1': ok(
      inquiry({ status: 'ANSWERED', answer: '월말까지예요', answeredAt: '2026-11-24T02:30:00Z' }),
    ),
  });
  await renderApp('/inquiries/1');
  expect(screen.getByText('월말까지예요')).toBeOnTheScreen();
});

it('shows not found for a bad id without calling the API', async () => {
  const server = mockServer({});
  await renderApp('/inquiries/abc');
  expect(screen.getByText('문의를 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/inquiries'))).toBe(false);
});

it('shows not found on 404', async () => {
  mockServer({
    '/inquiries/me/1': fail(404, 'COMMON_NOT_FOUND', '요청한 항목을 찾을 수 없습니다.'),
  });
  await renderApp('/inquiries/1');
  expect(screen.getByText('문의를 찾을 수 없어요')).toBeOnTheScreen();
});
