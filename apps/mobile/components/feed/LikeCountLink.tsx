import { Pressable } from 'react-native';

import { useRouter } from 'expo-router';

import Animated from 'react-native-reanimated';

import { AppText } from '@/components/ui/AppText';
import { usePressScale } from '@/hooks/use-press-scale';

/** "좋아요 n개". 누르면 누른 사람 목록으로 간다. 하나도 없으면 보이지 않는다. */
export function LikeCountLink({ postId, count }: { postId: number; count: number }) {
  const router = useRouter();
  const press = usePressScale(0.96);
  if (count <= 0) {
    return null;
  }
  return (
    <Animated.View style={[press.style, { alignSelf: 'flex-start' }]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`좋아요 ${count}개`}
        hitSlop={8}
        onPress={() => router.push(`/posts/${postId}/likes`)}
        {...press.handlers}
        style={{ minHeight: 44, justifyContent: 'center' }}
      >
        <AppText variant="label">좋아요 {count}개</AppText>
      </Pressable>
    </Animated.View>
  );
}
