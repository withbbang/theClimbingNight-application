import { render, screen } from '@testing-library/react';

import { LegalPage } from '@/components/legal/LegalPage';
import { PRIVACY } from '@/content/privacy';
import { TERMS } from '@/content/terms';

it('links each contents entry to its section', () => {
  const { container } = render(<LegalPage title="이용약관" {...TERMS} />);

  for (const { id, title } of TERMS.sections) {
    expect(screen.getByRole('link', { name: title })).toHaveAttribute('href', `#${id}`);
    expect(container.querySelector(`#${id}`)).toContainElement(
      screen.getByRole('heading', { name: title }),
    );
  }
});

it('has the sections the stores need', () => {
  expect(PRIVACY.sections.map(({ id }) => id)).toEqual(
    expect.arrayContaining([
      'collected',
      'purpose',
      'retention',
      'processors',
      'rights',
      'account-deletion',
      'contact',
    ]),
  );
});

it.each([
  ['terms', TERMS],
  ['privacy', PRIVACY],
])('has unique section ids in %s', (_name, { sections }) => {
  const ids = sections.map(({ id }) => id);
  expect(new Set(ids).size).toBe(ids.length);
});
