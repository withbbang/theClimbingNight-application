'use client';

import { ExternalLink } from '@/components/ui/ExternalLink';
import { buttonClass, textLinkClass } from '@/components/ui/styles';
import type { useAppSettings } from '@/hooks/use-app-settings';

interface SettingLinkProps {
  query: ReturnType<typeof useAppSettings>;
  url: string | null | undefined;
  label: string;
  failedMessage: string;
}

/** 받은 설정 없이 조회가 실패했는지. 다시 받는 동안에도 참이라 안내가 깜빡이지 않는다. */
export function settingsFailed(query: ReturnType<typeof useAppSettings>) {
  return query.data === undefined && query.errorUpdateCount > 0;
}

/** 앱 설정에 적힌 주소로 가는 버튼. 조회에 실패하면 그 자리에서 다시 시도하게 한다. */
export function SettingLink({ query, url, label, failedMessage }: SettingLinkProps) {
  if (settingsFailed(query)) {
    return (
      <div className="flex flex-col items-start">
        <p role="status" className="text-muted">
          {failedMessage}
        </p>
        <button type="button" className={textLinkClass} onClick={() => void query.refetch()}>
          {query.isFetching ? '불러오는 중' : '다시 시도'}
        </button>
      </div>
    );
  }
  return url ? (
    <ExternalLink href={url} className={buttonClass}>
      {label}
    </ExternalLink>
  ) : null;
}
