import { useSession } from '@/hooks/use-session';
import type { Role } from '@/types/api/user';

/** 로그인하지 않았으면 null. */
export function useRole(): Role | null {
  const session = useSession();
  return session.status === 'authenticated' ? session.tokens.user.role : null;
}

export function useIsMember() {
  const role = useRole();
  return role === 'MEMBER' || role === 'ADMIN';
}
