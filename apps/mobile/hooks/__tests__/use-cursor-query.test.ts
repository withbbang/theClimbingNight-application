import { createElement, type ReactNode } from 'react';

import { act, renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { useCursorQuery } from '@/hooks/use-cursor-query';
import type { CursorResponse } from '@/types/api/common';

type Item = { id: number; name: string };

let client: QueryClient;
afterEach(() => client.clear());

function setup(pages: Record<string, CursorResponse<Item>>) {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const fetchPage = jest.fn(async (cursor: string | null) => pages[cursor ?? 'first']);
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return { fetchPage, wrapper };
}

it('stops when nextCursor is null', async () => {
  // given
  const { fetchPage, wrapper } = setup({
    first: { items: [{ id: 1, name: '하나' }], nextCursor: 'c1' },
    c1: { items: [{ id: 2, name: '둘' }], nextCursor: null },
  });
  const { result } = await renderHook(() => useCursorQuery(['t'], fetchPage, true), { wrapper });
  await waitFor(() => expect(result.current.data).toHaveLength(1));
  expect(result.current.hasNextPage).toBe(true);

  // when
  await act(async () => {
    await result.current.fetchNextPage();
  });

  // then
  expect(fetchPage).toHaveBeenLastCalledWith('c1');
  await waitFor(() => expect(result.current.data?.map((item) => item.id)).toEqual([1, 2]));
  expect(result.current.hasNextPage).toBe(false);
});

it('drops duplicated ids across pages', async () => {
  // given: 받는 사이 새 글이 올라와 앞 페이지의 마지막 글이 다음 페이지에 다시 온다.
  const { fetchPage, wrapper } = setup({
    first: {
      items: [
        { id: 3, name: '셋' },
        { id: 2, name: '둘' },
      ],
      nextCursor: 'c1',
    },
    c1: {
      items: [
        { id: 2, name: '둘' },
        { id: 1, name: '하나' },
      ],
      nextCursor: null,
    },
  });
  const { result } = await renderHook(() => useCursorQuery(['t'], fetchPage, true), { wrapper });
  await waitFor(() => expect(result.current.data).toHaveLength(2));

  // when
  await act(async () => {
    await result.current.fetchNextPage();
  });

  // then
  await waitFor(() => expect(result.current.data?.map((item) => item.id)).toEqual([3, 2, 1]));
});
