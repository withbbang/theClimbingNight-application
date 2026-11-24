import { useEffect } from 'react';

import { useRouter } from 'expo-router';
import * as Notifications from 'expo-notifications';

import { useSession } from '@/hooks/use-session';
import { registerPushToken } from '@/lib/push';
import { isMembershipPush, routeForPush } from '@/lib/push-route';
import { queryClient } from '@/lib/query-client';

function syncMembership(data: unknown) {
  if (isMembershipPush(data)) {
    void queryClient.invalidateQueries({ queryKey: ['users', 'me'] });
    void queryClient.invalidateQueries({ queryKey: ['membership-requests'] });
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
      syncMembership(notification.request.content.data),
    );
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!ready || !response) {
      return;
    }
    Notifications.clearLastNotificationResponse();
    const data: unknown = response.notification.request.content.data;
    syncMembership(data);
    const route = routeForPush(data);
    if (route) {
      // 로그인 화면이 떠 있으면 걷어내고 간다. 탭 안에서는 dismissTo가 아무 일도 하지 않는다.
      if (router.canDismiss()) {
        router.dismissTo(route);
      } else {
        router.navigate(route);
      }
    }
  }, [ready, response, router]);
}
