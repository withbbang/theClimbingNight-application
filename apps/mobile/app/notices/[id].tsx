import { useLocalSearchParams, useRouter } from 'expo-router';

import { MegaphoneIcon } from 'phosphor-react-native/src/icons/Megaphone';

import { BackButton } from '@/components/ui/BackButton';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';
import { WebPage } from '@/components/web/WebPage';
import { parseId } from '@/lib/parse-id';

export default function NoticeScreen() {
  const router = useRouter();
  const id = parseId(useLocalSearchParams().id);

  if (id === null) {
    return (
      <Screen scroll>
        <BackButton />
        <Notice
          illustration={<FloatingIcon icon={MegaphoneIcon} />}
          title="공지를 찾을 수 없어요"
          description="지워졌거나 주소가 잘못됐어요."
          actionLabel="공지 목록"
          onAction={() => router.replace('/notices')}
        />
      </Screen>
    );
  }
  return <WebPage path={`/app/notices/${id}`} />;
}
