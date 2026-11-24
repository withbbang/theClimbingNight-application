import { useCallback, useRef, useState } from 'react';
import { AppState, Linking, View } from 'react-native';

import { useFocusEffect } from 'expo-router';
import * as Notifications from 'expo-notifications';

import { RoleGate } from '@/components/auth/RoleGate';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { Toggle } from '@/components/ui/Toggle';
import {
  useNotificationSettings,
  useUpdateNotificationSettings,
} from '@/hooks/use-notification-settings';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import { registerPushToken } from '@/lib/push';

/** 화면을 열 때와 기기 설정에서 돌아올 때 다시 확인한다. 그사이 허용했으면 이 기기를 등록한다. */
function usePermissionDenied() {
  const [denied, setDenied] = useState(false);
  const wasDenied = useRef(false);
  useFocusEffect(
    useCallback(() => {
      const check = () => {
        Notifications.getPermissionsAsync()
          .then(({ granted }) => {
            if (granted && wasDenied.current) {
              void registerPushToken();
            }
            wasDenied.current = !granted;
            setDenied(!granted);
          })
          .catch(() => {});
      };
      check();
      const subscription = AppState.addEventListener('change', (state) => {
        if (state === 'active') {
          check();
        }
      });
      return () => subscription.remove();
    }, []),
  );
  return denied;
}

function Settings() {
  const { space } = useTheme();
  const denied = usePermissionDenied();
  const query = useNotificationSettings();
  const update = useUpdateNotificationSettings();

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0}>
        <AppText variant="display" accessibilityRole="header">
          알림 설정
        </AppText>
      </FadeInItem>
      {denied ? (
        <FadeInItem index={1} style={{ gap: space(3) }}>
          <AppText>기기에서 알림이 꺼져 있어요. 켜야 알림을 받을 수 있어요.</AppText>
          <Button
            label="기기 설정 열기"
            variant="secondary"
            onPress={() => void Linking.openSettings().catch(() => {})}
          />
        </FadeInItem>
      ) : null}
      <QueryState query={query}>
        {(saved) => {
          const shown = update.isPending && update.variables ? update.variables : saved;
          return (
            <View style={{ gap: space(2) }}>
              <FadeInItem index={1}>
                <Toggle
                  label="새 벙"
                  description="벙이 새로 열리면 알려 드려요."
                  value={shown.newMeetup}
                  disabled={update.isPending}
                  onChange={(newMeetup) => void update.submit({ ...saved, newMeetup })}
                />
              </FadeInItem>
              <FadeInItem index={2}>
                <Toggle
                  label="새 공지"
                  description="공지가 올라오면 알려 드려요."
                  value={shown.newNotice}
                  disabled={update.isPending}
                  onChange={(newNotice) => void update.submit({ ...saved, newNotice })}
                />
              </FadeInItem>
              <FadeInItem index={3}>
                <AppText variant="caption" tone="muted">
                  신청한 벙의 확정, 취소, 일정과 문의 답변, 인증 결과는 항상 알려 드려요.
                </AppText>
              </FadeInItem>
            </View>
          );
        }}
      </QueryState>
      {update.error ? <ErrorBanner message={errorMessage(update.error)} /> : null}
    </View>
  );
}

export default function NotificationSettingsScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <Settings />
      </RoleGate>
    </Screen>
  );
}
