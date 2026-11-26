import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { TextField } from '@/components/ui/TextField';
import { useTheme } from '@/hooks/use-theme';

/** API가 받는 본문 최대 길이. */
export const MAX_BODY = 2200;

export const isBodyTooLong = (body: string) => body.length > MAX_BODY;

interface BodyFieldProps {
  value: string;
  onChangeText: (text: string) => void;
  editable: boolean;
}

/** 게시물 본문. 넘쳐도 잘라 내지 않고 글자 수와 안내로 알린다. */
export function BodyField({ value, onChangeText, editable }: BodyFieldProps) {
  const { space } = useTheme();
  const over = isBodyTooLong(value);
  return (
    <View style={{ gap: space(1) }}>
      <TextField
        label="본문"
        value={value}
        onChangeText={onChangeText}
        multiline
        editable={editable}
        placeholder="어떤 문제를 풀었나요?"
        error={over ? '본문은 2,200자까지 쓸 수 있어요.' : null}
      />
      <AppText
        variant="caption"
        tone={over ? 'danger' : 'muted'}
        style={styles.count}
        accessibilityLabel={`${MAX_BODY}자 중 ${value.length}자`}
      >
        {`${value.length} / ${MAX_BODY}`}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({ count: { alignSelf: 'flex-end' } });
