import { Pressable, StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { CaretRightIcon } from 'phosphor-react-native/src/icons/CaretRight';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { BioForm } from '@/components/profile/BioForm';
import { ProfilePhotoEditor } from '@/components/profile/ProfilePhotoEditor';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { Screen } from '@/components/ui/Screen';
import { useLeft } from '@/hooks/use-left';
import { useSession } from '@/hooks/use-session';
import { useTheme } from '@/hooks/use-theme';
import { useUserProfile } from '@/hooks/use-user-profile';

/** 닉네임은 기존 닉네임 변경 화면에서 바꾼다. 누르는 동안 화살표가 갈 곳을 가리킨다. */
function NicknameRow({ nickname }: { nickname: string }) {
  const router = useRouter();
  const { colors, motion, radius, space } = useTheme();
  const reduced = useReducedMotion();
  const pull = useSharedValue(0);
  const arrow = useAnimatedStyle(() => ({ transform: [{ translateX: 6 * pull.get() }] }));

  function pullTo(value: number) {
    if (!reduced) {
      pull.set(withSpring(value, motion.spring));
    }
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`닉네임 ${nickname}`}
      accessibilityHint="닉네임 변경 화면으로 가요"
      onPress={() => router.push('/settings/nickname')}
      onPressIn={() => pullTo(1)}
      onPressOut={() => pullTo(0)}
      style={[
        styles.row,
        {
          gap: space(3),
          paddingHorizontal: space(4),
          borderRadius: radius.input,
          backgroundColor: colors.surface,
        },
      ]}
    >
      <AppText variant="label">닉네임</AppText>
      <AppText tone="muted" numberOfLines={1} style={styles.value}>
        {nickname}
      </AppText>
      <Animated.View style={arrow}>
        <CaretRightIcon size={20} color={colors.textMuted} weight="bold" />
      </Animated.View>
    </Pressable>
  );
}

function ProfileEditForm() {
  const router = useRouter();
  const { space } = useTheme();
  const session = useSession();
  const left = useLeft();
  const me = session.status === 'authenticated' ? session.tokens.user : null;
  const query = useUserProfile(me?.id ?? null, { fresh: true });
  const nickname = me?.nickname ?? '';

  return (
    <View style={{ gap: space(6) }}>
      <FadeInItem index={0}>
        <AppText variant="display" accessibilityRole="header">
          프로필 편집
        </AppText>
      </FadeInItem>
      {/* 사진은 적용되면 새로 받은 값을 바로 보여 준다. 자기소개 입력만 처음 값으로 한 번 채운다. */}
      <FadeInItem index={1}>
        <ProfilePhotoEditor
          nickname={nickname}
          imageUrl={query.data?.profileImage?.largeUrl ?? null}
        />
      </FadeInItem>
      <FadeInItem index={2}>
        <NicknameRow nickname={nickname} />
      </FadeInItem>
      <FadeInItem index={3}>
        <FreshQuery query={query}>
          {(profile) => (
            <BioForm
              initial={profile.bio}
              onSaved={() => {
                if (left.current) {
                  return;
                }
                if (router.canGoBack()) {
                  router.back();
                } else if (me) {
                  router.replace(`/users/${me.id}`);
                }
              }}
            />
          )}
        </FreshQuery>
      </FadeInItem>
    </View>
  );
}

export default function ProfileEditScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        <ProfileEditForm />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center' },
  value: { flex: 1, textAlign: 'right' },
});
