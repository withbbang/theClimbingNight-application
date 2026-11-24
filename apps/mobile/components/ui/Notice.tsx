import type { ReactNode } from 'react';
import { View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useTheme } from '@/hooks/use-theme';

interface NoticeProps {
  title: string;
  description: string;
  illustration?: ReactNode;
  actionLabel?: string;
  onAction?: () => void;
}

/** 빈 화면·안내 화면. 무엇이 없고 무엇을 하면 되는지 왼쪽 정렬로 알린다. */
export function Notice({ title, description, illustration, actionLabel, onAction }: NoticeProps) {
  const { space } = useTheme();
  return (
    <View style={{ gap: space(3) }}>
      {illustration ? <FadeInItem index={0}>{illustration}</FadeInItem> : null}
      <FadeInItem index={1}>
        <AppText variant="title" accessibilityRole="header">
          {title}
        </AppText>
      </FadeInItem>
      <FadeInItem index={2}>
        <AppText tone="muted">{description}</AppText>
      </FadeInItem>
      {actionLabel && onAction ? (
        <FadeInItem index={3} style={{ marginTop: space(3) }}>
          <Button label={actionLabel} onPress={onAction} />
        </FadeInItem>
      ) : null}
    </View>
  );
}
