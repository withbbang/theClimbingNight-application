import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { PushPinIcon } from 'phosphor-react-native/src/icons/PushPin';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { PressCard } from '@/components/ui/PressCard';
import { QueryState } from '@/components/ui/QueryState';
import { useRecentNotices } from '@/hooks/use-home';
import { useTheme } from '@/hooks/use-theme';
import { formatDay, toDateKey } from '@/lib/kst';

export function RecentNotices() {
  const router = useRouter();
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
            <View>
              {items.map((notice, i) => (
                <PressCard
                  key={notice.id}
                  index={i + 2}
                  label={notice.pinned ? `고정된 공지, ${notice.title}` : notice.title}
                  onPress={() => router.push(`/notices/${notice.id}`)}
                >
                  <View style={styles.fill}>
                    <AppText variant="label">{notice.title}</AppText>
                    <AppText variant="caption" tone="muted">
                      {formatDay(toDateKey(notice.publishedAt))}
                    </AppText>
                  </View>
                  {notice.pinned ? (
                    <PushPinIcon size={20} color={colors.textMuted} weight="fill" />
                  ) : null}
                </PressCard>
              ))}
              <Button
                label="공지 모두 보기"
                variant="text"
                onPress={() => router.push('/notices')}
              />
            </View>
          )
        }
      </QueryState>
    </View>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
