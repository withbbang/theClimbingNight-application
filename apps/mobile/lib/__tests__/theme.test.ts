import { contrastRatio, darkTheme, lightTheme } from '@/lib/theme';

it('computes the WCAG contrast ratio', () => {
  expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 5);
  expect(contrastRatio('#FFFFFF', '#000000')).toBeCloseTo(21, 5);
  expect(contrastRatio('#18243D', '#18243D')).toBe(1);
});

describe.each([
  ['light', lightTheme],
  ['dark', darkTheme],
])('%s theme', (_name, { colors }) => {
  it.each(['text', 'textMuted', 'danger'] as const)('meets 4.5:1 for %s', (tone) => {
    expect(contrastRatio(colors[tone], colors.background)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(colors[tone], colors.surface)).toBeGreaterThanOrEqual(4.5);
  });

  it('meets 4.5:1 for labels on the accent', () => {
    expect(contrastRatio(colors.onAccent, colors.accent)).toBeGreaterThanOrEqual(4.5);
  });

  it('meets 3:1 for control borders', () => {
    expect(contrastRatio(colors.border, colors.surface)).toBeGreaterThanOrEqual(3);
  });
});

it('spaces in multiples of four', () => {
  expect(lightTheme.space(3)).toBe(12);
});
