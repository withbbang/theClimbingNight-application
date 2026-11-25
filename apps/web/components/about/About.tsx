'use client';

import { Wall } from '@/components/about/Wall';
import { ExternalLink } from '@/components/ui/ExternalLink';
import { Reveal } from '@/components/ui/Reveal';
import { buttonClass } from '@/components/ui/styles';
import { ABOUT } from '@/content/about';
import { useAppSettings } from '@/hooks/use-app-settings';

export function About() {
  const joinUrl = useAppSettings().data?.somoimJoinUrl;
  return (
    <>
      <Wall />
      <Reveal className="mt-6">
        <h1 className="text-4xl font-extrabold tracking-tight">벽헤는밤</h1>
      </Reveal>
      <Reveal index={1}>
        <p className="mt-2 text-lg text-muted">{ABOUT.lead}</p>
      </Reveal>
      <div className="mt-10 flex flex-col gap-4">
        {ABOUT.paragraphs.map((text, i) => (
          <Reveal key={text} index={i + 2}>
            <p>{text}</p>
          </Reveal>
        ))}
      </div>
      <section className="mt-14">
        <h2 className="rise text-2xl font-extrabold tracking-tight">함께하는 방법</h2>
        <ol className="mt-6">
          {ABOUT.steps.map(({ title, body }) => (
            <li key={title} className="rise group relative pb-8 pl-10 last:pb-0">
              <span
                aria-hidden
                className="absolute top-6 bottom-0 left-[0.55rem] w-0.5 bg-hold-off group-last:hidden"
              />
              <span aria-hidden className="step-hold hold absolute top-1.5 left-0 size-5" />
              <h3 className="text-lg font-bold">{title}</h3>
              <p className="text-muted">{body}</p>
            </li>
          ))}
        </ol>
      </section>
      {joinUrl ? (
        <Reveal className="mt-12">
          <ExternalLink href={joinUrl} className={buttonClass}>
            소모임에서 가입하기
          </ExternalLink>
        </Reveal>
      ) : null}
    </>
  );
}
