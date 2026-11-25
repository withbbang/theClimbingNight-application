import type { Metadata } from 'next';

import { LegalPage } from '@/components/legal/LegalPage';
import { PRIVACY } from '@/content/privacy';

export const metadata: Metadata = { title: '개인정보처리방침' };

export default function Legal() {
  return <LegalPage title="개인정보처리방침" {...PRIVACY} />;
}
