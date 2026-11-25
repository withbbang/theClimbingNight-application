import type { ReactNode } from 'react';

import { Reveal } from '@/components/ui/Reveal';

interface PageProps {
  title?: string;
  children: ReactNode;
}

export function Page({ title, children }: PageProps) {
  return (
    <main className="mx-auto w-full max-w-2xl px-6 pt-6 pb-16 sm:pt-14">
      {title ? (
        <Reveal className="mb-8">
          <h1 className="text-3xl font-extrabold tracking-tight">{title}</h1>
        </Reveal>
      ) : null}
      {children}
    </main>
  );
}
