import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { NoticeList } from '@/components/notice/NoticeList';
import { fail, mockServer, ok, renderWithQuery, type Reply } from '@/test-utils/server';

const notice = (id: number, pinned = false) => ({
  id,
  title: `공지 ${id}`,
  pinned,
  publishedAt: '2026-11-24T03:00:00Z',
});
const page = (items: object[], number: number, totalPages: number) =>
  ok({ items, page: number, size: 20, totalElements: 40, totalPages });

const first = '/notices?page=0&size=20';
const second = '/notices?page=1&size=20';

it('shows notices with the date and a link', async () => {
  mockServer({ [first]: page([notice(1)], 0, 1) });
  renderWithQuery(<NoticeList />);

  const link = await screen.findByRole('link', { name: /공지 1/ });
  expect(link).toHaveAttribute('href', '/app/notices/1');
  expect(screen.getByText('2026년 11월 24일')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
});

it('shows pinned notices with a label', async () => {
  mockServer({ [first]: page([notice(1, true), notice(2)], 0, 1) });
  renderWithQuery(<NoticeList />);

  expect(await screen.findAllByLabelText('고정된 공지')).toHaveLength(1);
});

it('loads more without repeating a notice', async () => {
  mockServer({
    [first]: page([notice(3), notice(2)], 0, 2),
    [second]: page([notice(2), notice(1)], 1, 2),
  });
  renderWithQuery(<NoticeList />);

  await userEvent.click(await screen.findByRole('button', { name: '더 보기' }));

  expect(await screen.findByText('공지 1')).toBeInTheDocument();
  expect(screen.getAllByText('공지 2')).toHaveLength(1);
  expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
});

it('keeps the list and tells when loading more fails', async () => {
  let reply: Reply = fail(500, 'COMMON_INTERNAL_ERROR', '잠시 후 다시 시도해 주세요.');
  mockServer({ [first]: page([notice(2)], 0, 2), [second]: () => reply });
  renderWithQuery(<NoticeList />);

  await userEvent.click(await screen.findByRole('button', { name: '더 보기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요.');
  expect(screen.getByText('공지 2')).toBeInTheDocument();

  reply = page([notice(1)], 1, 2);
  await userEvent.click(screen.getByRole('button', { name: '더 보기' }));
  expect(await screen.findByText('공지 1')).toBeInTheDocument();
});

it('says there are no notices yet', async () => {
  mockServer({ [first]: page([], 0, 0) });
  renderWithQuery(<NoticeList />);

  expect(await screen.findByText('아직 공지가 없어요')).toBeInTheDocument();
});
