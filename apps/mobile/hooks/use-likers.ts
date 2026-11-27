import { useCursorQuery } from '@/hooks/use-cursor-query';
import { fetchLikers } from '@/lib/post-api';

const LIKERS_PAGE_SIZE = 20;

/** 게시물에 좋아요를 누른 사람, 최근 순. 좋아요 수 캐시 갱신 범위 밖의 키다. */
export function useLikers(postId: number | null) {
  return useCursorQuery(
    ['posts', postId, 'likes'],
    (cursor) => fetchLikers(postId as number, cursor, LIKERS_PAGE_SIZE),
    postId !== null,
    (liker) => liker.userId,
  );
}
