'use client';

import Link from 'next/link';

import { PushPinIcon } from '@phosphor-icons/react/dist/csr/PushPin';

import { Notice } from '@/components/ui/Notice';
import { PulseHolds } from '@/components/ui/PulseHolds';
import { QueryState } from '@/components/ui/QueryState';
import { Reveal } from '@/components/ui/Reveal';
import { buttonClass } from '@/components/ui/styles';
import { useNotices } from '@/hooks/use-notices';
import { errorMessage } from '@/lib/error-message';
import { formatDate } from '@/lib/kst';

export function NoticeList() {
  const query = useNotices();
  return (
    <QueryState query={query}>
      {(notices) =>
        notices.length === 0 ? (
          <Notice
            title="아직 공지가 없어요"
            description="새 공지가 올라오면 이곳에서 볼 수 있어요."
          />
        ) : (
          <>
            <ul className="flex flex-col gap-2">
              {notices.map(({ id, title, pinned, publishedAt }, i) => (
                <li key={id}>
                  <Reveal index={i % 20}>
                    <Link
                      href={`/app/notices/${id}`}
                      className="press notice-row -mx-4 flex items-start gap-4 rounded-card px-4 py-3"
                    >
                      <span aria-hidden className="notice-hold mt-2 size-3 shrink-0" />
                      <span className="min-w-0 flex-1">
                        <span className="block font-semibold">{title}</span>
                        <span className="block text-sm text-muted">{formatDate(publishedAt)}</span>
                      </span>
                      {pinned ? (
                        <span role="img" aria-label="고정된 공지" className="pin mt-1 text-muted">
                          <PushPinIcon size={20} weight="fill" aria-hidden />
                        </span>
                      ) : null}
                    </Link>
                  </Reveal>
                </li>
              ))}
            </ul>
            {query.isFetchNextPageError ? (
              <p role="alert" className="mt-6">
                {errorMessage(query.error)}
              </p>
            ) : null}
            {query.isFetchingNextPage ? (
              <PulseHolds />
            ) : query.hasNextPage ? (
              <button
                type="button"
                className={`${buttonClass} mt-6`}
                onClick={() => void query.fetchNextPage()}
              >
                더 보기
              </button>
            ) : null}
          </>
        )
      }
    </QueryState>
  );
}
