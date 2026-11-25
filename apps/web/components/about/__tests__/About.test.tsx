import { fireEvent, screen, waitFor } from '@testing-library/react';

import { About } from '@/components/about/About';
import { fail, mockServer, ok, renderWithQuery } from '@/test-utils/server';

const settings = (somoimJoinUrl: string | null) => ok({ somoimJoinUrl, smartstoreUrl: null });

it('links to somoim when the URL is set', async () => {
  mockServer({ '/app-settings': settings('https://somoim.test/join') });
  renderWithQuery(<About />);

  const link = await screen.findByRole('link', { name: '소모임에서 가입하기' });
  expect(link).toHaveAttribute('href', 'https://somoim.test/join');
  expect(link).toHaveAttribute('target', '_blank');
});

it('keeps the story without a join link when there is no URL', async () => {
  mockServer({ '/app-settings': settings(null) });
  const { client } = renderWithQuery(<About />);

  await waitFor(() => expect(client.getQueryState(['app-settings'])?.fetchStatus).toBe('idle'));
  expect(screen.getByRole('heading', { level: 1, name: '벽헤는밤' })).toBeInTheDocument();
  expect(screen.queryByRole('link')).toBeNull();
  expect(screen.queryByRole('button')).toBeNull();
});

it('offers a retry for the link when settings fail', async () => {
  let down = true;
  mockServer({
    '/app-settings': () =>
      down ? fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') : settings('https://somoim.test/join'),
  });
  renderWithQuery(<About />);
  expect(await screen.findByText('가입 링크를 불러오지 못했어요.')).toBeInTheDocument();

  down = false;
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  const link = await screen.findByRole('link', { name: '소모임에서 가입하기' });
  expect(link).toHaveAttribute('href', 'https://somoim.test/join');
  expect(screen.queryByText('가입 링크를 불러오지 못했어요.')).toBeNull();
});

it('keeps the notice and shows progress while retrying', async () => {
  mockServer({ '/app-settings': fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') });
  renderWithQuery(<About />);
  const button = await screen.findByRole('button', { name: '다시 시도' });

  globalThis.fetch = vi.fn(() => new Promise<Response>(() => {}));
  fireEvent.click(button);

  expect(await screen.findByRole('button', { name: '불러오는 중' })).toBe(button);
  expect(screen.getByText('가입 링크를 불러오지 못했어요.')).toBeInTheDocument();
});
