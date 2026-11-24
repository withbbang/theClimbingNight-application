import { useEffect } from 'react';

import { useRouter } from 'expo-router';
import * as Notifications from 'expo-notifications';

import { useSession } from '@/hooks/use-session';
import { registerPushToken } from '@/lib/push';
import { queryKeysForPush, routeForPush } from '@/lib/push-route';
import { queryClient } from '@/lib/query-client';

function refetchFor(data: unknown) {
  for (const queryKey of queryKeysForPush(data)) {
    void queryClient.invalidateQueries({ queryKey });
  }
}

export function usePush() {
  const router = useRouter();
  const session = useSession();
  const userId = session.status === 'authenticated' ? session.tokens.user.id : null;
  const ready = session.status !== 'loading';
  const response = Notifications.useLastNotificationResponse();

  useEffect(() => {
    if (userId !== null) {
      void registerPushToken();
    }
  }, [userId]);

  useEffect(() => {
    const subscription = Notifications.addNotificationReceivedListener((notification) =>
      refetchFor(notification.request.content.data),
    );
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!ready || !response) {
      return;
    }
    Notifications.clearLastNotificationResponse();
    const data: unknown = response.notification.request.content.data;
    refetchFor(data);
    const route = routeForPush(data);
    if (route) {
      // 로그인이나 다른 상세가 떠 있으면 걷어내고 간다.
      if (router.canDismiss()) {
        router.dismissAll();
      }
      router.navigate(route);
    }
  }, [ready, response, router]);
}
