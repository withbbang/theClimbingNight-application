import { MountainsIcon } from 'phosphor-react-native/src/icons/Mountains';

import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';

export default function HomeScreen() {
  return (
    <Screen scroll bottomInset={false}>
      <Notice
        illustration={<FloatingIcon icon={MountainsIcon} />}
        title="벽헤는밤"
        description="공지와 다가오는 벙을 이곳에서 보여 드릴게요."
      />
    </Screen>
  );
}
