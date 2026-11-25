import { fireEvent, screen, waitFor } from '@testing-library/react';

import { Goods } from '@/components/goods/Goods';
import { fail, mockServer, ok, renderWithQuery } from '@/test-utils/server';

const settings = (smartstoreUrl: string | null) => ok({ somoimJoinUrl: null, smartstoreUrl });
const item = {
  name: '초크백',
  description: '허리에 매는 초크백',
  image: '/goods/chalk-bag.webp',
  alt: '남색 초크백',
};

it('lists goods with a store link', async () => {
  mockServer({ '/app-settings': settings('https://store.test') });
  renderWithQuery(<Goods items={[item]} />);

  expect(screen.getByRole('heading', { name: '초크백' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '남색 초크백' })).toBeInTheDocument();
  const link = await screen.findByRole('link', { name: '스마트스토어에서 구매하기' });
  expect(link).toHaveAttribute('href', 'https://store.test');
});

it('says goods are being prepared when there are none', async () => {
  mockServer({ '/app-settings': settings(null) });
  renderWithQuery(<Goods items={[]} />);

  expect(screen.getByText('굿즈를 준비하고 있어요')).toBeInTheDocument();
});

it('keeps the store link when there are no goods', async () => {
  mockServer({ '/app-settings': settings('https://store.test') });
  renderWithQuery(<Goods items={[]} />);

  expect(await screen.findByRole('link', { name: '스마트스토어 보기' })).toBeInTheDocument();
});

it('hides the store link when there is no URL', async () => {
  mockServer({ '/app-settings': settings(null) });
  const { client } = renderWithQuery(<Goods items={[item]} />);

  await waitFor(() => expect(client.getQueryState(['app-settings'])?.fetchStatus).toBe('idle'));
  expect(screen.getByText('초크백')).toBeInTheDocument();
  expect(screen.queryByRole('link')).toBeNull();
  expect(screen.queryByRole('button')).toBeNull();
});

it('offers a retry for the link when settings fail', async () => {
  let down = true;
  mockServer({
    '/app-settings': () =>
      down ? fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') : settings('https://store.test'),
  });
  renderWithQuery(<Goods items={[item]} />);
  expect(await screen.findByText('스토어 링크를 불러오지 못했어요.')).toBeInTheDocument();

  down = false;
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  const link = await screen.findByRole('link', { name: '스마트스토어에서 구매하기' });
  expect(link).toHaveAttribute('href', 'https://store.test');
  expect(screen.queryByText('스토어 링크를 불러오지 못했어요.')).toBeNull();
});

it('offers a retry for the link when settings fail and there are no goods', async () => {
  let down = true;
  mockServer({
    '/app-settings': () =>
      down ? fail(500, 'COMMON_INTERNAL_ERROR', '서버 오류') : settings('https://store.test'),
  });
  renderWithQuery(<Goods items={[]} />);
  expect(await screen.findByText('스토어 링크를 불러오지 못했어요.')).toBeInTheDocument();

  down = false;
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

  const link = await screen.findByRole('link', { name: '스마트스토어 보기' });
  expect(link).toHaveAttribute('href', 'https://store.test');
  expect(screen.queryByText('스토어 링크를 불러오지 못했어요.')).toBeNull();
});
