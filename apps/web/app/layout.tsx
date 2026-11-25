import type { ReactNode } from 'react';

import type { Metadata, Viewport } from 'next';

import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css';

import { Providers } from './providers';

import './globals.css';

export const metadata: Metadata = {
  title: { default: '벽헤는밤', template: '%s | 벽헤는밤' },
  description: '벽헤는밤 클라이밍 동호회',
};

export const viewport: Viewport = {
  colorScheme: 'light dark',
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#EEF2F8' },
    { media: '(prefers-color-scheme: dark)', color: '#101A2E' },
  ],
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
