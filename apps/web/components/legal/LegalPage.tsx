import { Markdown } from '@/components/notice/Markdown';
import { Page } from '@/components/ui/Page';
import { Reveal } from '@/components/ui/Reveal';

export interface LegalSection {
  id: string;
  title: string;
  /** 마크다운 */
  body: string;
}

export interface LegalContent {
  effectiveDate: string;
  sections: LegalSection[];
}

export function LegalPage({ title, effectiveDate, sections }: LegalContent & { title: string }) {
  return (
    <Page title={title}>
      <Reveal index={1}>
        <p className="text-muted">시행일: {effectiveDate}</p>
      </Reveal>
      <nav aria-label="목차" className="mt-6">
        <ul className="sm:columns-2 sm:gap-6">
          {sections.map((section) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                className="press inline-flex min-h-11 items-center underline decoration-accent decoration-2 underline-offset-4"
              >
                {section.title}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      {sections.map((section) => (
        <section key={section.id} id={section.id} className="legal-section mt-12 scroll-mt-6">
          <h2 className="text-xl font-extrabold">{section.title}</h2>
          <div className="mt-4">
            <Markdown>{section.body}</Markdown>
          </div>
        </section>
      ))}
    </Page>
  );
}
