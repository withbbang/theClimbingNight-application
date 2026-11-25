import { act, fireEvent, screen } from 'expo-router/testing-library';

import { queryClient } from '@/lib/query-client';
import { fail, flush, mockServer, ok, renderApp, type Reply } from '@/test-utils/app';
import { button, press, type } from '@/test-utils/screen';
import { startAs } from '@/test-utils/session';
import type { NoticeResponse } from '@/types/api/notice';

jest.mock('expo-secure-store', () =>
  jest.requireActual('@/test-utils/native-mocks').secureStoreMock(),
);
jest.mock('expo-splash-screen', () => jest.requireActual('@/test-utils/native-mocks').splashMock());
jest.mock('expo-haptics', () => jest.requireActual('@/test-utils/native-mocks').hapticsMock());
jest.mock('@/lib/social', () => jest.requireActual('@/test-utils/native-mocks').socialMock());

const LIST = '/notices?page=0&size=20';
const notice = (over: Partial<NoticeResponse> = {}): NoticeResponse => ({
  id: 1,
  title: '회비 안내',
  body: '**월말**까지',
  pinned: false,
  publishedAt: '2026-11-20T03:00:00Z',
  updatedAt: '2026-11-20T03:00:00Z',
  ...over,
});
const list = (items: NoticeResponse[]) =>
  ok({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 });

beforeEach(() => startAs('ADMIN'));

it('publishes a notice and returns to the refreshed list', async () => {
  let created = false;
  const server = mockServer({
    [LIST]: () => list(created ? [notice()] : []),
    'POST /notices': () => {
      created = true;
      return ok(notice());
    },
  });
  const app = await renderApp('/admin/notices');
  expect(screen.getByText('아직 공지가 없어요')).toBeOnTheScreen();
  await press('새 공지');
  expect(screen.getByText('올리면 회원에게 알림이 가요.')).toBeOnTheScreen();
  await type('제목', ' 회비 안내 ');
  await type('본문 (마크다운)', ' **월말**까지 ');
  await press('올리기');
  expect(server.calls.find((c) => c.method === 'POST')?.body).toEqual({
    title: '회비 안내',
    body: '**월말**까지',
    pinned: false,
  });
  expect(app.getPathname()).toBe('/admin/notices');
  expect(screen.getByText('회비 안내')).toBeOnTheScreen();
});

it('limits the lengths and rejects blanks', async () => {
  const server = mockServer({});
  await renderApp('/admin/notices/new');
  expect(screen.getByLabelText('제목').props.maxLength).toBe(100);
  expect(screen.getByLabelText('본문 (마크다운)').props.maxLength).toBe(10000);
  await type('제목', ' ');
  await press('올리기');
  expect(screen.getByText('제목을 입력해 주세요.')).toBeOnTheScreen();
  expect(screen.getByText('본문을 입력해 주세요.')).toBeOnTheScreen();
  expect(server.count('POST /notices')).toBe(0);
});

it('fills the form from a fresh fetch, keeps typing during a refetch, and updates', async () => {
  queryClient.setQueryData(['notices', 'detail', 1], notice({ title: '옛 제목' }));
  let title = '새 제목';
  const server = mockServer({
    '/notices/1': () => ok(notice({ title })),
    'PUT /notices/1': ok(notice()),
  });
  const app = await renderApp('/admin/notices/1');
  expect(screen.getByLabelText('제목').props.value).toBe('새 제목');
  expect(screen.queryByText('올리면 회원에게 알림이 가요.')).toBeNull();
  await type('제목', '고친 제목');
  title = '남이 고친 제목';
  await act(async () => {
    await queryClient.invalidateQueries({ queryKey: ['notices'] });
  });
  await flush();
  expect(screen.getByLabelText('제목').props.value).toBe('고친 제목');
  await fireEvent.press(screen.getByRole('switch', { name: '맨 위에 고정' }));
  await press('저장');
  expect(server.calls.find((c) => c.method === 'PUT')?.body).toEqual({
    title: '고친 제목',
    body: '**월말**까지',
    pinned: true,
  });
  expect(app.getPathname()).toBe('/admin/notices');
});

it('deletes after confirming and returns to the list without asking for it again', async () => {
  let deleted = false;
  const server = mockServer({
    [LIST]: () => list(deleted ? [] : [notice()]),
    '/notices/1': () => (deleted ? fail(404, 'COMMON_NOT_FOUND', '없음') : ok(notice())),
    'DELETE /notices/1': () => {
      deleted = true;
      return { status: 204 };
    },
  });
  const app = await renderApp('/admin/notices');
  await press('회비 안내, 11월 20일(금) 12:00');
  await press('공지 삭제');
  expect(server.count('DELETE /notices/1')).toBe(0);
  await press('공지 삭제 확정');
  expect(app.getPathname()).toBe('/admin/notices');
  expect(screen.getByText('아직 공지가 없어요')).toBeOnTheScreen();
  expect(server.count('GET /notices/1')).toBe(1);
});

it('keeps the text when saving fails', async () => {
  mockServer({ 'POST /notices': fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') });
  await renderApp('/admin/notices/new');
  await type('제목', '회비');
  await type('본문 (마크다운)', '내용');
  await press('올리기');
  expect(screen.getByText('서버 오류')).toBeOnTheScreen();
  expect(screen.getByLabelText('본문 (마크다운)').props.value).toBe('내용');
});

it('shows not found for a bad id without a request', async () => {
  const server = mockServer({});
  await renderApp('/admin/notices/abc');
  expect(screen.getByText('공지를 찾을 수 없어요')).toBeOnTheScreen();
  expect(server.calls.some((c) => c.path.startsWith('/notices/'))).toBe(false);
});

it('shows not found on 404', async () => {
  mockServer({ '/notices/1': fail(404, 'COMMON_NOT_FOUND', '없음') });
  await renderApp('/admin/notices/1');
  expect(screen.getByText('공지를 찾을 수 없어요')).toBeOnTheScreen();
  expect(button('저장')).toBeNull();
});

it('cannot delete while saving', async () => {
  let finish = (_: Reply) => {};
  mockServer({
    '/notices/1': ok(notice()),
    'PUT /notices/1': () => new Promise<Reply>((resolve) => (finish = resolve)),
  });
  await renderApp('/admin/notices/1');
  await press('공지 삭제');
  await press('저장');
  expect(button('공지 삭제 확정')).toBeDisabled();
  await act(async () => finish(fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류')));
  await flush();
  expect(button('공지 삭제 확정')).toBeEnabled();
});
