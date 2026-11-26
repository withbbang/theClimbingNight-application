import { StyleSheet, View } from 'react-native';

import { ShieldCheckIcon } from 'phosphor-react-native/src/icons/ShieldCheck';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { ReportTargetRow } from '@/components/admin/ReportTargetRow';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { FloatingIcon } from '@/components/ui/FloatingIcon';
import { Notice } from '@/components/ui/Notice';
import { PagedList } from '@/components/ui/PagedList';
import { reportTargetKey, useReportTargetAction, useReportTargets } from '@/hooks/use-moderation';
import { useTheme } from '@/hooks/use-theme';
import { errorMessage } from '@/lib/error-message';

const MAX_STAGGER = 6;

function Reports() {
  const { space } = useTheme();
  const query = useReportTargets();
  const act = useReportTargetAction();
  const locked = act.isPending || query.isRefetching;

  return (
    <View style={[styles.fill, { gap: space(4) }]}>
      {act.error ? <ErrorBanner message={errorMessage(act.error)} /> : null}
      <PagedList
        query={query}
        label="신고 목록"
        keyOf={reportTargetKey}
        renderItem={(target, index) => {
          const key = reportTargetKey(target);
          const mine = act.isPending && reportTargetKey(act.variables) === key;
          return (
            // 칸이 재사용돼도 펼친 확인 칸이 다른 대상으로 옮지 않게 한다.
            <ReportTargetRow
              key={key}
              target={target}
              index={Math.min(index, MAX_STAGGER)}
              sending={mine ? act.variables.action : null}
              locked={locked}
              onAct={async (action) => {
                act.reset();
                const done = await act.submit({
                  targetType: target.targetType,
                  targetId: target.targetId,
                  action,
                });
                return done === true;
              }}
            />
          );
        }}
        empty={
          <Notice
            illustration={<FloatingIcon icon={ShieldCheckIcon} />}
            title="처리할 신고가 없어요"
            description="회원이 게시물이나 사용자를 신고하면 여기에 보여요."
          />
        }
      />
    </View>
  );
}

export default function ReportsScreen() {
  return (
    <AdminScreen title="신고 목록">
      <Reports />
    </AdminScreen>
  );
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
