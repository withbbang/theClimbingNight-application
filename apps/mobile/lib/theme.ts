const palette = {
  deepWall: '#101A2E',
  wall: '#18243D',
  chalk: '#EEF2F8',
  hold: '#FFC83D',
} as const;

export interface Theme {
  dark: boolean;
  colors: {
    background: string;
    surface: string;
    text: string;
    textMuted: string;
    accent: string;
    onAccent: string;
    border: string;
    danger: string;
    /** 꺼진 홀드. 장식이라 글자에는 쓰지 않는다. */
    holdOff: string;
    /** 사진 위에 얹는 아이콘. 사진은 모드와 상관없으니 두 모드에서 같다. */
    onPhoto: string;
    /** 사진 위 아이콘 뒤에 옅게 까는 어둠(받침). */
    photoShade: string;
  };
  space(n: number): number;
  radius: { pill: number; input: number; card: number };
  motion: { spring: { damping: number; stiffness: number }; pressScale: number };
}

const shared = {
  space: (n: number) => n * 4,
  radius: { pill: 999, input: 12, card: 20 },
  motion: { spring: { damping: 18, stiffness: 180 }, pressScale: 0.97 },
};

export const lightTheme: Theme = {
  ...shared,
  dark: false,
  colors: {
    background: palette.chalk,
    surface: '#FFFFFF',
    text: palette.wall,
    textMuted: '#4A5873',
    accent: palette.hold,
    onAccent: palette.wall,
    border: '#7C8AA5',
    danger: '#B3261E',
    holdOff: '#CBD5E6',
    onPhoto: palette.chalk,
    photoShade: palette.deepWall,
  },
};

export const darkTheme: Theme = {
  ...shared,
  dark: true,
  colors: {
    background: palette.deepWall,
    surface: palette.wall,
    text: palette.chalk,
    textMuted: '#AAB7CF',
    accent: palette.hold,
    onAccent: palette.wall,
    border: '#6E7F9F',
    danger: '#FF9B91',
    holdOff: '#2B3B5E',
    onPhoto: palette.chalk,
    photoShade: palette.deepWall,
  },
};

function luminance(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const channel = parseInt(hex.slice(i, i + 2), 16) / 255;
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG 대비. #RRGGBB만 받는다. */
export function contrastRatio(a: string, b: string) {
  const [dark, light] = [luminance(a), luminance(b)].sort((x, y) => x - y);
  return (light + 0.05) / (dark + 0.05);
}
