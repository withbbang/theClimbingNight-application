import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { launchImageLibraryAsync } from 'expo-image-picker';
import { useRouter } from 'expo-router';

import Animated, { FadeIn, FadeOut, useReducedMotion } from 'react-native-reanimated';

import { RoleGate } from '@/components/auth/RoleGate';
import { BodyField, isBodyTooLong } from '@/components/feed/BodyField';
import { PhotoPickerStrip } from '@/components/feed/PhotoPickerStrip';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { Hold } from '@/components/ui/Hold';
import { Screen } from '@/components/ui/Screen';
import { useCreatePost, type UploadProgress } from '@/hooks/use-create-post';
import { useLeft } from '@/hooks/use-left';
import { useTheme } from '@/hooks/use-theme';
import { createErrorMessage } from '@/lib/error-message';
import { PhotoError, preparePhoto, type PreparedPhoto } from '@/lib/upload-photos';

/** 한 게시물에 올릴 수 있는 사진 수. */
const MAX_PHOTOS = 10;

/** 올린 사진만큼 홀드가 켜진다. */
function UploadHolds({ progress }: { progress: UploadProgress }) {
  const { space } = useTheme();
  const reduced = useReducedMotion();
  return (
    <Animated.View
      entering={reduced ? undefined : FadeIn.duration(200)}
      exiting={reduced ? undefined : FadeOut.duration(160)}
      style={{ gap: space(2) }}
    >
      <AppText variant="label" accessibilityLiveRegion="polite">
        {`사진 올리는 중 ${progress.done}/${progress.total}`}
      </AppText>
      <View
        style={[styles.row, { gap: space(2) }]}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {Array.from({ length: progress.total }, (_, i) => (
          <Hold key={i} on={i < progress.done} size={18} />
        ))}
      </View>
    </Animated.View>
  );
}

function NewPostForm() {
  const router = useRouter();
  const { space } = useTheme();
  const left = useLeft();
  const create = useCreatePost();
  const [photos, setPhotos] = useState<PreparedPhoto[]>([]);
  const [body, setBody] = useState('');
  const [preparing, setPreparing] = useState(false);
  const [pickError, setPickError] = useState<string | null>(null);
  const locked = create.isPending || create.isSuccess;

  async function pick() {
    const remaining = MAX_PHOTOS - photos.length;
    setPreparing(true);
    setPickError(null);
    try {
      const result = await launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsMultipleSelection: true,
        selectionLimit: remaining,
        orderedSelection: true,
        // 다시 줄여 저장하므로 고를 때는 압축하지 않는다.
        quality: 1,
      });
      if (result.canceled) {
        return;
      }
      // 한 장씩 줄여서 다 되는 대로 줄에 넣는다. 고른 순서를 지킨다.
      for (const asset of result.assets.slice(0, remaining)) {
        const photo = await preparePhoto(asset);
        if (left.current) {
          return;
        }
        setPhotos((current) => [...current, photo].slice(0, MAX_PHOTOS));
      }
    } catch (error) {
      if (!left.current) {
        setPickError(
          error instanceof PhotoError
            ? error.message
            : '사진을 불러오지 못했어요. 다른 사진을 골라 주세요.',
        );
      }
    } finally {
      if (!left.current) {
        setPreparing(false);
      }
    }
  }

  async function submit() {
    if ((await create.submit(photos, body.trim())) && !left.current) {
      router.dismissTo('/feed');
    }
  }

  const error = create.error
    ? create.error instanceof PhotoError
      ? create.error.message
      : createErrorMessage(create.error)
    : null;

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0} style={{ gap: space(1) }}>
        <AppText variant="display" accessibilityRole="header">
          사진 올리기
        </AppText>
        <AppText tone="muted">오늘 오른 벽을 남겨 보세요.</AppText>
      </FadeInItem>
      <FadeInItem index={1}>
        <PhotoPickerStrip
          photos={photos}
          max={MAX_PHOTOS}
          preparing={preparing}
          locked={locked}
          onPick={() => void pick()}
          onRemove={(index) => setPhotos((current) => current.filter((_, i) => i !== index))}
        />
      </FadeInItem>
      {pickError ? <ErrorBanner message={pickError} /> : null}
      <FadeInItem index={2}>
        <BodyField value={body} onChangeText={setBody} editable={!locked} />
      </FadeInItem>
      {create.progress ? <UploadHolds progress={create.progress} /> : null}
      {error ? <ErrorBanner message={error} /> : null}
      <FadeInItem index={3}>
        <Button
          label={error ? '다시 시도' : '올리기'}
          loading={locked}
          disabled={photos.length === 0 || preparing || isBodyTooLong(body)}
          onPress={() => void submit()}
        />
      </FadeInItem>
    </View>
  );
}

export default function NewPostScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER" description="로그인하면 사진을 올릴 수 있어요.">
        <NewPostForm />
      </RoleGate>
    </Screen>
  );
}

const styles = StyleSheet.create({ row: { flexDirection: 'row', flexWrap: 'wrap' } });
