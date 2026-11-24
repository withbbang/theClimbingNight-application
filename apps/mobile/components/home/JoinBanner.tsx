import { useState } from 'react';
import { Linking, View } from 'react-native';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { useAppSettings } from '@/hooks/use-home';
import { useIsMember } from '@/hooks/use-role';
import { useTheme } from '@/hooks/use-theme';

/** 아직 동호회원이 아닌 사람을 소모임으로 안내한다. */
export function JoinBanner() {
  const { space } = useTheme();
  const isMember = useIsMember();
  const url = useAppSettings().data?.somoimJoinUrl;
  const [failed, setFailed] = useState(false);
  if (isMember || !url) {
    return null;
  }

  function open(link: string) {
    setFailed(false);
    Linking.openURL(link).catch(() => setFailed(true));
  }

  return (
    <FadeInItem index={1} style={{ gap: space(3) }}>
      <View style={{ gap: space(1) }}>
        <AppText variant="title" accessibilityRole="header">
          같이 오를까요?
        </AppText>
        <AppText tone="muted">가입은 소모임에서 받아요. 가입한 뒤 앱에서 인증하면 돼요.</AppText>
      </View>
      <Button label="동호회 가입하기" onPress={() => open(url)} />
      {failed ? <ErrorBanner message="링크를 열지 못했어요." /> : null}
    </FadeInItem>
  );
}
