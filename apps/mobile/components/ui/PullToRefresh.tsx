import { useState } from 'react';
import { RefreshControl, type RefreshControlProps } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

interface PullToRefreshProps extends Omit<RefreshControlProps, 'refreshing' | 'onRefresh'> {
  onRefresh: () => Promise<unknown>;
}

// ScrollView가 Android에서 자식을 넣어 주므로 나머지 props를 그대로 넘긴다.
export function PullToRefresh({ onRefresh, ...props }: PullToRefreshProps) {
  const { colors } = useTheme();
  const [refreshing, setRefreshing] = useState(false);
  return (
    <RefreshControl
      {...props}
      refreshing={refreshing}
      tintColor={colors.accent}
      colors={[colors.text]}
      progressBackgroundColor={colors.surface}
      onRefresh={() => {
        setRefreshing(true);
        void onRefresh().finally(() => setRefreshing(false));
      }}
    />
  );
}
