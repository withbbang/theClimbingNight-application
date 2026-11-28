import { StyleSheet, View } from 'react-native';

import { useLocalSearchParams } from 'expo-router';

import { HeartIcon } from 'phosphor-react-native/src/icons/Heart';

import { AppText } from '@/components/ui/AppText';
import { AuthorLink } from '@/components/ui/AuthorLink';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { Screen } from '@/components/ui/Screen';
import { useLikers } from '@/hooks/use-likers';
import { useTheme } from '@/hooks/use-theme';
import { parseId } from '@/lib/parse-id';

/** 처음 화면에 들어오는 줄만 차례로 올라온다. 스크롤로 만나는 줄은 바로 올라온다. */
const STAGGERED = 8;

export default function PostLikesScreen() {
  const { space, colors, radius } = useTheme();
  const query = useLikers(parseId(useLocalSearchParams().id));

  return (
    <Screen>
      <BackButton />
      <AppText variant="title" accessibilityRole="header" style={{ marginBottom: space(3) }}>
        좋아요
      </AppText>
      <PagedList
        query={query}
        label="좋아요 누른 사람"
        keyOf={(liker) => liker.userId}
        renderItem={(liker, index) => (
          <FadeInItem
            index={index < STAGGERED ? index : 0}
            style={[
              styles.row,
              {
                gap: space(3),
                padding: space(3),
                marginBottom: space(2),
                borderRadius: radius.card,
                backgroundColor: colors.surface,
              },
            ]}
          >
            <AuthorLink
              userId={liker.userId}
              nickname={liker.nickname}
              imageUrl={liker.profileImageUrl}
            />
            <View style={styles.fill}>
              <AuthorLink
                userId={liker.userId}
                nickname={liker.nickname}
                imageUrl={liker.profileImageUrl}
              >
                <AppText variant="label" numberOfLines={1} testID="liker-name">
                  {liker.nickname}
                </AppText>
              </AuthorLink>
            </View>
          </FadeInItem>
        )}
        empty={
          <Notice
            illustration={<FloatingIcon icon={HeartIcon} />}
            title="아직 좋아요가 없어요"
            description="첫 번째로 좋아요를 눌러 보세요."
          />
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 56, flexDirection: 'row', alignItems: 'center' },
  fill: { flex: 1 },
});
