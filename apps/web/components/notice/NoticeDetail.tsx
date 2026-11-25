'use client';

import Link from 'next/link';

import { Markdown } from '@/components/notice/Markdown';
import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { Reveal } from '@/components/ui/Reveal';
import { buttonClass, textLinkClass } from '@/components/ui/styles';
import { useNotice } from '@/hooks/use-notices';
import { isNotFound } from '@/lib/api-error';
import { formatDate } from '@/lib/kst';

export function NoticeDetail({ id }: { id: number | null }) {
  const query = useNotice(id);
  if (id === null || isNotFound(query.error)) {
    return (
      <Notice
        title="없는 공지예요"
        description="지워졌거나 주소가 잘못된 공지예요."
        action={
          <Link href="/app/notices" className={buttonClass}>
            공지 목록
          </Link>
        }
      />
    );
  }
  return (
    <QueryState query={query}>
      {({ title, body, publishedAt }) => (
        <article>
          <Reveal>
            <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{title}</h1>
          </Reveal>
          <Reveal index={1}>
            <p className="mt-2 text-sm text-muted">{formatDate(publishedAt)}</p>
          </Reveal>
          <Reveal index={2} className="mt-8">
            <Markdown>{body}</Markdown>
          </Reveal>
          <Reveal index={3} className="mt-12">
            <Link href="/app/notices" className={textLinkClass}>
              공지 목록
            </Link>
          </Reveal>
        </article>
      )}
    </QueryState>
  );
}
