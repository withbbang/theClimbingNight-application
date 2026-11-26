import { ScrollView, StyleSheet, View } from 'react-native';

import { Image } from 'expo-image';

import { WarningIcon } from 'phosphor-react-native/src/icons/Warning';
import Animated, { FadeIn, useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { REASONS } from '@/components/feed/ReportSheet';
import { AppText } from '@/components/ui/AppText';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useTheme } from '@/hooks/use-theme';
import { formatRelative } from '@/lib/kst';
import type { ReportTargetAction, ReportTargetResponse } from '@/types/api/moderation';

const THUMB = 64;
const SHOWN_DETAILS = 3;
const HIDDEN = ['HIDDEN', 'HIDDEN_PENDING'];

const STATUS_LABELS: Record<string, string> = {
  PENDING: '사진 정리 중',
  VISIBLE: '공개 중',
  HIDDEN_PENDING: '자동으로 숨김',
  HIDDEN: '숨김',
  DELETED: '삭제됨',
  ACTIVE: '활동 중',
  WITHDRAWN: '탈퇴',
};

/** 화면 낭독기와 버튼 이름에 쓰는 대상 이름. */
export function targetName(target: ReportTargetResponse) {
  if (target.targetType === 'USER') {
    return `${target.userNickname ?? '탈퇴한 회원'} 사용자`;
  }
  return target.post ? `${target.post.author.nickname}의 게시물` : `${target.targetId}번 게시물`;
}

function reasonSummary(reasons: ReportTargetResponse['reasons']) {
  return REASONS.filter(({ value }) => (reasons[value] ?? 0) > 0)
    .map(({ value, label }) => `${label} ${reasons[value]}`)
    .join(', ');
}

interface ActionSpec {
  action: ReportTargetAction;
  label: string;
  message: string;
}

function actionsOf(target: ReportTargetResponse): ActionSpec[] {
  const dismiss = (message: string): ActionSpec => ({ action: 'DISMISS', label: '기각', message });
  if (target.targetType !== 'POST') {
    return [dismiss('신고를 모두 기각해요.')];
  }
  if (target.targetStatus === 'DELETED') {
    return [dismiss('이미 지워진 글이에요. 신고를 닫아요.')];
  }
  const autoHidden = target.targetStatus === 'HIDDEN_PENDING';
  const hidden = HIDDEN.includes(target.targetStatus);
  const actions: ActionSpec[] = [];
  if (target.targetStatus !== 'HIDDEN') {
    actions.push({
      action: 'HIDE',
      label: '숨기기',
      message: autoHidden
        ? '자동으로 숨긴 글을 그대로 숨겨 두고 신고를 처리해요.'
        : '피드에서 내리고 사진을 격리해요. 신고는 모두 처리돼요.',
    });
  }
  if (hidden) {
    actions.push({
      action: 'RESTORE',
      label: '복원',
      message: '다시 피드에 올려요. 신고는 모두 기각돼요.',
    });
  }
  actions.push(
    dismiss(
      autoHidden
        ? '신고를 모두 기각하고 자동으로 숨긴 글을 다시 올려요.'
        : '신고를 모두 기각해요. 글은 그대로 둬요.',
    ),
  );
  return actions;
}

function Photos({ urls, name }: { urls: string[]; name: string }) {
  const { colors, radius, space } = useTheme();
  const reduced = useReducedMotion();
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false}>
      <View style={[styles.row, { gap: space(2) }]}>
        {urls.map((url, i) => (
          <Animated.View
            key={url}
            entering={
              reduced
                ? undefined
                : ZoomIn.delay(i * 60)
                    .springify()
                    .damping(16)
            }
          >
            <Image
              source={{ uri: url }}
              accessibilityLabel={`${name} 사진 ${i + 1}`}
              contentFit="cover"
              style={{
                width: THUMB,
                height: THUMB,
                borderRadius: radius.input,
                backgroundColor: colors.holdOff,
              }}
            />
          </Animated.View>
        ))}
      </View>
    </ScrollView>
  );
}

interface ReportTargetRowProps {
  target: ReportTargetResponse;
  index: number;
  /** 이 대상에 보내는 중인 조치. */
  sending: ReportTargetAction | null;
  locked: boolean;
  onAct: (action: ReportTargetAction) => Promise<boolean>;
}

/** 신고가 들어온 대상 하나. 무엇이 왜 신고됐는지 보여 주고 숨김·복원·기각을 고른다. */
export function ReportTargetRow({ target, index, sending, locked, onAct }: ReportTargetRowProps) {
  const { colors, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const name = targetName(target);
  const { post } = target;
  // 숨긴 글은 공개본이 격리돼 있으니 격리본을 본다.
  const urls =
    target.quarantineUrls.length > 0
      ? target.quarantineUrls
      : (post?.media.flatMap((m) => (m.thumbnailUrl ? [m.thumbnailUrl] : [])) ?? []);
  const more = target.details.length - SHOWN_DETAILS;

  return (
    <FadeInItem
      index={index}
      style={{
        gap: space(3),
        padding: space(4),
        marginBottom: space(3),
        borderRadius: radius.card,
        backgroundColor: colors.surface,
        borderWidth: target.cleanupFailed ? 1.5 : 0,
        borderColor: colors.danger,
      }}
    >
      <View style={[styles.row, { gap: space(2) }]}>
        <View
          style={[
            styles.badge,
            { borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: space(2) },
          ]}
        >
          <AppText variant="caption">{target.targetType === 'USER' ? '사용자' : '게시물'}</AppText>
        </View>
        <AppText variant="caption" tone="muted" style={styles.fill}>
          {STATUS_LABELS[target.targetStatus] ?? target.targetStatus}
        </AppText>
        <AppText variant="caption" tone="muted">
          {formatRelative(target.lastReportedAt)}
        </AppText>
      </View>

      <View style={{ gap: space(1) }}>
        <AppText variant="title">{`신고 ${target.reportCount}건`}</AppText>
        <AppText variant="label">{reasonSummary(target.reasons)}</AppText>
      </View>

      {target.cleanupFailed ? (
        <Animated.View
          entering={reduced ? undefined : FadeIn.duration(240)}
          style={[styles.row, { gap: space(2) }]}
        >
          <WarningIcon size={20} color={colors.danger} weight="bold" />
          <AppText variant="caption" tone="danger" style={styles.fill}>
            사진 정리에 실패했어요. 공개 사본이 남아 있을 수 있어요.
          </AppText>
        </Animated.View>
      ) : null}

      {target.targetType === 'USER' ? (
        <AppText>{target.userNickname ?? '탈퇴한 회원'}</AppText>
      ) : post ? (
        <View style={{ gap: space(2) }}>
          <AppText variant="label">{post.author.nickname}</AppText>
          {urls.length > 0 ? <Photos urls={urls} name={name} /> : null}
          {post.body ? <AppText numberOfLines={3}>{post.body}</AppText> : null}
        </View>
      ) : null}

      {target.details.length > 0 ? (
        <View
          style={{
            gap: space(1),
            paddingLeft: space(3),
            borderLeftWidth: 2,
            borderLeftColor: colors.accent,
          }}
        >
          {target.details.slice(0, SHOWN_DETAILS).map((detail, i) => (
            <AppText key={i} variant="caption">
              {detail}
            </AppText>
          ))}
          {more > 0 ? <AppText variant="caption" tone="muted">{`외 ${more}건`}</AppText> : null}
        </View>
      ) : null}

      <View style={{ gap: space(2) }}>
        {actionsOf(target).map(({ action, label, message }) => (
          <ConfirmAction
            key={action}
            label={label}
            confirmLabel={`${label} 확정`}
            target={name}
            message={message}
            variant={action === 'DISMISS' ? 'text' : 'secondary'}
            loading={sending === action}
            locked={locked}
            disabled={locked}
            onConfirm={() => onAct(action)}
          />
        ))}
      </View>
    </FadeInItem>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center' },
  badge: { borderWidth: 1, paddingVertical: 2 },
});
