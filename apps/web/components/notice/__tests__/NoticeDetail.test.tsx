import { screen } from '@testing-library/react';

import { NoticeDetail } from '@/components/notice/NoticeDetail';
import { parseId } from '@/lib/parse-id';
import { fail, mockServer, ok, renderWithQuery } from '@/test-utils/server';

const notice = {
  id: 1,
  title: '정기 벙 안내',
  body: '**토요일**에 만나요',
  pinned: false,
  publishedAt: '2026-11-24T03:00:00Z',
  updatedAt: '2026-11-24T03:00:00Z',
};

it('shows the notice', async () => {
  mockServer({ '/notices/1': ok(notice) });
  renderWithQuery(<NoticeDetail id={1} />);

  expect(
    await screen.findByRole('heading', { level: 1, name: '정기 벙 안내' }),
  ).toBeInTheDocument();
  expect(screen.getByText('토요일').tagName).toBe('STRONG');
  expect(screen.getByText('2026년 11월 24일')).toBeInTheDocument();
});

it('offers the list when the notice is gone', async () => {
  mockServer({ '/notices/1': fail(404, 'COMMON_NOT_FOUND', '요청한 항목을 찾을 수 없습니다.') });
  renderWithQuery(<NoticeDetail id={1} />);

  expect(await screen.findByText('없는 공지예요')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '공지 목록' })).toHaveAttribute('href', '/app/notices');
});

it('drops a notice deleted while it was open', async () => {
  let gone = false;
  mockServer({
    '/notices/1': () => (gone ? fail(404, 'COMMON_NOT_FOUND', '없음') : ok(notice)),
  });
  const { client } = renderWithQuery(<NoticeDetail id={1} />);
  await screen.findByText('정기 벙 안내');

  gone = true;
  await client.invalidateQueries();

  expect(await screen.findByText('없는 공지예요')).toBeInTheDocument();
});

it('offers a retry on a server error', async () => {
  mockServer({ '/notices/1': fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류입니다.') });
  renderWithQuery(<NoticeDetail id={1} />);

  expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();
});

it.each(['abc', '0', '-1', '1.5', '9007199254740993'])(
  'does not call the API for the bad id %s',
  (raw) => {
    const { calls } = mockServer({});
    renderWithQuery(<NoticeDetail id={parseId(raw)} />);

    expect(screen.getByText('없는 공지예요')).toBeInTheDocument();
    expect(calls).toEqual([]);
  },
);

it('offers a retry when a 404 did not come from the API', async () => {
  mockServer({ '/notices/1': { status: 404 } });
  renderWithQuery(<NoticeDetail id={1} />);

  expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  expect(screen.queryByText('없는 공지예요')).toBeNull();
});
