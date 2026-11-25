import type { ComponentPropsWithoutRef } from 'react';

import ReactMarkdown, { defaultUrlTransform, type Components } from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkCjkFriendly from 'remark-cjk-friendly';
import remarkGfm from 'remark-gfm';

import { ExternalLink } from '@/components/ui/ExternalLink';
import { remarkTrimParticles } from '@/lib/remark-trim-particles';

const FOOTNOTE = /^#user-content-fn/;
const LINK = /^(https?:|mailto:|tel:)/i;

function Heading2({ children }: ComponentPropsWithoutRef<'h2'>) {
  return <h2 className="mt-8 text-xl font-extrabold first:mt-0">{children}</h2>;
}

function Heading3({ children, id }: ComponentPropsWithoutRef<'h3'>) {
  return (
    <h3 id={id} className="mt-6 text-lg font-bold first:mt-0">
      {children}
    </h3>
  );
}

function Heading4({ children }: ComponentPropsWithoutRef<'h4'>) {
  return <h4 className="mt-5 font-bold first:mt-0">{children}</h4>;
}

// 페이지 제목이 h1이라 본문 제목은 한 단계씩 내린다.
const components: Components = {
  h1: Heading2,
  h2: Heading3,
  h3: Heading4,
  h4: Heading4,
  h5: Heading4,
  h6: Heading4,
  p: ({ children }) => <p className="mt-4 first:mt-0">{children}</p>,
  ul: ({ children }) => <ul className="mt-4 list-disc pl-6">{children}</ul>,
  ol: ({ children, start }) => (
    <ol start={start} className="mt-4 list-decimal pl-6">
      {children}
    </ol>
  ),
  li: ({ children, id }) => (
    <li id={id} className="mt-1">
      {children}
    </li>
  ),
  blockquote: ({ children }) => (
    <blockquote className="mt-4 border-l-4 border-accent pl-4 text-muted">{children}</blockquote>
  ),
  hr: () => <hr className="my-8 border-hold-off" />,
  pre: ({ children }) => (
    <pre className="mt-4 overflow-x-auto rounded-xl bg-surface p-4">{children}</pre>
  ),
  code: ({ children }) => <code className="rounded bg-surface px-1 text-[0.9em]">{children}</code>,
  table: ({ children }) => (
    <div className="mt-4 overflow-x-auto">
      <table className="w-full border-collapse text-left">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border-b-2 border-border min-w-20 px-3 py-2 font-bold">{children}</th>
  ),
  td: ({ children }) => <td className="min-w-20 border-b border-hold-off px-3 py-2">{children}</td>,
  a: ({ href, id, children }) =>
    href && FOOTNOTE.test(href) ? (
      <a id={id} href={href} className="px-1 font-semibold underline">
        {children}
      </a>
    ) : href && LINK.test(href) ? (
      <ExternalLink
        href={href}
        className="font-semibold underline decoration-accent decoration-2 underline-offset-4"
      >
        {children}
      </ExternalLink>
    ) : (
      <span>{children}</span>
    ),
  img: ({ src, alt }) =>
    typeof src === 'string' && /^https:/i.test(src) ? (
      // 주소를 미리 알 수 없는 외부 이미지라 next/image를 쓰지 않는다.
      // eslint-disable-next-line @next/next/no-img-element
      <img src={src} alt={alt ?? ''} loading="lazy" className="mt-4 max-w-full rounded-card" />
    ) : (
      <span>{alt}</span>
    ),
};

const FOOTNOTES = { footnoteLabel: '각주', footnoteBackLabel: '본문으로 돌아가기' };

function transformUrl(url: string) {
  return /^tel:/i.test(url) ? url : defaultUrlTransform(url);
}

/** 운영진이 쓴 마크다운. HTML은 그리지 않고 글자로 보인다. */
export function Markdown({ children }: { children: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[
        [remarkGfm, { singleTilde: false }],
        remarkCjkFriendly,
        remarkBreaks,
        remarkTrimParticles,
      ]}
      remarkRehypeOptions={FOOTNOTES}
      urlTransform={transformUrl}
      components={components}
    >
      {children}
    </ReactMarkdown>
  );
}
