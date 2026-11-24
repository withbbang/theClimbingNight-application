import { StyleSheet, View } from 'react-native';

import { PushPinIcon } from 'phosphor-react-native/src/icons/PushPin';

import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { QueryState } from '@/components/ui/QueryState';
import { useRecentNotices } from '@/hooks/use-home';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, toDateKey } from '@/lib/kst';

export function RecentNotices() {
  const { colors, space } = useTheme();
  const query = useRecentNotices();
  return (
    <View style={{ gap: space(3) }}>
      <AppText variant="title" accessibilityRole="header">
        공지
      </AppText>
      <QueryState query={query}>
        {({ items }) =>
          items.length === 0 ? (
            <AppText tone="muted">아직 공지가 없어요</AppText>
          ) : (
            <View style={{ gap: space(4) }}>
              {items.map((notice, i) => (
                <FadeInItem key={notice.id} index={i + 2} style={[styles.row, { gap: space(2) }]}>
                  <View style={styles.fill}>
                    <AppText variant="label">{notice.title}</AppText>
                    <AppText variant="caption" tone="muted">
                      {formatDay(toDateKey(notice.publishedAt))}
                    </AppText>
                  </View>
                  {notice.pinned ? (
                    <View accessible accessibilityLabel="고정된 공지">
                      <PushPinIcon size={20} color={colors.textMuted} weight="fill" />
                    </View>
                  ) : null}
                </FadeInItem>
              ))}
            </View>
          )
        }
      </QueryState>
    </View>
  );
}

const styles = StyleSheet.create({ row: { flexDirection: 'row' }, fill: { flex: 1 } });
