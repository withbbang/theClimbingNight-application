import Link from 'next/link';

import { Notice } from '@/components/ui/Notice';
import { Page } from '@/components/ui/Page';
import { buttonClass } from '@/components/ui/styles';

export default function NotFound() {
  return (
    <Page>
      <Notice
        title="없는 페이지예요"
        description="주소가 바뀌었거나 지워진 페이지예요."
        action={
          <Link href="/app/notices" className={buttonClass}>
            공지 보기
          </Link>
        }
      />
    </Page>
  );
}
