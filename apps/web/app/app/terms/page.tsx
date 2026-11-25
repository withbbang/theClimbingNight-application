import type { Metadata } from 'next';

import { LegalPage } from '@/components/legal/LegalPage';
import { TERMS } from '@/content/terms';

export const metadata: Metadata = { title: '이용약관' };

export default function Legal() {
  return <LegalPage title="이용약관" {...TERMS} />;
}
