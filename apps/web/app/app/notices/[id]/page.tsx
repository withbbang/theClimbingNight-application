import type { Metadata } from 'next';

import { NoticeDetail } from '@/components/notice/NoticeDetail';
import { Page } from '@/components/ui/Page';
import { parseId } from '@/lib/parse-id';

export const metadata: Metadata = { title: '공지' };

export default async function NoticePage({ params }: PageProps<'/app/notices/[id]'>) {
  const { id } = await params;
  return (
    <Page>
      <NoticeDetail id={parseId(id)} />
    </Page>
  );
}
