import { useQuery } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { QueryState } from '@/components/ui/QueryState';
import { apiFetch } from '@/lib/api';
import { fail, mockServer, ok, renderWithQuery, type Reply } from '@/test-utils/server';

function Sample() {
  const query = useQuery({ queryKey: ['x'], queryFn: () => apiFetch<{ name: string }>('/x') });
  return <QueryState query={query}>{({ name }) => <p>{name}</p>}</QueryState>;
}

const failure = fail(500, 'COMMON_INTERNAL_ERROR', '잠시 후 다시 시도해 주세요.');

it('announces loading', () => {
  mockServer({ '/x': ok({ name: '공지' }) });
  renderWithQuery(<Sample />);

  expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument();
});

it('retries from the error state', async () => {
  let reply: Reply = failure;
  mockServer({ '/x': () => reply });
  renderWithQuery(<Sample />);
  expect(await screen.findByText('잠시 후 다시 시도해 주세요.')).toBeInTheDocument();

  reply = ok({ name: '공지' });
  await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  expect(await screen.findByText('공지')).toBeInTheDocument();
});

it('keeps data when a refetch fails', async () => {
  let reply: Reply = ok({ name: '공지' });
  mockServer({ '/x': () => reply });
  const { client } = renderWithQuery(<Sample />);
  await screen.findByText('공지');

  reply = failure;
  await client.invalidateQueries({ queryKey: ['x'] });

  await waitFor(() => expect(client.getQueryState(['x'])?.status).toBe('error'));
  expect(screen.getByText('공지')).toBeInTheDocument();
});
