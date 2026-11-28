import { useQuery } from '@tanstack/react-query';

import { useCursorQuery } from '@/hooks/use-cursor-query';
import { useRole } from '@/hooks/use-role';
import { profileApi } from '@/lib/profile-api';

export const profileKey = (id: number) => ['users', id, 'profile'];

/** 사용자 프로필. 내 프로필도 내 id로 같은 키를 쓴다. */
export function useUserProfile(id: number | null) {
  const role = useRole();
  return useQuery({
    queryKey: profileKey(id ?? 0),
    queryFn: () => profileApi.profile(id as number),
    enabled: role !== null && id !== null,
  });
}

/** 프로필 사진 격자. 커서로 18개씩. */
export function useUserPosts(id: number | null) {
  const role = useRole();
  return useCursorQuery(
    ['users', id, 'posts'],
    (cursor) => profileApi.posts(id as number, cursor),
    role !== null && id !== null,
  );
}
