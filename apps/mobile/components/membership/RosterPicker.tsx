import { useState } from 'react';
import { View } from 'react-native';

import Animated, { FadeInDown, useReducedMotion } from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { QueryState } from '@/components/ui/QueryState';
import { RadioRow } from '@/components/ui/RadioRow';
import { TextField } from '@/components/ui/TextField';
import { useDebounced } from '@/hooks/use-debounced';
import { useRosterSearch } from '@/hooks/use-membership-admin';
import { useTheme } from '@/hooks/use-theme';
import type { RosterCandidateResponse } from '@/types/api/roster';

/** 낭독기가 읽는 명부 항목 이름. */
export const rosterLabel = (target: string, roster: RosterCandidateResponse) =>
  `${target} 명부: ${roster.somoimNickname}${roster.note ? `, ${roster.note}` : ''}`;

interface RosterPickerProps {
  target: string;
  /** 카드에 이미 보이는 명부. 결과에서 뺀다. */
  shownIds: number[];
  disabled: boolean;
  onSelect: (roster: RosterCandidateResponse) => void;
}

/** 닉네임이 바뀐 사람을 위해 연결되지 않은 명부를 찾는다. */
export function RosterPicker({ target, shownIds, disabled, onSelect }: RosterPickerProps) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  const [text, setText] = useState('');
  const q = useDebounced(text.trim());
  const query = useRosterSearch(q);

  return (
    <Animated.View
      entering={reduced ? undefined : FadeInDown.springify().damping(18)}
      style={{ gap: space(2) }}
    >
      <TextField
        label="명부 검색"
        value={text}
        onChangeText={setText}
        maxLength={50}
        editable={!disabled}
        autoCorrect={false}
      />
      {q === '' ? null : (
        <QueryState query={query}>
          {({ items, totalPages }) => (
            <View>
              {items.length === 0 ? <AppText tone="muted">찾는 명부가 없어요</AppText> : null}
              {items
                .filter((roster) => !shownIds.includes(roster.id))
                .map((roster) => (
                  <RadioRow
                    key={roster.id}
                    label={roster.somoimNickname}
                    caption={roster.note}
                    accessibilityLabel={rosterLabel(target, roster)}
                    selected={false}
                    disabled={disabled}
                    onPress={() => onSelect(roster)}
                  />
                ))}
              {totalPages > 1 ? (
                <AppText variant="caption" tone="muted">
                  더 있어요. 닉네임을 더 적어 좁혀 보세요.
                </AppText>
              ) : null}
            </View>
          )}
        </QueryState>
      )}
    </Animated.View>
  );
}
