import type { Metadata } from 'next';

import { About } from '@/components/about/About';
import { Page } from '@/components/ui/Page';

export const metadata: Metadata = { title: '동호회 소개' };

export default function AboutPage() {
  return (
    <Page>
      <About />
    </Page>
  );
}
