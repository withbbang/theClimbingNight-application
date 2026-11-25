import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Screen } from '@/components/ui/Screen';
import { useTheme } from '@/hooks/use-theme';

interface AdminScreenProps {
  title: string;
  children: ReactNode;
  /** 목록처럼 안에서 스크롤하면 false. */
  scroll?: boolean;
}

export function AdminScreen({ title, children, scroll = false }: AdminScreenProps) {
  const { space } = useTheme();
  return (
    <Screen scroll={scroll}>
      <BackButton />
      <RoleGate role="ADMIN">
        <View style={[styles.fill, { gap: space(4) }]}>
          <FadeInItem index={0}>
            <AppText variant="display" accessibilityRole="header">
              {title}
            </AppText>
          </FadeInItem>
          {children}
        </View>
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
