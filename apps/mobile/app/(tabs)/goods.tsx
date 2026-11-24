import { ToteIcon } from 'phosphor-react-native/src/icons/Tote';

import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { Screen } from '@/components/ui/Screen';

export default function GoodsScreen() {
  return (
    <Screen scroll bottomInset={false}>
      <Notice
        illustration={<FloatingIcon icon={ToteIcon} />}
        title="굿즈를 준비하고 있어요"
        description="판매를 시작하면 이곳에서 볼 수 있어요."
      />
    </Screen>
  );
}
