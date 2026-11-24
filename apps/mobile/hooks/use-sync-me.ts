import { useEffect } from 'react';
import { AppState } from 'react-native';

import { focusManager, useQuery } from '@tanstack/react-query';

import { useSession } from '@/hooks/use-session';
import { authApi } from '@/lib/auth-api';
import { sessionStore } from '@/lib/session-store';

/** 시작할 때와 앱으로 돌아올 때 내 정보를 다시 받아 승인된 등급을 반영한다. */
export function useSyncMe() {
  const session = useSession();
  const { data, dataUpdatedAt } = useQuery({
    queryKey: ['users', 'me'],
    queryFn: authApi.getMe,
    enabled: session.status === 'authenticated',
  });

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      focusManager.setFocused(state === 'active');
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (data) {
      void sessionStore.updateUser(data);
    }
    // 내용이 같으면 data 참조가 그대로라, 다시 받은 시각으로 반영한다.
  }, [data, dataUpdatedAt]);
}
