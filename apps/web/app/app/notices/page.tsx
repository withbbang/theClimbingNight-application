import type { Metadata } from 'next';

import { NoticeList } from '@/components/notice/NoticeList';
import { Page } from '@/components/ui/Page';

export const metadata: Metadata = { title: '공지' };

export default function NoticesPage() {
  return (
    <Page title="공지">
      <NoticeList />
    </Page>
  );
}
