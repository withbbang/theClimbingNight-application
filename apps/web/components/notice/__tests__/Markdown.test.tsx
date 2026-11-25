import { render, screen } from '@testing-library/react';

import { Markdown } from '@/components/notice/Markdown';

it('does not render raw HTML', () => {
  const { container } = render(
    <Markdown>{'<img src=x onerror="alert(1)">\n\n<script>alert(1)</script>\n\n본문'}</Markdown>,
  );

  expect(container.querySelector('img, script')).toBeNull();
  expect(screen.getByText('본문')).toBeInTheDocument();
});

it.each(['javascript:alert(1)', 'data:text/html,x', 'intent://x'])('drops %s links', (url) => {
  render(<Markdown>{`[누르기](${url})`}</Markdown>);

  expect(screen.getByText('누르기')).toBeInTheDocument();
  expect(screen.queryByRole('link')).toBeNull();
});

it('keeps single line breaks', () => {
  const { container } = render(<Markdown>{'첫 줄\n둘째 줄'}</Markdown>);

  expect(container.querySelectorAll('br')).toHaveLength(1);
});

it('opens links outside', () => {
  render(<Markdown>{'https://example.com/a'}</Markdown>);

  expect(screen.getByRole('link')).toHaveAttribute('target', '_blank');
});

it('keeps mail links', () => {
  render(<Markdown>{'[메일](mailto:a@b.c)'}</Markdown>);

  expect(screen.getByRole('link', { name: '메일' })).toHaveAttribute('href', 'mailto:a@b.c');
});

it('shows only https images', () => {
  render(<Markdown>{'![벽](https://img.test/a.png)\n\n![홀드](http://img.test/b.png)'}</Markdown>);

  expect(screen.getByRole('img', { name: '벽' })).toBeInTheDocument();
  expect(screen.queryByRole('img', { name: '홀드' })).toBeNull();
  expect(screen.getByText('홀드')).toBeInTheDocument();
});

it('lowers headings below the page title', () => {
  render(<Markdown>{'# 큰 제목\n\n## 작은 제목'}</Markdown>);

  expect(screen.getByRole('heading', { level: 2, name: '큰 제목' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { level: 3, name: '작은 제목' })).toBeInTheDocument();
});

it('renders tables', () => {
  render(<Markdown>{'| 날 | 암장 |\n|---|---|\n| 화 | 서울 |'}</Markdown>);

  expect(screen.getByRole('cell', { name: '서울' })).toBeInTheDocument();
});
