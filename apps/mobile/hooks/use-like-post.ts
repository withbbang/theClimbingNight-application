import { useCallback } from 'react';

import { useQueryClient, type QueryClient } from '@tanstack/react-query';

import { notifyError } from '@/lib/haptics';
import { likePost, unlikePost } from '@/lib/post-api';
import type { PostResponse } from '@/types/api/post';

interface Settled {
  likedByMe: boolean;
  likeCount: number;
}

/** 같은 글에서 진행 중인 좋아요 요청. 앱 전체에서 글 하나에 하나만 둔다. */
interface Flight {
  /** 마지막으로 누른 뜻. 요청이 끝나면 이것과 비교한다. */
  wanted: boolean;
  /** 서버가 마지막으로 확정한 값. 실패하면 여기로 되돌린다. */
  settled: Settled;
}

const flights = new WeakMap<QueryClient, Map<number, Flight>>();

function flightsOf(client: QueryClient) {
  let map = flights.get(client);
  if (!map) {
    map = new Map();
    flights.set(client, map);
  }
  return map;
}

function isPost(value: unknown, id: number): value is PostResponse {
  return typeof value === 'object' && value !== null && (value as { id?: unknown }).id === id;
}

/** 피드 페이지 묶음, 글 하나, 글 배열 어느 모양이든 이 글만 고친다. */
function patch(data: unknown, id: number, change: (post: PostResponse) => PostResponse): unknown {
  if (Array.isArray(data)) {
    return data.map((item) => (isPost(item, id) ? change(item) : item));
  }
  if (isPost(data, id)) {
    return change(data);
  }
  const pages = (data as { pages?: unknown } | undefined)?.pages;
  if (Array.isArray(pages)) {
    return {
      ...(data as object),
      pages: pages.map((page: { items?: unknown }) =>
        Array.isArray(page?.items) ? { ...page, items: patch(page.items, id, change) } : page,
      ),
    };
  }
  return data;
}

function write(client: QueryClient, id: number, change: (post: PostResponse) => PostResponse) {
  client.setQueriesData({ queryKey: ['posts'] }, (data: unknown) => patch(data, id, change));
}

/**
 * 하트를 누르면 캐시를 바로 고치고 요청은 뒤에서 보낸다.
 * 같은 글의 요청이 진행 중이면 뜻만 기록해 두고, 끝난 뒤 최종 상태가 다르면 한 번 더 보낸다.
 */
export function useLikePost() {
  const client = useQueryClient();

  const setLiked = useCallback(
    (target: PostResponse, liked: boolean) => {
      const id = target.id;
      const running = flightsOf(client);
      const current = running.get(id);

      write(client, id, (post) => ({
        ...post,
        likedByMe: liked,
        likeCount:
          post.likedByMe === liked ? post.likeCount : Math.max(0, post.likeCount + (liked ? 1 : -1)),
      }));

      if (current) {
        current.wanted = liked;
        return;
      }
      if (target.likedByMe === liked) {
        return;
      }

      const flight: Flight = {
        wanted: liked,
        settled: { likedByMe: target.likedByMe, likeCount: target.likeCount },
      };
      running.set(id, flight);

      void (async () => {
        try {
          let sent = liked;
          for (;;) {
            const result = await (sent ? likePost(id) : unlikePost(id));
            flight.settled = result;
            if (flight.wanted === sent) {
              write(client, id, (post) => ({ ...post, ...result }));
              return;
            }
            sent = flight.wanted;
          }
        } catch {
          const back = flight.settled;
          write(client, id, (post) => ({ ...post, ...back }));
          notifyError();
        } finally {
          running.delete(id);
        }
      })();
    },
    [client],
  );

  return { setLiked };
}
