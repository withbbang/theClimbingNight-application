import { useState } from 'react';
import { View } from 'react-native';

import { RosterPicker, rosterLabel } from '@/components/membership/RosterPicker';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { RadioRow } from '@/components/ui/RadioRow';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';
import type {
  MembershipApproveRequest,
  MembershipReviewItemResponse,
} from '@/types/api/membership';
import type { RosterCandidateResponse } from '@/types/api/roster';

type Choice = RosterCandidateResponse | 'NEW' | null;

/** 후보가 하나면 그 명부, 없으면 새 항목. 여럿이면 운영진이 고른다. */
function defaultChoice({ candidates }: MembershipReviewItemResponse): Choice {
  return candidates.length === 0 ? 'NEW' : candidates.length === 1 ? candidates[0] : null;
}

interface RequestCardProps {
  request: MembershipReviewItemResponse;
  index: number;
  /** 이 신청을 보내는 중인 동작. */
  sending: 'approve' | 'reject' | null;
  /** 다른 신청을 보내거나 목록을 다시 받는 중. */
  locked: boolean;
  onApprove: (body: MembershipApproveRequest) => void;
  onReject: (reason: string) => Promise<boolean>;
}

export function RequestCard({
  request,
  index,
  sending,
  locked,
  onApprove,
  onReject,
}: RequestCardProps) {
  const { colors, radius, space } = useTheme();
  const [chosen, setChosen] = useState<Choice>(null);
  const [searching, setSearching] = useState(false);
  const [reason, setReason] = useState('');
  const choice = chosen ?? defaultChoice(request);
  const choiceId = choice !== null && choice !== 'NEW' ? choice.id : null;
  const isCandidate = request.candidates.some((roster) => roster.id === choiceId);
  // 검색으로 고른 명부는 검색어가 바뀌어도 보이게 카드에 함께 둔다.
  const rosters =
    choice === null || choice === 'NEW' || isCandidate
      ? request.candidates
      : [...request.candidates, choice];
  const name = request.user.nickname;
  const busy = locked || sending !== null;
  const trimmed = reason.trim();

  function approve() {
    if (choice !== null) {
      onApprove(choice === 'NEW' ? { createNew: true } : { rosterMemberId: choice.id });
    }
  }

  return (
    <FadeInItem
      index={index}
      style={{
        gap: space(3),
        marginBottom: space(4),
        padding: space(4),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      <View style={{ gap: space(1) }}>
        <AppText variant="title">{name}</AppText>
        <AppText>{`소모임 닉네임: ${request.somoimNickname}`}</AppText>
        <AppText variant="caption" tone="muted">
          {formatDateTime(request.createdAt)}
        </AppText>
      </View>
      <View accessibilityRole="radiogroup">
        <AppText variant="label">어느 명부에 연결할까요?</AppText>
        {rosters.map((roster) => (
          <RadioRow
            key={roster.id}
            label={roster.somoimNickname}
            caption={roster.note}
            accessibilityLabel={rosterLabel(name, roster)}
            selected={choiceId === roster.id}
            disabled={busy}
            onPress={() => setChosen(roster)}
          />
        ))}
        <RadioRow
          label="새 명부 항목"
          caption="처음 온 동호회원이면 골라요."
          accessibilityLabel={`${name} 새 명부 항목`}
          selected={choice === 'NEW'}
          disabled={busy}
          onPress={() => setChosen('NEW')}
        />
        {searching ? (
          <RosterPicker
            target={name}
            shownIds={rosters.map((roster) => roster.id)}
            disabled={busy}
            onSelect={setChosen}
          />
        ) : (
          <Button
            label="다른 명부 찾기"
            accessibilityLabel={`${name} 다른 명부 찾기`}
            variant="text"
            onPress={() => setSearching(true)}
          />
        )}
      </View>
      <Button
        label="승인"
        accessibilityLabel={`${name} 승인`}
        loading={sending === 'approve'}
        disabled={busy || choice === null}
        onPress={approve}
      />
      <ConfirmAction
        target={name}
        label="거절"
        confirmLabel="거절 확정"
        message="신청한 사람에게 사유가 보여요."
        loading={sending === 'reject'}
        locked={busy}
        disabled={busy || !trimmed}
        onConfirm={() => onReject(trimmed)}
      >
        <TextField
          label="거절 사유"
          value={reason}
          onChangeText={setReason}
          maxLength={200}
          multiline
          editable={!busy}
        />
      </ConfirmAction>
    </FadeInItem>
  );
}
