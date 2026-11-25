import { useRef, useState, type ReactNode } from 'react';

import Animated, {
  FadeIn,
  FadeInDown,
  LinearTransition,
  useReducedMotion,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';

interface ConfirmActionProps {
  label: string;
  confirmLabel: string;
  message: string;
  /** 성공하면 true. 실패하면 펼친 채로 둔다. */
  onConfirm: () => Promise<boolean>;
  /** 무엇에 대한 것인지. 같은 버튼이 여럿일 때 화면 낭독기가 읽는다. */
  target?: string;
  loading?: boolean;
  /** 펼치지 못하게 잠근다. */
  locked?: boolean;
  /** 확인 버튼만 잠근다. (필수 입력이 비었을 때) */
  disabled?: boolean;
  error?: string | null;
  variant?: 'primary' | 'secondary' | 'text';
  /** 펼쳤을 때 함께 보이는 입력. */
  children?: ReactNode;
}

/** 되돌리기 어려운 동작. 한 번 누르면 무슨 일이 생기는지 펼쳐 보이고, 다시 눌러야 실행한다. */
export function ConfirmAction({
  label,
  confirmLabel,
  message,
  onConfirm,
  target,
  loading = false,
  locked = false,
  disabled = false,
  error,
  variant = 'secondary',
  children,
}: ConfirmActionProps) {
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const [open, setOpen] = useState(false);
  const busy = useRef(false);
  const left = useLeft();
  const named = (text: string) => (target ? `${target} ${text}` : text);

  async function confirm() {
    if (busy.current) {
      return;
    }
    busy.current = true;
    try {
      if ((await onConfirm()) && !left.current) {
        setOpen(false);
      }
    } finally {
      busy.current = false;
    }
  }

  if (!open) {
    return (
      <Animated.View key="closed" entering={reduced ? undefined : FadeIn.duration(180)}>
        <Button
          label={label}
          accessibilityLabel={named(label)}
          variant={variant}
          disabled={locked}
          onPress={() => setOpen(true)}
        />
      </Animated.View>
    );
  }
  return (
    <Animated.View
      key="open"
      entering={
        reduced
          ? undefined
          : FadeInDown.springify().damping(motion.spring.damping).stiffness(motion.spring.stiffness)
      }
      layout={reduced ? undefined : LinearTransition.springify().damping(motion.spring.damping)}
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
        accessibilityLabel={named(confirmLabel)}
        loading={loading}
        disabled={disabled}
        onPress={() => void confirm()}
      />
      <Button
        label="돌아가기"
        accessibilityLabel={named('돌아가기')}
        variant="text"
        disabled={loading}
        onPress={() => setOpen(false)}
      />
    </Animated.View>
  );
}
