import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { SealCheckIcon } from 'phosphor-react-native/src/icons/SealCheck';
import Animated, { FadeIn, FadeInDown, useReducedMotion, ZoomIn } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { RadioRow } from '@/components/ui/RadioRow';
import { TextField } from '@/components/ui/TextField';
import { useReport } from '@/hooks/use-moderation';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { ReportReason, ReportTargetType } from '@/types/api/moderation';

const DETAIL_MAX = 500;
const STAGGER_MS = 45;

export const REASONS: { value: ReportReason; label: string; caption?: string }[] = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'SEXUAL', label: '음란·선정' },
  { value: 'ABUSE', label: '욕설·혐오·괴롭힘' },
  {
    value: 'PORTRAIT_PRIVACY',
    label: '초상권·사생활 침해',
    caption: '내 사진이 동의 없이 올라왔어요',
  },
  { value: 'OTHER', label: '기타' },
];

interface ReportSheetProps {
  targetType: ReportTargetType;
  targetId: number;
  onClose: () => void;
}

function Received({ onClose }: { onClose: () => void }) {
  const { colors, motion, space } = useTheme();
  const reduced = useReducedMotion();
  return (
    <View style={[styles.center, { gap: space(3), paddingVertical: space(2) }]}>
      <Animated.View
        entering={
          reduced
            ? undefined
            : ZoomIn.springify()
                .damping(motion.spring.damping - 6)
                .stiffness(motion.spring.stiffness)
        }
      >
        <SealCheckIcon size={40} color={colors.accent} weight="fill" />
      </Animated.View>
      <Animated.View entering={reduced ? undefined : FadeIn.delay(120).duration(220)}>
        <AppText variant="label" accessibilityLiveRegion="polite" style={styles.centerText}>
          신고를 접수했어요. 운영진이 확인할게요.
        </AppText>
      </Animated.View>
      <Button label="닫기" variant="text" onPress={onClose} />
    </View>
  );
}

/** 신고 사유를 고르고 보낸다. 이미 신고했거나 이미 내려간 글이어도 접수로 끝낸다. */
export function ReportSheet({ targetType, targetId, onClose }: ReportSheetProps) {
  const { motion, space } = useTheme();
  const reduced = useReducedMotion();
  const report = useReport();
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [detail, setDetail] = useState('');
  const [done, setDone] = useState(false);

  // 댓글에는 초상권 사유가 없다.
  const reasons =
    targetType === 'COMMENT'
      ? REASONS.filter((item) => item.value !== 'PORTRAIT_PRIVACY')
      : REASONS;
  const trimmed = detail.trim();
  const ready = reason !== null && (reason !== 'OTHER' || trimmed.length > 0);

  async function send() {
    if (!ready) {
      return;
    }
    const sent = await report.submit({
      targetType,
      targetId,
      reason,
      detail: trimmed.length > 0 ? trimmed : null,
    });
    if (sent) {
      setDone(true);
    }
  }

  if (done) {
    return <Received onClose={onClose} />;
  }
  return (
    <View style={{ gap: space(3) }}>
      <AppText variant="label" accessibilityRole="header">
        신고하는 이유를 골라 주세요
      </AppText>
      <View accessibilityRole="radiogroup">
        {reasons.map((item, i) => (
          <Animated.View
            key={item.value}
            entering={
              reduced
                ? undefined
                : FadeInDown.delay(i * STAGGER_MS)
                    .springify()
                    .damping(motion.spring.damping)
                    .stiffness(motion.spring.stiffness)
            }
          >
            <RadioRow
              label={item.label}
              caption={item.caption}
              accessibilityLabel={item.label}
              selected={reason === item.value}
              disabled={report.isPending}
              onPress={() => setReason(item.value)}
            />
          </Animated.View>
        ))}
      </View>
      {reason === null ? null : (
        <Animated.View entering={reduced ? undefined : FadeInDown.duration(200)}>
          <TextField
            label="자세한 내용"
            helper={reason === 'OTHER' ? '기타는 내용을 꼭 적어 주세요.' : '적지 않아도 돼요.'}
            value={detail}
            onChangeText={setDetail}
            maxLength={DETAIL_MAX}
            multiline
            editable={!report.isPending}
          />
        </Animated.View>
      )}
      {report.error ? <ErrorBanner message={errorMessage(report.error)} /> : null}
      <Button
        label="신고 보내기"
        loading={report.isPending}
        disabled={!ready}
        onPress={() => void send()}
      />
      <Button label="돌아가기" variant="text" disabled={report.isPending} onPress={onClose} />
    </View>
  );
}

const styles = StyleSheet.create({
  center: { alignItems: 'center' },
  centerText: { textAlign: 'center' },
});
