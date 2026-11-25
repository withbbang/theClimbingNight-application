import { AdminScreen } from '@/components/admin/AdminScreen';
import { NoticeForm } from '@/components/notice/NoticeForm';

export default function NewNoticeScreen() {
  return (
    <AdminScreen title="새 공지" scroll>
      <NoticeForm />
    </AdminScreen>
  );
}
