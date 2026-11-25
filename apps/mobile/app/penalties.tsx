import { View } from 'react-native';

import { HandPalmIcon } from 'phosphor-react-native/src/icons/HandPalm';

import { RoleGate } from '@/components/auth/RoleGate';
import { PenaltyRow } from '@/components/penalty/PenaltyRow';
import { AppText } from '@/components/ui/AppText';
import { BackButton } from '@/components/ui/BackButton';
import { FadeInItem } from '@/components/ui/FadeInItem';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { QueryState } from '@/components/ui/QueryState';
import { Screen } from '@/components/ui/Screen';
import { useMyPenalties } from '@/hooks/use-my-penalties';
import { useTheme } from '@/hooks/use-theme';

const MAX_STAGGER = 10;

function Penalties() {
  const { space } = useTheme();
  const query = useMyPenalties();
  return (
    <QueryState query={query}>
      {(penalties) =>
        penalties.length === 0 ? (
          <Notice
            illustration={<FloatingIcon icon={HandPalmIcon} />}
            title="기록이 없어요"
            description="늦은 취소나 노쇼로 남은 기록이 없어요."
          />
        ) : (
          <View style={{ gap: space(6) }}>
            <FadeInItem index={0}>
              <AppText variant="display" accessibilityRole="header">
                블랙리스트 기록
              </AppText>
              <AppText variant="title">{`${penalties.filter((p) => p.active).length}건`}</AppText>
            </FadeInItem>
            {penalties.map((penalty, i) => (
              <FadeInItem key={penalty.id} index={Math.min(i + 1, MAX_STAGGER)}>
                <PenaltyRow penalty={penalty} />
              </FadeInItem>
            ))}
          </View>
        )
      }
    </QueryState>
  );
}

export default function PenaltiesScreen() {
  return (
    <Screen scroll>
      <BackButton />
      <RoleGate role="MEMBER">
        <Penalties />
      </RoleGate>
    </Screen>
  );
}
