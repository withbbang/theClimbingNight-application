import { useRef, useState } from 'react';

import {
  useAddLateCancelPenalty,
  useCorrectAttendance,
  useRemoveApplication,
} from '@/hooks/use-applications';
import type { AttendanceResult } from '@/types/api/attendance';

export type ApplicationAction =
  | { type: 'remove' }
  | { type: 'penalize' }
  | { type: 'correct'; attendanceId: number; result: AttendanceResult };

/**
 * 신청자별로 하나씩, 서로 다른 신청자는 동시에 보낸다.
 * 줄이 화면 밖으로 나가도 보내는 중인 것을 잊지 않게 화면이 들고 있는다.
 */
export function useApplicationActions(meetupId: number) {
  const { mutateAsync: remove } = useRemoveApplication(meetupId);
  const { mutateAsync: penalize } = useAddLateCancelPenalty(meetupId);
  const { mutateAsync: correct } = useCorrectAttendance(meetupId);
  const busy = useRef(new Set<number>());
  const [errors, setErrors] = useState<Record<number, Error | undefined>>({});
  const [sending, setSending] = useState<Record<number, ApplicationAction | undefined>>({});

  function request(id: number, action: ApplicationAction): Promise<unknown> {
    switch (action.type) {
      case 'remove':
        return remove(id);
      case 'penalize':
        return penalize(id);
      case 'correct':
        return correct({ id: action.attendanceId, result: action.result });
    }
  }

  /** 성공하면 true. */
  async function send(id: number, action: ApplicationAction) {
    if (busy.current.has(id)) {
      return false;
    }
    busy.current.add(id);
    setErrors((old) => ({ ...old, [id]: undefined }));
    setSending((old) => ({ ...old, [id]: action }));
    try {
      await request(id, action);
      return true;
    } catch (error) {
      setErrors((old) => ({ ...old, [id]: error as Error }));
      return false;
    } finally {
      busy.current.delete(id);
      setSending((old) => ({ ...old, [id]: undefined }));
    }
  }

  const clearErrors = () => setErrors({});

  return { sending, errors, clearErrors, send };
}
