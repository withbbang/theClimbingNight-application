import { AdminScreen } from '@/components/admin/AdminScreen';
import { NoticeForm } from '@/components/notice/NoticeForm';
import { useSaveNotice } from '@/hooks/use-notice-admin';

export default function NewNoticeScreen() {
  const save = useSaveNotice(null);
  return (
    <AdminScreen title="새 공지" scroll>
      <NoticeForm save={save} />
    </AdminScreen>
  );
}
