import { useRef, useState, type ReactNode } from 'react';

import Animated, { FadeIn, FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';

interface ConfirmPanelProps {
  confirmLabel: string;
  message: string;
  onConfirm: () => Promise<unknown>;
  onBack: () => void;
  /** 무엇에 대한 것인지. 같은 버튼이 여럿일 때 화면 낭독기가 읽는다. */
  target?: string;
  loading?: boolean;
  /** 확인 버튼만 잠근다. (필수 입력이 비었을 때) */
  disabled?: boolean;
  error?: string | null;
  /** 함께 보이는 입력. */
  children?: ReactNode;
}

const named = (target: string | undefined, text: string) => (target ? `${target} ${text}` : text);

/** 무슨 일이 생기는지 알리고 확인을 받는 칸. */
export function ConfirmPanel({
  confirmLabel,
  message,
  onConfirm,
  onBack,
  target,
  loading = false,
  disabled = false,
  error,
  children,
}: ConfirmPanelProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const busy = useRef(false);

  async function confirm() {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      await onConfirm();
    } finally {
      busy.current = false;
    }
  }

  return (
    <Animated.View
      entering={
        reduced
          ? undefined
          : FadeInDown.springify().damping(motion.spring.damping).stiffness(motion.spring.stiffness)
      }
      style={{
        gap: space(3),
        padding: space(4),
        borderRadius: radius.card,
        borderWidth: 1.5,
        borderColor: colors.border,
      }}
    >
      <AppText accessibilityLiveRegion="polite">{message}</AppText>
      {children}
      {error ? <ErrorBanner message={error} /> : null}
      <Button
        label={confirmLabel}
        accessibilityLabel={named(target, confirmLabel)}
        loading={loading}
        disabled={disabled}
        onPress={() => void confirm()}
      />
      <Button
        label="돌아가기"
        accessibilityLabel={named(target, '돌아가기')}
        variant="text"
        disabled={loading}
        onPress={onBack}
      />
    </Animated.View>
  );
}

interface ConfirmActionProps extends Omit<ConfirmPanelProps, 'onConfirm' | 'onBack'> {
  label: string;
  /** 성공하면 true. 실패하면 펼친 채로 둔다. */
  onConfirm: () => Promise<boolean>;
  /** 펼치지 못하게 잠근다. */
  locked?: boolean;
  variant?: 'primary' | 'secondary' | 'text';
}

/** 되돌리기 어려운 동작. 한 번 누르면 무슨 일이 생기는지 펼쳐 보이고, 다시 눌러야 실행한다. */
export function ConfirmAction({
  label,
  onConfirm,
  locked = false,
  variant = 'secondary',
  ...panel
}: ConfirmActionProps) {
  const reduced = useReducedMotion();
  const [open, setOpen] = useState(false);
  const left = useLeft();

  async function confirm() {
    if ((await onConfirm()) && !left.current) {
      setOpen(false);
    }
  }

  if (open) {
    return <ConfirmPanel {...panel} onConfirm={confirm} onBack={() => setOpen(false)} />;
  }
  return (
    <Animated.View entering={reduced ? undefined : FadeIn.duration(180)}>
      <Button
        label={label}
        accessibilityLabel={named(panel.target, label)}
        variant={variant}
        disabled={locked}
        onPress={() => setOpen(true)}
      />
    </Animated.View>
  );
}
