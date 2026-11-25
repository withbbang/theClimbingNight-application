import { useState } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { HandPalmIcon } from 'phosphor-react-native/src/icons/HandPalm';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { PenaltyRow } from '@/components/penalty/PenaltyRow';
import { AppText } from '@/components/ui/AppText';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { TextField } from '@/components/ui/TextField';
import { useRevokePenalty, useRosterPenalties } from '@/hooks/use-penalty-admin';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { parseId } from '@/lib/parse-id';
import type { PenaltyResponse } from '@/types/api/penalty';

const MAX_STAGGER = 10;

function PenaltyCard({ penalty, index }: { penalty: PenaltyResponse; index: number }) {
  const { colors, radius, space } = useTheme();
  const [reason, setReason] = useState('');
  const revoke = useRevokePenalty();
  const trimmed = reason.trim();

  return (
    <FadeInItem
      index={Math.min(index, MAX_STAGGER)}
      style={{
        gap: space(3),
        padding: space(4),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      <PenaltyRow penalty={penalty} />
      {/* 다른 운영진이 먼저 취소해 버튼이 사라져도 이유는 남긴다. */}
      {revoke.error ? <ErrorBanner message={errorMessage(revoke.error)} /> : null}
      {penalty.active ? (
        <ConfirmAction
          label="기록 취소"
          confirmLabel="기록 취소 확정"
          message="취소한 기록은 횟수에서 빠지고, 본인에게 사유가 보여요."
          loading={revoke.isPending}
          disabled={!trimmed}
          onConfirm={async () =>
            (await revoke.submit({ id: penalty.id, reason: trimmed })) !== undefined
          }
        >
          <TextField
            label="취소 사유"
            value={reason}
            onChangeText={setReason}
            maxLength={200}
            multiline
            editable={!revoke.isPending}
          />
        </ConfirmAction>
      ) : null}
    </FadeInItem>
  );
}

export default function RosterPenaltiesScreen() {
  const router = useRouter();
  const { space } = useTheme();
  const id = parseId(useLocalSearchParams().rosterId);
  const query = useRosterPenalties(id);
  return (
    <AdminScreen title="블랙리스트 기록" scroll>
      {id === null || isNotFound(query.error) ? (
        <Notice
          illustration={<FloatingIcon icon={HandPalmIcon} />}
          title="명부에서 찾을 수 없어요"
          description="주소가 잘못됐거나 명부에 없는 사람이에요."
          actionLabel="홈으로"
          onAction={() => router.dismissTo('/')}
        />
      ) : (
        <QueryState query={query}>
          {(roster) => (
            <View style={{ gap: space(4) }}>
              <FadeInItem index={1}>
                <AppText variant="title">{roster.somoimNickname}</AppText>
                <AppText tone="muted">
                  {`누적 ${roster.totalCount}회, 최근 3개월 ${roster.recentCount}회`}
                </AppText>
              </FadeInItem>
              {roster.penalties.length === 0 ? <AppText>기록이 없어요</AppText> : null}
              {roster.penalties.map((penalty, i) => (
                <PenaltyCard key={penalty.id} penalty={penalty} index={i + 2} />
              ))}
            </View>
          )}
        </QueryState>
      )}
    </AdminScreen>
  );
}
