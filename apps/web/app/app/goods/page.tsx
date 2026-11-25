import type { Metadata } from 'next';

import { Goods } from '@/components/goods/Goods';
import { Page } from '@/components/ui/Page';
import { GOODS } from '@/content/goods';

export const metadata: Metadata = { title: '굿즈' };

export default function GoodsPage() {
  return (
    <Page title="굿즈">
      <Goods items={GOODS} />
    </Page>
  );
}
