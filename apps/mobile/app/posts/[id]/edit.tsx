import { useState } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { ImageBrokenIcon } from 'phosphor-react-native/src/icons/ImageBroken';

import { RoleGate } from '@/components/auth/RoleGate';
import { BodyField, isBodyTooLong } from '@/components/feed/BodyField';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';
import { useLeft } from '@/hooks/use-left';
import { usePostForEdit, useUpdatePost } from '@/hooks/use-my-posts';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { parseId } from '@/lib/parse-id';
import type { PostResponse } from '@/types/api/post';

/** 본문만 고친다. 사진은 바꿀 수 없다. */
function EditBody({ post }: { post: PostResponse }) {
  const router = useRouter();
  const { space } = useTheme();
  const left = useLeft();
  const save = useUpdatePost(post.id);
  const [body, setBody] = useState(post.body);
  const locked = save.isPending || save.isSuccess;

  async function submit() {
    if ((await save.submit(body.trim())) && !left.current) {
      router.back();
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      <FadeInItem index={0} style={{ gap: space(1) }}>
        <AppText variant="display" accessibilityRole="header">
          글 고치기
        </AppText>
        <AppText tone="muted">사진은 그대로 두고 본문만 고칠 수 있어요.</AppText>
      </FadeInItem>
      <FadeInItem index={1}>
        <BodyField value={body} onChangeText={setBody} editable={!locked} />
      </FadeInItem>
      {save.error ? <ErrorBanner message={errorMessage(save.error)} /> : null}
      <FadeInItem index={2}>
        <Button
          label="저장"
          loading={locked}
          disabled={isBodyTooLong(body)}
          onPress={() => void submit()}
        />
      </FadeInItem>
    </View>
  );
}

function NotEditable() {
  const router = useRouter();
  return (
    <Notice
      illustration={<FloatingIcon icon={ImageBrokenIcon} />}
      title="고칠 수 없는 글이에요"
      description="지워졌거나 내 글이 아니에요."
      actionLabel="피드로"
      onAction={() => router.dismissTo('/feed')}
    />
  );
}

export default function EditPostScreen() {
  const id = parseId(useLocalSearchParams().id);
  const query = usePostForEdit(id);
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="USER">
        {id === null || isNotFound(query.error) ? (
          <NotEditable />
        ) : (
          <FreshQuery query={query}>
            {(post) => (post.mine ? <EditBody post={post} /> : <NotEditable />)}
          </FreshQuery>
        )}
      </RoleGate>
    </Screen>
  );
}
