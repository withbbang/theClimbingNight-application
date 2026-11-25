import { useAdminMutation } from '@/hooks/use-admin-mutation';
import { meetupApi } from '@/lib/meetup-api';
import type { MeetupCopyRequest, MeetupWriteRequest } from '@/types/api/meetup';

const KEYS = [['meetups']];

export function useCreateMeetup() {
  return useAdminMutation(meetupApi.create, KEYS);
}

export function useUpdateMeetup(id: number) {
  return useAdminMutation((body: MeetupWriteRequest) => meetupApi.update(id, body), KEYS);
}

export function useCancelMeetup(id: number) {
  return useAdminMutation(() => meetupApi.cancelMeetup(id), KEYS);
}

export function useCopyMeetup(id: number) {
  return useAdminMutation((body: MeetupCopyRequest) => meetupApi.copy(id, body), KEYS);
}
