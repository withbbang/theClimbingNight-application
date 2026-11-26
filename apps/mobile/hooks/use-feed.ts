import { useCursorQuery } from '@/hooks/use-cursor-query';
import { fetchFeed } from '@/lib/post-api';

const FEED_PAGE_SIZE = 10;

export function useFeed() {
  return useCursorQuery(['posts'], (cursor) => fetchFeed(cursor, FEED_PAGE_SIZE), true);
}
