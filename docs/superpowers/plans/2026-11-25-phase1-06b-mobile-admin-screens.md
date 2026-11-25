# 1단계-06B 모바일 운영진 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 운영진이 앱에서 벙 개설·수정·취소·복제, 신청자 관리, 참석 보정, 블랙리스트, 인증 심사, 회원 관리, 공지, 문의 답변을 할 수 있게 한다.

**Architecture:** API는 이미 모두 있어 `apps/mobile`만 고친다. 6-A와 같이 `lib/<도메인>-api.ts` → `hooks/` → 화면 순이고, 운영진 화면은 루트 Stack의 `app/admin/` 아래에 둔다. 되돌리기 어려운 동작은 공용 `ConfirmAction`으로 한 번 더 확인한다. 판정은 모두 서버가 한다.

**Tech Stack:** 6-A와 같음. 새 의존성 없음 (날짜는 `MonthCalendar`, 시각은 숫자 입력).

**Spec:** `docs/architecture.md` 6장, 8장, 9장, 11장. 규약은 `.claude/rules/mobile.md`, `.claude/rules/testing.md`, `api-conventions` 스킬. 앞 계획: `2026-11-24-phase1-06a-mobile-member-screens.md`.

**이 계획에 없는 것:** 명부 CSV 등록(스크립트가 있음), 관리자 웹, 공지 마크다운 미리보기(7), ADMIN 지정·해제(DB에서 직접).

## 작업 방식

- 브랜치: `dev`에서 `feat/mobile-admin-screens`. 태스크마다 `.gitmessage.txt`를 먼저 읽고 커밋한다. 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 명령은 `apps/mobile`에서 실행한다. 태스크마다 **테스트 작성 → `pnpm test <이름>`으로 실패 확인 → 구현 → `pnpm lint && pnpm typecheck && pnpm test` 통과 → 커밋** 순서다.
- 화면을 만들 때 `frontend-design`, `ui-ux-pro-max`, `taste-skill` 스킬을 불러오고 **애니메이션을 최대한 많이** 넣는다(사용자 요청). 기존 토큰·홀드 표현을 이어 쓴다.
- 리뷰 강도는 `medium`을 제안한다(일괄 승인, 자격 해제, 블랙리스트가 있음). 실행 전 승인받는다.

## Global Constraints

- 계획 5와 6-A의 Global Constraints를 그대로 따른다.
- `app/admin/` 화면은 모두 `RoleGate role="ADMIN"` 안에 두고, 운영진 쿼리는 `useIsAdmin()`일 때만 `enabled`다.
- 변이는 `useSubmit`을 쓰고 `onSettled`에서(성공·실패 모두) 아래 키를 무효화한다.

| 변이 | 무효화 |
|---|---|
| 벙 개설·수정·취소·복제, 내보내기 | `['meetups']` |
| 늦은 취소 블랙리스트, 기록 취소 | `['meetups']`, `['penalties']` |
| 참석 보정 | `['meetups']`, `['penalties']`, `['attendance-rates']` |
| 승인·거절·일괄 승인 | `['membership-requests']`, `['members']`, `['roster-members']` |
| 자격 해제 | `['members']`, `['meetups']` |
| 공지 작성·수정·삭제 | `['notices']` |
| 문의 답변 | `['inquiries']` |

- 확인을 거치는 것: 벙 취소, 내보내기, 늦은 취소 블랙리스트, 노쇼로 바꾸기, 기록 취소, 일괄 승인, 자격 해제, 공지 삭제. 나머지는 바로 보낸다.
- 글자 수: 벙 제목·장소 100, 설명 5000, 정원 1~500, 늦은 취소 기준 0~168시간. 거절·기록 취소 사유 200. 공지 제목 100, 본문 10000. 답변 2000. 필수 글은 공백뿐이면 보내지 않고, 앞뒤 공백을 떼어 보낸다.
- 목록은 FlashList + 20개씩 무한 스크롤이다. 처리해서 빠지는 목록도 무효화하면 받은 페이지를 처음부터 다시 받으므로 따로 빼지 않는다.

## Review Focus

1. **운영진에서 내려간 사람이 운영진 화면에 남아 있다** → 403 뒤 내 정보를 다시 받아 운영진 안내로 바뀐다. Task 1.
2. **기기가 해외 시간대이거나 벙이 자정을 넘긴다** → 입력한 시각은 서울 시각이고, 종료가 시작보다 이르거나 같으면 다음 날이다. Task 1·2.
3. **다른 운영진이 고친 벙·공지를 낡은 캐시로 연다** → 새로 받은 값으로 폼을 채운다(PUT은 전체 교체). 입력 중 재조회는 입력을 지우지 않는다. Task 2·7.
4. **수백 건 일괄 승인이 15초 제한을 넘는다** → 실패로 단정하지 않고 "처리 중일 수 있어요"를 보여주고 목록을 다시 받는다. Task 5.
5. **다른 운영진이 먼저 처리한 신청·기록을 누른다** → 서버 문구가 남아 있고 목록은 최신이 된다. Task 3·5.

---

### Task 1: 운영진 공용 기반과 메뉴

**Files:**
- Create: `hooks/use-paged-query.ts`, `components/ui/{ConfirmAction,PagedList}.tsx`, `components/admin/AdminScreen.tsx`, `app/admin/index.tsx`
- Modify: `components/auth/RoleGate.tsx`, `hooks/use-role.ts`, `lib/kst.ts`, `app/(tabs)/my.tsx`, `hooks/use-inquiries.ts`, `app/inquiries/index.tsx` (뒤 둘은 공용 목록으로 옮김)
- Test: `components/ui/__tests__/ConfirmAction.test.tsx`, `components/auth/__tests__/RoleGate.test.tsx`, `lib/__tests__/kst.test.ts`, `__tests__/{admin-menu,my}.test.tsx`

**Interfaces:**
- Produces:
  - `useIsAdmin(): boolean`, `RoleGate`의 `role`에 `'ADMIN'` 추가. ADMIN이 아니면 "운영진만 볼 수 있어요"와 "홈으로"(`/`).
  - `parseTime(text: string): string | null` (`'1930'`·`'930'`·`'19:30'`·`'9:30'` → `'HH:MM'`, 아니면 null)
  - `toIso(day: DateKey, time: string): string` (서울 시각 → UTC ISO)
  - `usePagedQuery<T extends { id: number }>(key: QueryKey, fetchPage: (page: number, size: number) => Promise<PageResponse<T>>, enabled: boolean)`: 지금 `useMyInquiries`의 무한 조회·중복 제거를 옮긴다. ID가 `id`가 아닌 목록을 위해 네 번째 인자 `idOf?: (item: T) => number`를 받고, 주면 `T`에 `id`가 없어도 된다.
  - `<PagedList query label renderItem empty />`: 지금 `app/inquiries/index.tsx`의 끝 도달·다음 페이지 오류·당겨서 새로고침 처리를 옮긴다.
  - `<ConfirmAction label confirmLabel message onConfirm loading error />`: 첫 누름은 문구와 `confirmLabel`·"돌아가기"를 펼치고, 두 번째에 `onConfirm(): Promise<boolean>`을 부른다. `true`면 접고 아니면 `error` 문구와 함께 펼쳐 둔다.
  - `<AdminScreen title scroll?>`: `Screen` + `BackButton` + `RoleGate` + 제목

- [ ] **Step 1: 실패하는 테스트**

```ts
it.each([['1930','19:30'],['930','09:30'],['19:30','19:30'],['9:30','09:30'],['0000','00:00'],['2400',null],['1260',null],['19',null],['',null],['오후 7시',null]])(
  'parses %p to %p', (text, out) => expect(parseTime(text)).toBe(out));
it('reads the input as Seoul time', () => {
  expect(toIso('2026-11-25', '19:30')).toBe('2026-11-25T10:30:00.000Z');
  expect(toIso('2027-01-01', '00:00')).toBe('2026-12-31T15:00:00.000Z');
});
it('asks before running and runs once', ...)             // 첫 누름 호출 0, 확인 두 번 눌러도 1
it('stays open with the error when it fails', ...)
it('closes without running on 돌아가기', ...)
it('blocks USER and MEMBER from admin children', ...)
it('shows the operator menu to an ADMIN only', ...)      // 마이: '운영진 메뉴' → /admin
it('opens every admin menu', ...)                        // 벙 열기, 인증 심사, 회원 목록, 블랙리스트, 공지 관리, 문의 답변
it('turns to the blocked notice after a 403', ...)       // 403 AUTH_FORBIDDEN → GET /users/me MEMBER → 안내
```

- [ ] **Step 2: 실패 확인** — `pnpm test ConfirmAction RoleGate kst admin-menu my`.
- [ ] **Step 3: 구현** — 메뉴는 `MenuRow`를 쓴다. 메뉴의 대상: `/admin/meetups/new`, `/admin/requests`, `/admin/members`, `/admin/penalties`, `/admin/notices`, `/admin/inquiries`. 아직 없는 화면은 이 태스크에서 제목만 있는 자리 화면으로 만들고 뒤 태스크가 채운다. 기존 문의 테스트는 고치지 않고 통과해야 한다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 운영진 공용 기반과 메뉴 추가`

---

### Task 2: 벙 개설, 수정, 복제, 취소

**Files:**
- Create: `lib/meetup-form.ts`, `hooks/use-meetup-admin.ts`, `components/meetup/{MeetupForm,ScheduleFields,AdminBar}.tsx`, `app/admin/meetups/[id]/{edit,copy}.tsx`
- Modify: `types/api/meetup.ts`, `lib/meetup-api.ts`, `app/admin/meetups/new.tsx`, `app/meetups/[id]/index.tsx`, `app/(tabs)/meetups.tsx`
- Test: `lib/__tests__/meetup-form.test.ts`, `__tests__/admin-meetup.test.tsx`

**Interfaces:**
- Consumes: `parseTime`, `toIso`, `ConfirmAction`, `AdminScreen`, `MonthCalendar`, `useMeetup`, `meetupPhase`
- Produces:
  - `MeetupWriteRequest { title; description: string | null; placeName; startsAt; endsAt; capacity: number; lateCancelHours: number; participantsVisible: boolean }` (개설·수정 공용, 모든 필드를 보낸다)
  - `meetupApi.create(body)` POST `/meetups`, `.update(id, body)` PUT `/meetups/{id}`, `.cancelMeetup(id)` POST `/meetups/{id}/cancel`, `.copy(id, { startsAt, endsAt })` POST `/meetups/{id}/copy`
  - `interface Schedule { day: DateKey; start: string; end: string }`, `toSchedule(startsAt, endsAt): Schedule`, `scheduleToIso(s: Schedule): { startsAt: string; endsAt: string } | null` (시각이 틀리면 null)
  - `useCreateMeetup()`, `useUpdateMeetup(id)`, `useCancelMeetup(id)`, `useCopyMeetup(id)`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('ends the next day when the end is not after the start', () => {
  expect(scheduleToIso({ day: '2026-11-25', start: '2200', end: '0100' })).toEqual(
    { startsAt: '2026-11-25T13:00:00.000Z', endsAt: '2026-11-25T16:00:00.000Z' });
  expect(scheduleToIso({ day: '2026-11-25', start: '1900', end: '1900' })?.endsAt).toBe('2026-11-26T10:00:00.000Z');
  expect(scheduleToIso({ day: '2026-11-25', start: '2500', end: '1900' })).toBeNull();
});
it('round-trips a saved schedule', ...)                  // toSchedule → scheduleToIso가 같은 순간
it('opens a meetup with the defaults', ...)              // lateCancelHours 24, participantsVisible true, 빈 설명은 null
it('tells when the end is the next day', ...)            // '다음 날 01:00에 끝나요'
it('rejects blanks, capacity 0/501/1.5 and 169 hours without a request', ...)
it('shows field errors and the server message', ...)     // MEETUP_START_IN_PAST
it('replaces the form with the new detail', ...)         // /meetups/{새 id}, 뒤로 가면 폼이 아님
it('fills the edit form from a fresh fetch', ...)        // 낡은 캐시 정원 12, 서버 20 → 20
it('keeps typing while a refetch finishes', ...)
it('copies with only a new schedule', ...)               // 본문은 startsAt, endsAt만
it('cancels only after confirming', ...)
it('hides edit after the start, cancel after the end or when canceled', ...)
it('hides copy for an imported meetup (capacity null)', ...)
it('hides the admin bar from a MEMBER', ...)
it('does not request for a bad id', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test meetup-form admin-meetup`.
- [ ] **Step 3: 구현**
  - 개설 성공은 `router.replace('/meetups/{id}')`, 수정은 상세로 `dismissTo`, 복제는 새 상세로 `replace`.
  - 수정 폼은 `isFetchedAfterMount`가 된 값으로 한 번만 초기화한다. 조회가 실패하면 폼 대신 오류와 "다시 시도"를 보여준다.
  - 복제 폼의 처음 값은 원본과 같은 시각, 날짜는 원본 + 7일(이미 지났으면 오늘).
  - 상세의 `AdminBar`: 신청자 관리, 수정, 복제, 벙 취소. 벙 탭에 ADMIN용 "벙 열기" 버튼을 둔다. 신청자 관리(`/admin/meetups/{id}/applications`)는 Task 3이 만들 때까지 자리 화면이다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 벙 개설과 수정, 복제, 취소 추가`

---

### Task 3: 신청자 관리와 참석 보정

**Files:**
- Create: `hooks/use-applications.ts`, `components/meetup/{ApplicationRow,AttendanceSwitch}.tsx`
- Modify: `types/api/{meetup,attendance}.ts`, `lib/{meetup-api,attendance-api}.ts`, `app/admin/meetups/[id]/applications.tsx`
- Test: `__tests__/admin-applications.test.tsx`

**Interfaces:**
- Consumes: `ConfirmAction`, `AdminScreen`, `useMeetup`, `meetupPhase`
- Produces:
  - `AttendanceResult = 'ATTENDED' | 'EXCUSED' | 'NO_SHOW'`, `MeetupApplicationAdminResponse`(서버 DTO와 같은 필드), `AttendanceResponse`
  - `meetupApi.applications(id)` GET `/meetups/{id}/applications`, `.removeApplication(applicationId)` POST `/meetup-applications/{id}/remove`(204), `.addLateCancelPenalty(applicationId)` POST `/meetup-applications/{id}/penalties`
  - `attendanceApi.correct(attendanceId, result)` PUT `/attendances/{id}` `{ result }`
  - `useApplications(meetupId)` 키 `['meetups', 'applications', meetupId]`, `useRemoveApplication()`, `useAddLateCancelPenalty()`, `useCorrectAttendance()`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('groups applicants in the server order', ...)         // 확정, 대기, 취소·내보냄
it('shows total and recent penalty counts', ...)         // '블랙리스트 3회 (최근 3개월 1회)', 0이면 숨김
it('shows a late cancel with its reason', ...)
it('removes an applicant after confirming, only before the start', ...)
it('adds a late-cancel penalty after confirming', ...)
it('hides the penalty action when penalized or the meetup is canceled', ...)
it('corrects to EXCUSED at once', ...)                   // PUT { result: 'EXCUSED' } 1회
it('asks before marking NO_SHOW', ...)                   // '블랙리스트에 1회 기록돼요'
it('does not send the result already selected', ...)
it('shows no correction without an attendance record', ...) // 끝났으면 '참석 기록을 준비하고 있어요'
it('corrects two people independently', ...)             // 한 줄 전송 중에도 다른 줄을 누를 수 있다
it('keeps the server message and refetches when stale', ...) // 409 MEETUP_APPLICATION_NOT_ACTIVE
it('opens the penalties of the applicant', ...)          // /admin/penalties/{rosterMemberId}
```

- [ ] **Step 2: 실패 확인** — `pnpm test admin-applications`.
- [ ] **Step 3: 구현** — 변이는 줄마다 둔다(`ApplicationRow` 안). 목록 순서는 서버 순서를 그대로 쓴다. 이 화면의 목록은 최대 수백 명이므로 FlashList를 쓰고 키는 신청 ID, 확인 창이 열린 줄과 오류는 재사용으로 섞이지 않게 줄 컴포넌트에 `key={id}`를 준다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 신청자 관리와 참석 보정 추가`

---

### Task 4: 블랙리스트

**Files:**
- Create: `hooks/use-penalty-admin.ts`, `components/penalty/PenaltyRow.tsx`(`app/penalties.tsx`의 줄을 옮겨 같이 씀), `app/admin/penalties/[rosterId].tsx`
- Modify: `types/api/penalty.ts`, `lib/penalty-api.ts`, `app/admin/penalties/index.tsx`, `app/penalties.tsx`
- Test: `__tests__/admin-penalties.test.tsx`

**Interfaces:**
- Consumes: `usePagedQuery`, `PagedList`, `ConfirmAction`
- Produces: `PenaltySummaryResponse { rosterMemberId; somoimNickname; totalCount; recentCount }`, `RosterPenaltiesResponse { rosterMemberId; somoimNickname; totalCount; recentCount; penalties: PenaltyResponse[] }`, `penaltyApi.summaries(page, size)` GET `/penalties?page=&size=`, `.ofRoster(id)` GET `/roster-members/{id}/penalties`, `.revoke(id, reason)` POST `/penalties/{id}/revoke`, `usePenaltySummaries()` 키 `['penalties', 'admin', 'list']`, `useRosterPenalties(id)` 키 `['penalties', 'admin', id]`, `useRevokePenalty()`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('lists people with total and recent counts in the server order', ...)
it('opens the records of a person', ...)
it('shows active and revoked records with the reason', ...)
it('revokes with a trimmed reason after confirming', ...)   // { reason: '착오' }
it('does not revoke without a reason', ...)
it('offers no revoke on a revoked record', ...)
it('keeps the reason when it fails', ...)                   // 409 PENALTY_ALREADY_REVOKED 문구
it('shows not found for a bad or missing roster id', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test penalties`.
- [ ] **Step 3: 구현** — 사유 입력은 `ConfirmAction`의 `children`(펼쳤을 때 보임)으로 넣고, 사유가 비면 확인 버튼이 잠긴다(`disabled` 추가).
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 블랙리스트 관리 추가`

---

### Task 5: 동호회원 인증 심사

**Files:**
- Create: `types/api/roster.ts`, `lib/roster-api.ts`, `hooks/use-membership-admin.ts`, `components/membership/{RequestCard,RosterPicker}.tsx`, `hooks/use-debounced.ts`
- Modify: `types/api/membership.ts`, `lib/membership-api.ts`, `app/admin/requests.tsx`
- Test: `__tests__/admin-requests.test.tsx`

**Interfaces:**
- Consumes: `usePagedQuery`, `PagedList`, `ConfirmAction`
- Produces:
  - `RosterSource = 'APP' | 'IMPORT'`, `RosterCandidateResponse { id; somoimNickname; note: string | null; source }`, `RosterMemberResponse`(위 + `userId: number | null`), `MembershipReviewItemResponse`(서버 DTO와 같은 필드), `BulkApproveResponse { approvedCount; skippedCount }`
  - `membershipApi.pending(page, size)` GET `/membership-requests?status=PENDING&page=&size=`, `.approve(id, { rosterMemberId } | { createNew: true })`, `.reject(id, reason)`, `.bulkApprove()`
  - `rosterApi.search(q, page, size)` GET `/roster-members?q=&linked=false&page=&size=` (`q`는 `encodeURIComponent`)
  - `usePendingRequests()` 키 `['membership-requests', 'admin', 'PENDING']`, `useRosterSearch(q)` 키 `['roster-members', 'unlinked', q]`, `useApproveRequest()`, `useRejectRequest()`, `useBulkApprove()`
  - `useDebounced<T>(value: T, ms?: number): T` (기본 300ms)

- [ ] **Step 1: 실패하는 테스트**

```ts
it('shows the app nickname, the somoim nickname and candidates', ...)
it('approves with the single candidate', ...)            // { rosterMemberId: 3 }
it('requires choosing when there are several candidates', ...)
it('offers a new roster entry without candidates', ...)  // { createNew: true }
it('approves with another roster entry found by search', ...) // '별 이&' 인코딩, 300ms 뒤 1회
it('does not search with a blank query', ...)
it('rejects with a trimmed reason, not a blank one', ...)
it('bulk-approves after confirming and shows the result', ...) // '12건 승인, 1건 건너뜀'
it('says it may still be running when bulk approval times out', ...) // 문구 + 목록 재조회
it('disables bulk approval when no loaded request is approvable and all pages are loaded', ...)
it('shows the server message and drops a request handled elsewhere', ...) // 409 MEMBERSHIP_REQUEST_ALREADY_REVIEWED
it('shows an empty notice', ...)                         // '기다리는 신청이 없어요'
```

- [ ] **Step 2: 실패 확인** — `pnpm test admin-requests`.
- [ ] **Step 3: 구현**
  - 승인·거절 변이는 카드가 아니라 화면에 하나 둔다. 처리된 카드가 사라져도 오류 문구가 남게 하기 위해서다. 한 건을 보내는 동안 다른 카드와 일괄 승인 버튼을 잠근다.
  - 일괄 승인의 네트워크 오류(`networkError`) 문구: "처리 중일 수 있어요. 잠시 뒤 목록을 새로고침해 주세요."
  - 일괄 승인 버튼에는 건수를 쓰지 않는다(서버가 전체 기준으로 정하고 받은 페이지만으로는 모름).
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 동호회원 인증 심사 추가`

---

### Task 6: 회원 목록과 자격 해제

**Files:**
- Create: `types/api/member.ts`, `lib/member-api.ts`, `hooks/use-members.ts`
- Modify: `app/admin/members.tsx`
- Test: `__tests__/admin-members.test.tsx`

**Interfaces:**
- Consumes: `usePagedQuery`(`idOf: (m) => m.userId`), `PagedList`, `ConfirmAction`, `useDebounced`
- Produces: `MemberResponse { userId; nickname; role: Role; rosterMemberId: number | null; somoimNickname: string | null }`, `memberApi.list(q, page, size)` GET `/members?q=&page=&size=`, `.revoke(userId)` DELETE `/members/{userId}`, `useMembers(q)` 키 `['members', q]`, `useRevokeMember()`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('lists members and searches with the encoded query', ...)   // 빈 검색은 q 없이
it('keeps the answer of the last query when responses arrive out of order', ...)
it('revokes a MEMBER after confirming', ...)   // '다가오는 벙 신청도 취소돼요'
it('offers no revoke for an ADMIN', ...)
it('opens the penalties of a linked member only', ...)
it('shows "명부와 연결되지 않았어요" without a roster entry', ...)
it('refetches when the member was already revoked', ...)       // 404
```

- [ ] **Step 2: 실패 확인** — `pnpm test admin-members`.
- [ ] **Step 3: 구현** — 해제 변이는 Task 5와 같은 이유로 화면에 하나 둔다. 검색어가 바뀌는 동안 이전 목록을 `placeholderData`로 남기지 않는다(다른 사람을 누르지 않게).
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 회원 목록과 자격 해제 추가`

---

### Task 7: 공지 관리

**Files:**
- Create: `hooks/use-notice-admin.ts`, `components/notice/NoticeForm.tsx`, `app/admin/notices/{new,[id]}.tsx`
- Modify: `types/api/notice.ts`, `lib/notice-api.ts`, `app/admin/notices/index.tsx`
- Test: `__tests__/admin-notices.test.tsx`

**Interfaces:**
- Consumes: `usePagedQuery`, `PagedList`, `ConfirmAction`, `Toggle`
- Produces: `NoticeResponse { id; title; body; pinned; publishedAt; updatedAt }`, `NoticeRequest { title; body; pinned: boolean }`, `noticeApi.get/create/update/remove`, `useAdminNotices()` 키 `['notices', 'admin', 'list']`, `useNotice(id)` 키 `['notices', 'detail', id]`, `useSaveNotice(id: number | null)`, `useDeleteNotice(id)`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('publishes a notice and returns to the refreshed list', ...)  // POST { title, body, pinned: false }
it('warns that publishing sends a push', ...)                    // '올리면 회원에게 알림이 가요'
it('limits the title to 100 and the body to 10000, and rejects blanks', ...)
it('fills the edit form from a fresh fetch and keeps typing during refetch', ...)
it('updates without the push warning', ...)                      // PUT
it('deletes after confirming and returns to the list without a 404 flash', ...)
it('keeps the text when saving fails', ...)
it('shows not found for a bad or missing id', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test admin-notices`.
- [ ] **Step 3: 구현** — 본문은 마크다운 원문을 그대로 편집한다. 삭제 성공은 상세 쿼리를 `removeQueries`하고 목록으로 나간다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 공지 관리 추가`

---

### Task 8: 문의 답변

**Files:**
- Create: `hooks/use-inquiry-admin.ts`, `app/admin/inquiries/[id].tsx`
- Modify: `types/api/inquiry.ts`, `lib/inquiry-api.ts`, `app/admin/inquiries/index.tsx`
- Test: `__tests__/admin-inquiries.test.tsx`

**Interfaces:**
- Consumes: `usePagedQuery`, `PagedList`, `Segments`
- Produces: `InquiryAdminResponse`(`InquiryResponse` + `userId: number`, `nickname: string | null`), `inquiryApi.all(status, page, size)` GET `/inquiries?status=&page=&size=`, `.get(id)` GET `/inquiries/{id}`, `.answer(id, answer)` PUT `/inquiries/{id}/answer`, `useAdminInquiries(status: 'OPEN' | 'ANSWERED')` 키 `['inquiries', 'admin', status]`, `useAdminInquiry(id)` 키 `['inquiries', 'admin', 'detail', id]`, `useAnswerInquiry(id)`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('starts with unanswered inquiries and switches to answered', ...)
it('shows "탈퇴한 회원" without a nickname', ...)
it('answers with trimmed text and returns to the list', ...)
it('says no push is sent again when editing an answer', ...)     // '답변을 고쳐도 알림은 다시 가지 않아요'
it('limits to 2000 and rejects blanks', ...)
it('keeps the text when sending fails', ...)
it('keeps typing during a refetch', ...)
it('shows not found for a bad or missing id', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test admin-inquiries`.
- [ ] **Step 3: 구현** — 처음 값은 `isFetchedAfterMount`가 된 답변(없으면 빈 글)으로 한 번만 채운다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 문의 답변 추가`

---

### Task 9: 문서와 화면 확인

**Files:** Modify: `.claude/rules/mobile.md` (`AdminScreen`, `ConfirmAction`, `PagedList` 사용 규칙), `docs/architecture.md` (9장 화면 표에 문의·공지·복제 화면이 빠졌으면 추가)

- [ ] **Step 1: 자동 검사** — `pnpm lint && pnpm typecheck && pnpm test && pnpm expo install --check`. Expected: 모두 통과.
- [ ] **Step 2: 화면 확인** — 확인용 기기를 따로 띄우고 개발 서버는 8090. 6-A Task 10과 같은 조건(iOS·Android, 라이트·다크, 동작 줄이기, 가장 큰 글꼴). 사유·답변 입력 때 키보드가 버튼을 가리지 않는지 본다. 끝나면 정리하고, 못 본 화면은 그대로 보고한다.
- [ ] **Step 3: 커밋** `Docs : 운영진 화면 문서 반영`

---

## 이 계획이 끝나면 되는 것

- 1단계 네이티브 화면이 모두 동작한다.
- 계획 7이 이어받는 것: 공지 마크다운 표시.
- 정해 둔 것(제안): 종료 날짜는 입력받지 않고 시각으로 정함, 지난 벙의 참석 보정은 벙 달력에서 찾아 들어감, 노쇼와 되돌리기 어려운 동작만 확인, 일괄 승인 버튼에 건수 없음, 새 문의·새 인증 신청을 운영진에게 알리는 푸시는 없음.
