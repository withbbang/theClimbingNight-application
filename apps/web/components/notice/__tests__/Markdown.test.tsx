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

it('keeps tilde ranges as text', () => {
  const { container } = render(<Markdown>{'토 10~12시, 일 14~16시에 열어요. ~~취소~~'}</Markdown>);

  expect(container).toHaveTextContent('토 10~12시, 일 14~16시에 열어요. 취소');
  expect(container.querySelectorAll('del')).toHaveLength(1);
});

it('shows HTML as text instead of dropping its words', () => {
  render(<Markdown>{'<div>중요한 안내</div>'}</Markdown>);

  expect(screen.getByText(/중요한 안내/)).toBeInTheDocument();
});

it.each([
  ['https://open.kakao.com/o/abc로 오세요', 'https://open.kakao.com/o/abc'],
  ['https://example.com에서 봐요', 'https://example.com/'],
  ['www.example.com으로 오세요', 'http://www.example.com/'],
  ['https://example.com/a에서는 안 돼요', 'https://example.com/a'],
])('leaves Korean particles out of the link: %s', (text, href) => {
  const { container } = render(<Markdown>{text}</Markdown>);

  expect((screen.getByRole('link') as HTMLAnchorElement).href).toBe(href);
  expect(container).toHaveTextContent(text);
});

it('keeps Korean words that belong to the address', () => {
  render(<Markdown>{'https://namu.wiki/w/클라이밍 참고'}</Markdown>);

  expect(screen.getByRole('link')).toHaveTextContent('https://namu.wiki/w/클라이밍');
});

it('bolds a phrase that ends with punctuation before a particle', () => {
  const { container } = render(<Markdown>{'**벙(정기)**은 토요일'}</Markdown>);

  expect(container.querySelector('strong')).toHaveTextContent('벙(정기)');
});

it('links footnotes both ways', () => {
  const { container } = render(<Markdown>{'본문[^1]\n\n[^1]: 각주 내용'}</Markdown>);

  const [down, up] = screen.getAllByRole('link');
  for (const link of [down, up]) {
    const target = link?.getAttribute('href')?.slice(1) ?? '';
    expect(container.querySelector(`[id="${target}"]`)).not.toBeNull();
  }
  expect(screen.getByRole('heading', { name: '각주' })).toBeInTheDocument();
});
