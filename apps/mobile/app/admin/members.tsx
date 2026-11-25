import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { UsersThreeIcon } from 'phosphor-react-native/src/icons/UsersThree';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { TextField } from '@/components/ui/TextField';
import { useDebounced } from '@/hooks/use-debounced';
import { useMembers, useRevokeMember } from '@/hooks/use-members';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { MemberResponse } from '@/types/api/member';

const MAX_STAGGER = 8;

interface MemberRowProps {
  member: MemberResponse;
  index: number;
  sending: boolean;
  locked: boolean;
  onRevoke: () => Promise<boolean>;
}

function MemberRow({ member, index, sending, locked, onRevoke }: MemberRowProps) {
  const router = useRouter();
  const { colors, radius, space } = useTheme();
  const { nickname, rosterMemberId } = member;
  return (
    <FadeInItem
      index={Math.min(index, MAX_STAGGER)}
      style={{
        gap: space(2),
        marginBottom: space(3),
        padding: space(4),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      <AppText variant="label">{nickname}</AppText>
      {member.role === 'ADMIN' ? <AppText variant="caption">운영진</AppText> : null}
      <AppText variant="caption" tone="muted">
        {member.somoimNickname === null
          ? '명부와 연결되지 않았어요'
          : `소모임 닉네임: ${member.somoimNickname}`}
      </AppText>
      {rosterMemberId === null ? null : (
        <Button
          label="블랙리스트 보기"
          accessibilityLabel={`${nickname} 블랙리스트 보기`}
          variant="text"
          onPress={() => router.push(`/admin/penalties/${rosterMemberId}`)}
        />
      )}
      {member.role === 'MEMBER' ? (
        <ConfirmAction
          target={nickname}
          label="자격 해제"
          confirmLabel="자격 해제 확정"
          message="일반회원이 되고 다가오는 벙 신청도 취소돼요. 참석 기록은 남아요."
          loading={sending}
          locked={locked}
          disabled={locked}
          onConfirm={onRevoke}
        />
      ) : null}
    </FadeInItem>
  );
}

function Members() {
  const { space } = useTheme();
  const [text, setText] = useState('');
  const query = useMembers(useDebounced(text.trim()));
  const revoke = useRevokeMember();
  const locked = revoke.isPending || query.isRefetching;

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <TextField
        label="닉네임 검색"
        value={text}
        onChangeText={setText}
        maxLength={50}
        autoCorrect={false}
      />
      {revoke.error ? <ErrorBanner message={errorMessage(revoke.error)} /> : null}
      <PagedList
        query={query}
        label="회원 목록"
        keyOf={(member) => member.userId}
        renderItem={(member, index) => (
          // 칸이 재사용돼도 열린 확인이 다른 회원으로 옮지 않게 한다.
          <MemberRow
            key={member.userId}
            member={member}
            index={index}
            sending={revoke.isPending && revoke.variables === member.userId}
            locked={locked}
            onRevoke={async () => (await revoke.submit(member.userId)) !== undefined}
          />
        )}
        empty={
          <Notice
            illustration={<FloatingIcon icon={UsersThreeIcon} />}
            title="찾는 회원이 없어요"
            description="앱 닉네임이나 소모임 닉네임으로 찾을 수 있어요."
          />
        }
      />
    </View>
  );
}

export default function MembersScreen() {
  return (
    <AdminScreen title="회원 목록">
      <Members />
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
