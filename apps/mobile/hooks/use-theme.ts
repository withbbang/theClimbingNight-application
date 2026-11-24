import { useColorScheme } from 'react-native';

import { darkTheme, lightTheme, type Theme } from '@/lib/theme';

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? darkTheme : lightTheme;
}
