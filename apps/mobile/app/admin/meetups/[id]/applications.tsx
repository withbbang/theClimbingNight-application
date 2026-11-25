import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useLocalSearchParams } from 'expo-router';

import { FlashList } from '@shopify/flash-list';
import { UsersThreeIcon } from 'phosphor-react-native/src/icons/UsersThree';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { ApplicationRow } from '@/components/meetup/ApplicationRow';
import { MeetupNotFound } from '@/components/meetup/MeetupNotFound';
import { AppText } from '@/components/ui/AppText';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PullToRefresh } from '@/components/ui/PullToRefresh';
import { QueryState } from '@/components/ui/QueryState';
import { useApplicationActions } from '@/hooks/use-application-actions';
import { useApplications } from '@/hooks/use-applications';
import { useMeetup } from '@/hooks/use-meetups';
import { useNow } from '@/hooks/use-now';
import { useTheme } from '@/hooks/use-theme';
import { isNotFound } from '@/lib/api-error';
import { errorMessage } from '@/lib/error-message';
import { formatDateTime } from '@/lib/kst';
import { meetupPhase, phaseBoundaries } from '@/lib/meetup-status';
import { parseId } from '@/lib/parse-id';
import type { MeetupApplicationAdminResponse, MeetupResponse } from '@/types/api/meetup';

type Item = string | MeetupApplicationAdminResponse;

const MAX_STAGGER = 8;
const GROUPS = [
  { label: '확정', statuses: ['CONFIRMED'] },
  { label: '대기', statuses: ['WAITLISTED'] },
  { label: '취소·내보냄', statuses: ['CANCELED', 'REMOVED'] },
];

/** 묶음 머리글과 줄. 묶음 안의 순서는 서버가 정한 그대로다. */
function toItems(applications: MeetupApplicationAdminResponse[]): Item[] {
  return GROUPS.flatMap(({ label, statuses }) => {
    const rows = applications.filter((a) => statuses.includes(a.status));
    return rows.length === 0 ? [] : [`${label} ${rows.length}`, ...rows];
  });
}

interface ApplicationsProps {
  meetup: MeetupResponse;
  applications: MeetupApplicationAdminResponse[];
  onRefresh: () => Promise<unknown>;
}

function Applications({ meetup, applications, onRefresh }: ApplicationsProps) {
  const { space } = useTheme();
  const [error, setError] = useState<Error | null>(null);
  const { sending, send } = useApplicationActions(meetup.id, setError);
  const now = useNow(phaseBoundaries(meetup));
  const phase = meetupPhase(meetup, new Date(now));

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      <AppText tone="muted">{`${meetup.title}, ${formatDateTime(meetup.startsAt)}`}</AppText>
      {error ? <ErrorBanner message={errorMessage(error)} /> : null}
      <View style={styles.fill}>
        <FlashList
          accessibilityLabel="신청자 목록"
          data={toItems(applications)}
          keyExtractor={(item) => (typeof item === 'string' ? item : String(item.id))}
          getItemType={(item) => (typeof item === 'string' ? 'group' : 'row')}
          renderItem={({ item, index }) =>
            typeof item === 'string' ? (
              <AppText
                variant="title"
                accessibilityRole="header"
                style={{ marginTop: space(2), marginBottom: space(3) }}
              >
                {item}
              </AppText>
            ) : (
              // 칸이 재사용돼도 열린 확인이 다른 사람에게 옮지 않게 한다.
              <ApplicationRow
                key={item.id}
                application={item}
                phase={phase}
                index={Math.min(index, MAX_STAGGER)}
                sending={sending[item.id]}
                onSend={(action) => send(item.id, action)}
              />
            )
          }
          ListEmptyComponent={
            <Notice
              illustration={<FloatingIcon icon={UsersThreeIcon} />}
              title="아직 신청자가 없어요"
              description="동호회원이 신청하면 여기에 보여요."
            />
          }
          showsVerticalScrollIndicator={false}
          refreshControl={<PullToRefresh onRefresh={onRefresh} />}
        />
      </View>
    </View>
  );
}

export default function ApplicationsScreen() {
  const id = parseId(useLocalSearchParams().id);
  const meetup = useMeetup(id);
  const applications = useApplications(id);
  return (
    <AdminScreen title="신청자 관리">
      {id === null || isNotFound(meetup.error) || isNotFound(applications.error) ? (
        <MeetupNotFound />
      ) : (
        <QueryState query={meetup}>
          {(detail) => (
            <QueryState query={applications}>
              {(rows) => (
                <Applications
                  key={id}
                  meetup={detail.meetup}
                  applications={rows}
                  onRefresh={() => Promise.all([meetup.refetch(), applications.refetch()])}
                />
              )}
            </QueryState>
          )}
        </QueryState>
      )}
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
