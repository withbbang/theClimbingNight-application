import { useRef } from 'react';
import { View } from 'react-native';

import { useLocalSearchParams, useRouter } from 'expo-router';

import { MegaphoneIcon } from 'phosphor-react-native/src/icons/Megaphone';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { NoticeForm } from '@/components/notice/NoticeForm';
import { ConfirmAction } from '@/components/ui/ConfirmAction';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { FreshQuery } from '@/components/ui/FreshQuery';
import { Notice } from '@/components/ui/Notice';
import { useLeft } from '@/hooks/use-left';
import { useDeleteNotice, useNotice, useSaveNotice } from '@/hooks/use-notice-admin';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { parseId } from '@/lib/parse-id';
import type { NoticeResponse } from '@/types/api/notice';

function EditNotice({ notice }: { notice: NoticeResponse }) {
  const router = useRouter();
  const { space } = useTheme();
  const left = useLeft();
  const save = useSaveNotice(notice.id);
  const remove = useDeleteNotice(notice.id);

  const busy = useRef(false);

  /** 저장과 삭제는 다시 그려지기 전에 잇달아 눌러도 하나만 보낸다. */
  async function exclusive<T>(send: () => Promise<T | undefined>) {
    if (busy.current) {
      return undefined;
    }
    busy.current = true;
    try {
      return await send();
    } finally {
      busy.current = false;
    }
  }

  async function confirm() {
    const removed = (await exclusive(() => remove.submit())) !== undefined;
    if (removed && !left.current) {
      router.dismissTo('/admin/notices');
    }
    return removed;
  }

  return (
    <View style={{ gap: space(4) }}>
      <NoticeForm
        notice={notice}
        save={{ ...save, submit: (body) => exclusive(() => save.submit(body)) }}
        locked={remove.isPending || remove.isSuccess}
      />
      <ConfirmAction
        label="공지 삭제"
        confirmLabel="공지 삭제 확정"
        message="지운 공지는 되돌릴 수 없어요."
        loading={remove.isPending}
        locked={save.isPending || save.isSuccess || remove.isSuccess}
        disabled={save.isPending || save.isSuccess}
        error={remove.error ? errorMessage(remove.error) : null}
        onConfirm={confirm}
      />
    </View>
  );
}

export default function EditNoticeScreen() {
  const router = useRouter();
  const id = parseId(useLocalSearchParams().id);
  const query = useNotice(id);
  return (
    <AdminScreen title="공지 수정" scroll>
      {id === null || isNotFound(query.error) ? (
        <Notice
          illustration={<FloatingIcon icon={MegaphoneIcon} />}
          title="공지를 찾을 수 없어요"
          description="지워졌거나 주소가 잘못됐어요."
          actionLabel="홈으로"
          onAction={() => router.dismissTo('/')}
        />
      ) : (
        <FreshQuery key={id} query={query}>
          {(notice) => <EditNotice notice={notice} />}
        </FreshQuery>
      )}
    </AdminScreen>
  );
}
