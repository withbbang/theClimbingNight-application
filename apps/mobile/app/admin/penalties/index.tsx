import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { HandPalmIcon } from 'phosphor-react-native/src/icons/HandPalm';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { AppText } from '@/components/ui/AppText';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { PressCard } from '@/components/ui/PressCard';
import { usePenaltySummaries } from '@/hooks/use-penalty-admin';

function Penalties() {
  const router = useRouter();
  const query = usePenaltySummaries();
  return (
    <PagedList
      query={query}
      label="블랙리스트 목록"
      keyOf={(summary) => summary.rosterMemberId}
      renderItem={(summary, index) => {
        const counts = `누적 ${summary.totalCount}회, 최근 3개월 ${summary.recentCount}회`;
        return (
          <PressCard
            label={`${summary.somoimNickname}, ${counts}`}
            index={index}
            onPress={() => router.push(`/admin/penalties/${summary.rosterMemberId}`)}
          >
            <View style={{ flex: 1 }}>
              <AppText variant="label">{summary.somoimNickname}</AppText>
              <AppText variant="caption" tone="muted">
                {counts}
              </AppText>
            </View>
          </PressCard>
        );
      }}
      empty={
        <Notice
          illustration={<FloatingIcon icon={HandPalmIcon} />}
          title="기록이 있는 사람이 없어요"
          description="늦은 취소를 기록하거나 노쇼로 바꾸면 여기에 보여요."
        />
      }
    />
  );
}

export default function AdminPenaltiesScreen() {
  return (
    <AdminScreen title="블랙리스트">
      <Penalties />
    </AdminScreen>
  );
}
