import { useState, type ReactElement } from 'react';
import { StyleSheet, View } from 'react-native';

import { FlashList } from '@shopify/flash-list';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GridCell } from '@/components/profile/GridCell';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { useTheme } from '@/hooks/use-theme';
import type { GridPostResponse } from '@/types/api/profile';

const COLUMNS = 3;
/** 칸 사이. 사진이 붙어 보이되 경계는 보이게 한다. */
export const GRID_GAP = 2;

interface Row {
  key: string;
  start: number;
  posts: GridPostResponse[];
}

function toRows(posts: GridPostResponse[]): Row[] {
  const rows: Row[] = [];
  for (let start = 0; start < posts.length; start += COLUMNS) {
    const slice = posts.slice(start, start + COLUMNS);
    rows.push({ key: slice.map((post) => post.id).join('-'), start, posts: slice });
  }
  return rows;
}

interface ProfileGridProps {
  posts: GridPostResponse[];
  header: ReactElement;
  /** 칸이 하나도 없을 때(불러오는 중·실패·빈 격자). */
  empty: ReactElement | null;
  footer?: ReactElement | null;
  onEndReached: () => void;
  onRefresh: () => Promise<unknown>;
}

/** 프로필 화면 전체 목록. 머리 아래에 3열 정사각 격자를 3칸씩 묶은 줄로 그린다. */
export function ProfileGrid({
  posts,
  header,
  empty,
  footer = null,
  onEndReached,
  onRefresh,
}: ProfileGridProps) {
  const { space } = useTheme();
  const insets = useSafeAreaInsets();
  // 이 화면에서 이미 나타난 칸. 재활용돼 다시 그려지는 칸이 다시 나타나는 연출을 하지 않게 한다.
  const [shown] = useState(() => new Set<number>());
  return (
    <FlashList
      accessibilityLabel="게시물 격자"
      data={toRows(posts)}
      keyExtractor={(row) => row.key}
      renderItem={({ item }) => (
        <View testID="grid-row" style={[styles.row, { gap: GRID_GAP, marginBottom: GRID_GAP }]}>
          {item.posts.map((post, i) => (
            <GridCell key={post.id} post={post} index={item.start + i} shown={shown} />
          ))}
          {/* 마지막 줄이 덜 차도 칸 크기가 같게 빈자리를 채운다. */}
          {Array.from({ length: COLUMNS - item.posts.length }, (_, i) => (
            <View key={`pad-${i}`} style={styles.pad} />
          ))}
        </View>
      )}
      onEndReached={onEndReached}
      ListHeaderComponent={header}
      ListEmptyComponent={empty}
      ListFooterComponent={footer}
      contentContainerStyle={{ paddingBottom: insets.bottom + space(4) }}
      showsVerticalScrollIndicator={false}
      refreshControl={<PullToRefresh onRefresh={onRefresh} />}
    />
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row' },
  pad: { flex: 1 },
});
