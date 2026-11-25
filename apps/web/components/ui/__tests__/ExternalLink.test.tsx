import { render, screen } from '@testing-library/react';

import { ExternalLink } from '@/components/ui/ExternalLink';

it('opens outside without handing over the page', () => {
  render(<ExternalLink href="https://store.test">스마트스토어</ExternalLink>);

  const link = screen.getByRole('link', { name: '스마트스토어' });
  expect(link).toHaveAttribute('href', 'https://store.test');
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', 'noopener noreferrer');
});
