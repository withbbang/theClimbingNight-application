import { StyleSheet, View } from 'react-native';

import { EyeSlashIcon } from 'phosphor-react-native/src/icons/EyeSlash';

import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useTheme } from '@/hooks/use-theme';

/** 작성자 본인에게만 보이는 숨김 안내. 숨긴 글의 사진은 API가 주지 않는다. */
export function PostHiddenNotice() {
  const { colors, radius, space } = useTheme();
  return (
    <FadeInItem index={0}>
      <View
        accessible
        accessibilityRole="summary"
        style={[
          styles.row,
          {
            gap: space(3),
            padding: space(4),
            borderRadius: radius.card,
            borderWidth: 1.5,
            borderColor: colors.border,
          },
        ]}
      >
        <EyeSlashIcon size={24} color={colors.text} weight="bold" />
        <View style={[styles.fill, { gap: space(1) }]}>
          <AppText variant="label">운영 정책에 따라 숨겨졌어요</AppText>
          <AppText variant="caption" tone="muted">
            다른 사람에게는 이 글이 보이지 않아요.
          </AppText>
        </View>
      </View>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
});
