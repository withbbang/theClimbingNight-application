import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/hooks/use-theme';
import { useUpdateBio } from '@/hooks/use-update-bio';
import { ApiError } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';

export const BIO_MAX = 150;
/** 줄바꿈 3개 = 4줄. */
const BIO_MAX_BREAKS = 3;
const BIO_RULE = '자기소개는 150자, 4줄까지 쓸 수 있어요.';

/** API와 같은 규칙: 줄바꿈을 \n으로 맞추고 앞뒤 공백을 자른 뒤 코드포인트로 센다. */
export function normalizeBio(text: string) {
  return text.replace(/\r\n?/g, '\n').trim();
}

interface BioFormProps {
  initial: string | null;
  /** 저장이 끝났을 때. */
  onSaved: () => void;
}

/** 남은 글자 수. 한도를 넘는 순간 한 번 흔들리고 경고색이 된다. */
function Counter({ left }: { left: number }) {
  const { motion } = useTheme();
  const reduced = useReducedMotion();
  const shake = useSharedValue(0);
  const over = left < 0;
  const wasOver = useRef(over);

  useEffect(() => {
    if (over && !wasOver.current && !reduced) {
      shake.set(
        withSequence(
          withTiming(-6, { duration: 50 }),
          withTiming(5, { duration: 60 }),
          withTiming(-3, { duration: 60 }),
          withSpring(0, motion.spring),
        ),
      );
    }
    wasOver.current = over;
  }, [motion.spring, over, reduced, shake]);

  const style = useAnimatedStyle(() => ({ transform: [{ translateX: shake.get() }] }));
  return (
    <Animated.View style={[styles.counter, style]}>
      <AppText
        variant="caption"
        tone={over ? 'danger' : 'muted'}
        accessibilityLiveRegion={over ? 'polite' : 'none'}
      >
        {over ? `${-left}자 넘었어요` : `${left}자 남았어요`}
      </AppText>
    </Animated.View>
  );
}

export function BioForm({ initial, onSaved }: BioFormProps) {
  const { space } = useTheme();
  const update = useUpdateBio();
  const [bio, setBio] = useState(initial ?? '');
  const normalized = normalizeBio(bio);
  const left = BIO_MAX - [...normalized].length;
  const tooManyLines = (normalized.match(/\n/g)?.length ?? 0) > BIO_MAX_BREAKS;
  const invalid = left < 0 || tooManyLines;
  const ofField = update.error instanceof ApiError && update.error.code === 'USER_BIO_INVALID';

  async function save() {
    if (invalid) {
      return;
    }
    if (await update.submit(normalized)) {
      onSaved();
    }
  }

  return (
    <View style={{ gap: space(4) }}>
      {/* 입력 중에도 키보드에 가리지 않게 남은 글자 수는 이름표 줄 오른쪽에 둔다. */}
      <View>
        <TextField
          label="자기소개"
          value={bio}
          onChangeText={(text) => {
            setBio(text);
            update.reset();
          }}
          placeholder="어떤 클라이밍을 좋아하는지 적어 보세요"
          multiline
          editable={!update.isPending}
          error={tooManyLines ? BIO_RULE : ofField ? errorMessage(update.error) : null}
        />
        <Counter left={left} />
      </View>
      {update.error && !ofField ? <ErrorBanner message={errorMessage(update.error)} /> : null}
      <Button
        label="저장"
        loading={update.isPending}
        disabled={invalid}
        onPress={() => void save()}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  // 이름표(label, 줄 높이 24)와 글줄을 맞춘다.
  counter: { position: 'absolute', top: 3, right: 0 },
});
