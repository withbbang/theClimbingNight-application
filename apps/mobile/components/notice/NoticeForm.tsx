import { useState } from 'react';
import { View } from 'react-native';

import { useRouter } from 'expo-router';

import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { TextField } from '@/components/ui/TextField';
import { Toggle } from '@/components/ui/Toggle';
import { useLeft } from '@/hooks/use-left';
import { useSaveNotice } from '@/hooks/use-notice-admin';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';
import type { NoticeResponse } from '@/types/api/notice';

interface NoticeFormProps {
  /** 없으면 새 공지. */
  notice?: NoticeResponse;
  /** 지우는 중처럼 저장하면 안 될 때. */
  locked?: boolean;
}

export function NoticeForm({ notice, locked = false }: NoticeFormProps) {
  const router = useRouter();
  const { space } = useTheme();
  const [title, setTitle] = useState(notice?.title ?? '');
  const [body, setBody] = useState(notice?.body ?? '');
  const [pinned, setPinned] = useState(notice?.pinned ?? false);
  const [tried, setTried] = useState(false);
  const left = useLeft();
  const save = useSaveNotice(notice?.id ?? null);
  const editable = !save.isPending && !locked;

  async function submit() {
    setTried(true);
    const request = { title: title.trim(), body: body.trim(), pinned };
    if (request.title && request.body && (await save.submit(request)) && !left.current) {
      router.dismissTo('/admin/notices');
    }
  }

  return (
    <View style={{ gap: space(5) }}>
      {notice ? null : (
        <FadeInItem index={1}>
          <AppText tone="muted">올리면 회원에게 알림이 가요.</AppText>
        </FadeInItem>
      )}
      <FadeInItem index={2}>
        <TextField
          label="제목"
          value={title}
          onChangeText={setTitle}
          maxLength={100}
          editable={editable}
          error={tried && !title.trim() ? '제목을 입력해 주세요.' : null}
        />
      </FadeInItem>
      <FadeInItem index={3}>
        <TextField
          label="본문 (마크다운)"
          value={body}
          onChangeText={setBody}
          maxLength={10000}
          multiline
          editable={editable}
          error={tried && !body.trim() ? '본문을 입력해 주세요.' : null}
        />
      </FadeInItem>
      <FadeInItem index={4}>
        <Toggle label="맨 위에 고정" value={pinned} disabled={!editable} onChange={setPinned} />
      </FadeInItem>
      {save.error ? <ErrorBanner message={errorMessage(save.error)} /> : null}
      <FadeInItem index={5}>
        <Button
          label={notice ? '저장' : '올리기'}
          loading={save.isPending}
          disabled={locked}
          onPress={() => void submit()}
        />
      </FadeInItem>
    </View>
  );
}
