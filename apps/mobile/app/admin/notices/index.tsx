import { StyleSheet, View } from 'react-native';

import { useRouter } from 'expo-router';

import { MegaphoneIcon } from 'phosphor-react-native/src/icons/Megaphone';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { AppText } from '@/components/ui/AppText';
import { Button } from '@/components/ui/Button';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Hold } from '@/components/ui/Hold';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { PressCard } from '@/components/ui/PressCard';
import { useAdminNotices } from '@/hooks/use-notice-admin';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/kst';

function Notices() {
  const router = useRouter();
  const { space } = useTheme();
  const query = useAdminNotices();
  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <PagedList
        query={query}
        label="공지 목록"
        keyOf={(notice) => notice.id}
        renderItem={(notice, index) => {
          const published = formatDateTime(notice.publishedAt);
          return (
            <PressCard
              label={`${notice.title}, ${published}${notice.pinned ? ', 고정' : ''}`}
              index={index}
              onPress={() => router.push(`/admin/notices/${notice.id}`)}
            >
              <View style={styles.fill}>
                <AppText variant="label">{notice.title}</AppText>
                <AppText variant="caption" tone="muted">
                  {published}
                </AppText>
              </View>
              {notice.pinned ? (
                <>
                  <Hold on />
                  <AppText variant="caption">고정</AppText>
                </>
              ) : null}
            </PressCard>
          );
        }}
        empty={
          <Notice
            illustration={<FloatingIcon icon={MegaphoneIcon} />}
            title="아직 공지가 없어요"
            description="새 공지를 올리면 홈에 보여요."
          />
        }
      />
      <Button label="새 공지" onPress={() => router.push('/admin/notices/new')} />
    </View>
  );
}

export default function AdminNoticesScreen() {
  return (
    <AdminScreen title="공지 관리">
      <Notices />
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
