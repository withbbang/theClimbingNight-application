import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import * as Haptics from 'expo-haptics';

import { AppText } from '@/components/ui/AppText';
import { ConfirmPanel } from '@/components/ui/ConfirmAction';
import { Hold } from '@/components/ui/Hold';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';
import type { AttendanceResult } from '@/types/api/attendance';

const RESULTS: { value: AttendanceResult; label: string }[] = [
  { value: 'ATTENDED', label: '참석' },
  { value: 'EXCUSED', label: '불참' },
  { value: 'NO_SHOW', label: '노쇼' },
];

interface AttendanceSwitchProps {
  target: string;
  value: AttendanceResult;
  loading: boolean;
  /** 저장했으면 true. */
  onChange: (result: AttendanceResult) => Promise<boolean>;
}

/** 고른 결과의 홀드가 켜진다. 노쇼는 블랙리스트가 되므로 한 번 더 묻는다. */
export function AttendanceSwitch({ target, value, loading, onChange }: AttendanceSwitchProps) {
  const { colors, radius, space } = useTheme();
  const [asking, setAsking] = useState(false);
  const left = useLeft();

  function choose(result: AttendanceResult) {
    if (result === value) {
      setAsking(false);
      return;
    }
    void Haptics.selectionAsync().catch(() => {});
    if (result === 'NO_SHOW') {
      setAsking(true);
    } else {
      setAsking(false);
      void onChange(result);
    }
  }

  return (
    <View style={{ gap: space(3) }}>
      <View accessibilityRole="radiogroup" style={[styles.row, { gap: space(2) }]}>
        {RESULTS.map((result) => {
          const selected = result.value === value;
          return (
            <Pressable
              key={result.value}
              accessibilityRole="radio"
              accessibilityLabel={`${target} ${result.label}`}
              accessibilityState={{ checked: selected, disabled: loading }}
              disabled={loading}
              onPress={() => choose(result.value)}
              style={[
                styles.item,
                {
                  gap: space(2),
                  borderRadius: radius.pill,
                  borderColor: selected ? colors.text : colors.border,
                },
              ]}
            >
              <Hold on={selected} />
              <AppText variant="label" maxFontSizeMultiplier={1.4}>
                {result.label}
              </AppText>
            </Pressable>
          );
        })}
      </View>
      {asking && value !== 'NO_SHOW' ? (
        <ConfirmPanel
          target={target}
          confirmLabel="노쇼 확정"
          message="블랙리스트에 1회 기록돼요."
          loading={loading}
          onConfirm={async () => {
            if ((await onChange('NO_SHOW')) && !left.current) {
              setAsking(false);
            }
          }}
          onBack={() => setAsking(false)}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap' },
  item: {
    minHeight: 48,
    paddingHorizontal: 16,
    borderWidth: 1.5,
    flexDirection: 'row',
    alignItems: 'center',
  },
});
