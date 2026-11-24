import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Hold } from '@/components/ui/Hold';
import { useTheme } from '@/hooks/use-theme';
import type { ParticipantResponse } from '@/types/api/meetup';

const MAX_STAGGER = 10;

export function ParticipantList({ participants }: { participants: ParticipantResponse[] | null }) {
  const { space } = useTheme();
  if (participants === null) {
    return <AppText tone="muted">명단은 공개되지 않아요</AppText>;
  }
  return (
    <View style={{ gap: space(3) }}>
      <AppText variant="label" accessibilityRole="header">
        함께하는 사람
      </AppText>
      {participants.length === 0 ? <AppText tone="muted">아직 신청한 사람이 없어요</AppText> : null}
      {participants.map((person, i) => {
        const label = person.status === 'CONFIRMED' ? '확정' : '대기';
        return (
          <FadeInItem key={`${person.status}-${person.order}`} index={Math.min(i, MAX_STAGGER)}>
            <View
              accessible
              accessibilityLabel={`${label} ${person.order}번 ${person.somoimNickname}`}
              style={[styles.row, { gap: space(3) }]}
            >
              <Hold on={person.status === 'CONFIRMED'} index={Math.min(i, MAX_STAGGER)} />
              <AppText style={styles.name}>{person.somoimNickname}</AppText>
              <AppText variant="caption" tone="muted">
                {person.status === 'CONFIRMED' ? label : `${label} ${person.order}`}
              </AppText>
            </View>
          </FadeInItem>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', minHeight: 32 },
  name: { flex: 1 },
});
