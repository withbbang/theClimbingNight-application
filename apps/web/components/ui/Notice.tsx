import type { ReactNode } from 'react';

import { Reveal } from '@/components/ui/Reveal';

interface NoticeProps {
  title: string;
  description: string;
  action?: ReactNode;
}

/** 빈 화면·안내 화면. 무엇이 없고 무엇을 하면 되는지 알린다. */
export function Notice({ title, description, action }: NoticeProps) {
  return (
    <div className="flex flex-col items-start gap-3 py-4">
      <Reveal>
        <span aria-hidden className="hold sway block h-8 w-12" />
      </Reveal>
      <Reveal index={1}>
        <h2 className="text-xl font-extrabold">{title}</h2>
      </Reveal>
      <Reveal index={2}>
        <p className="text-muted">{description}</p>
      </Reveal>
      {action ? (
        <Reveal index={3} className="mt-2">
          {action}
        </Reveal>
      ) : null}
    </div>
  );
}
