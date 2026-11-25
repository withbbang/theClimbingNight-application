import { useEffect, useRef, type ReactElement } from 'react';
import { StyleSheet, View } from 'react-native';

import { FlashList } from '@shopify/flash-list';
import type { UseInfiniteQueryResult } from '@tanstack/react-query';

import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { QueryState } from '@/components/ui/QueryState';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

interface PagedListProps<T> {
  query: UseInfiniteQueryResult<T[]>;
  label: string;
  keyOf: (item: T) => number;
  renderItem: (item: T, index: number) => ReactElement;
  empty: ReactElement;
  header?: ReactElement;
}

/** 끝에 닿으면 다음 페이지를 이어 받는 목록. */
export function PagedList<T>({
  query,
  label,
  keyOf,
  renderItem,
  empty,
  header,
}: PagedListProps<T>) {
  const { space } = useTheme();
  const { fetchNextPage, hasNextPage, isFetching } = query;
  // 다시 받는 중에 끝에 닿으면 목록은 다시 알려 주지 않으므로 기억했다가 이어 받는다.
  const reachedEnd = useRef(false);

  useEffect(() => {
    if (reachedEnd.current && !isFetching) {
      reachedEnd.current = false;
      if (hasNextPage) {
        void fetchNextPage();
      }
    }
  }, [fetchNextPage, hasNextPage, isFetching]);

  return (
    <View style={styles.fill}>
      <QueryState query={query}>
        {(items) => (
          <FlashList
            accessibilityLabel={label}
            data={items}
            keyExtractor={(item) => String(keyOf(item))}
            renderItem={({ item, index }) => renderItem(item, index)}
            onEndReached={() => {
              if (query.isFetchingNextPage) {
                return;
              }
              if (isFetching) {
                reachedEnd.current = true;
              } else if (hasNextPage) {
                void fetchNextPage();
              }
            }}
            ListHeaderComponent={header}
            ListFooterComponent={
              query.isFetchNextPageError ? (
                <View style={{ gap: space(2) }}>
                  <ErrorBanner message={errorMessage(query.error)} />
                  <Button
                    label="다시 시도"
                    variant="secondary"
                    onPress={() => void fetchNextPage()}
                  />
                </View>
              ) : null
            }
            ListEmptyComponent={empty}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
            refreshControl={<PullToRefresh onRefresh={query.refetch} />}
          />
        )}
      </QueryState>
    </View>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
