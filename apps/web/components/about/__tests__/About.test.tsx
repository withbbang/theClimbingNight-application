import { screen, waitFor } from '@testing-library/react';

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

it.each([
  ['there is no URL', settings(null)],
  ['settings fail', fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류')],
])('keeps the story without a join link when %s', async (_name, reply) => {
  mockServer({ '/app-settings': reply });
  const { client } = renderWithQuery(<About />);

  await waitFor(() => expect(client.getQueryState(['app-settings'])?.fetchStatus).toBe('idle'));
  expect(screen.getByRole('heading', { level: 1, name: '벽헤는밤' })).toBeInTheDocument();
  expect(screen.queryByRole('link')).toBeNull();
});
