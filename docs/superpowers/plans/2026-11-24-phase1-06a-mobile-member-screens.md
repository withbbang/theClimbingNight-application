# 1단계-06A 모바일 회원 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 계획 5의 자리 화면을 실제 화면으로 바꿔, 회원이 벙을 찾아 신청·취소하고 인증·참석률·블랙리스트·문의·설정·탈퇴를 앱에서 할 수 있게 한다.

**Architecture:** 엔드포인트 묶음마다 `lib/<도메인>-api.ts`(호출)와 `hooks/use-<도메인>.ts`(React Query)를 두고 화면은 훅만 쓴다. 상세·설정 화면은 탭 위의 루트 Stack에 쌓는다. 시각은 서버 ISO 문자열로 다루고 표시·날짜 계산은 `lib/kst.ts`만 한다. 신청 가능 여부, 늦은 취소, 참석률의 최종 판정은 모두 서버가 한다.

**Tech Stack:** 계획 5와 같음 + `@shopify/flash-list` (SDK 58 호환 2.3.2, `pnpm expo install`). 달력은 직접 만든다.

**Spec:** `docs/architecture.md` 6장 인증 신청, 8장 벙·참석률·블랙리스트, 9장 화면 표, 11장 푸시, 12장 탈퇴. 규약은 `.claude/rules/mobile.md`, `.claude/rules/testing.md`, `api-conventions` 스킬. 앞 계획: `2026-11-24-phase1-05-mobile-foundation.md`.

**이 계획에 없는 것:** 운영진 화면(6-B), 공지·소개·굿즈·약관 웹뷰(7).

## 작업 방식

- 브랜치: `dev`에서 `feat/mobile-member-screens`. 태스크마다 `Feat(mobile) : 제목`으로 커밋하고 `.gitmessage.txt`를 먼저 읽는다. 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 명령은 `apps/mobile`에서 실행한다. 태스크마다 **테스트 작성 → `pnpm test <파일>`로 실패 확인 → 구현 → `pnpm lint && pnpm typecheck && pnpm test` 통과 → 커밋** 순서다.
- 화면 태스크(2~8)는 시작할 때 `frontend-design`, `ui-ux-pro-max`, `taste-skill` 스킬을 불러온다. **애니메이션을 최대한 많이** 넣는다(사용자 요청, 스킬의 절제 기본값보다 우선).
- 리뷰 강도는 `medium`을 제안한다(신청·취소와 탈퇴가 있음). 실행 전 승인받는다.

## Global Constraints

- 계획 5의 Global Constraints를 그대로 따른다.
- 날짜·시각 계산은 `lib/kst.ts`로만 한다. KST는 UTC+9 고정이라 기기 시간대와 `Intl`을 쓰지 않는다.
- 변이가 성공하거나 4xx로 실패하면 관련 쿼리 키를 무효화한다(낡은 화면에서 눌렀을 수 있다). 벙 변이는 `['meetups']` 전체다.
- 개인 쿼리(`meetups`, `attendance-rates`, `penalties`, `membership-requests`, `inquiries`, `notification-settings`)는 필요한 역할로 로그인했을 때만 `enabled`다.
- 서버 오류 문구는 `errorMessage(error)`로 그대로 보여준다.
- 첫 로딩은 `PulseHolds`, 첫 조회 실패는 `Notice` + "다시 시도". 이미 받은 데이터가 있으면 재조회 실패로 화면을 지우지 않는다.
- 쓰기 버튼은 요청 중 `loading`으로 잠근다.
- 테스트는 `test-utils/app.tsx`의 `mockServer`·`renderApp`을 쓰고 시각은 `jest.setSystemTime`으로 고정한다. 기준 시각 `2026-11-24T03:00:00Z`(서울 12:00).

## Review Focus

1. **동호회원 자격이 방금 해제된 사람이 벙 화면을 연다** → 403 뒤 내 정보를 다시 받아 인증 안내로 바뀐다. Task 1.
2. **해외이거나 자정 근처인 기기** → 벙 날짜와 "오늘"이 서울 기준이다. Task 1.
3. **낡은 화면에서 신청한다(이미 확정·취소된 벙)** → 서버 문구를 보여주고 상세를 새로 받는다. Task 3.
4. **인증 신청 기록이 없어 204를 받는다** → 오류가 아니라 신청 폼을 보여준다. Task 5.
5. **푸시·딥링크의 ID가 숫자가 아니거나 없는 벙이다** → 잘못된 요청 없이 안내하고 홈으로 나갈 수 있다. Task 1·3·9.

---

### Task 1: 공용 기반

**Files:**
- Create: `lib/kst.ts`, `lib/parse-id.ts`, `types/api/common.ts`, `components/ui/{BackButton,QueryState}.tsx`, `components/auth/RoleGate.tsx`, `test-utils/native-mocks.ts`
- Modify: `lib/query-client.ts`, `app/_layout.tsx`, `app/(tabs)/meetups.tsx`, `package.json`
- Test: `lib/__tests__/{kst,parse-id,query-client}.test.ts`, `components/auth/__tests__/RoleGate.test.tsx`

**Interfaces:**
- Produces:
  - `type DateKey = string` (`'YYYY-MM-DD'`)
  - `todayKst(now?: Date): DateKey`, `toDateKey(iso: string): DateKey`, `addDays(key: DateKey, days: number): DateKey`, `addMonths(key: DateKey, months: number): DateKey` (말일 초과는 그 달 말일)
  - `monthGrid(key: DateKey): DateKey[][]` (일요일 시작, 앞뒤 달로 채운 완전한 주)
  - `formatDay(key): string` → `11월 24일(화)`, `formatTime(iso): string` → `19:30`, `formatDateTime(iso): string` → `11월 24일(화) 19:30`
  - `parseId(value: unknown): number | null` (양의 안전한 정수, 십진 문자열 또는 number)
  - `interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }`
  - `<BackButton />` (뒤로 갈 곳이 없으면 `/`로 replace)
  - `<QueryState query={UseQueryResult<T>}>{(data: T) => ReactNode}</QueryState>` (데이터가 있으면 오류여도 children)
  - `<RoleGate role="USER" | "MEMBER">`: 비로그인은 로그인 안내, 부족한 역할은 인증 안내("인증하기" → `/membership`), 통과하면 children. 문구는 지금 `meetups.tsx`의 것을 옮긴다.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('uses the Seoul date, not the device date', () => {
  expect(todayKst(new Date('2026-11-24T14:59:59Z'))).toBe('2026-11-24');
  expect(todayKst(new Date('2026-11-24T15:00:00Z'))).toBe('2026-11-25');
});
it('formats in Seoul time', () => {
  expect(formatDateTime('2026-11-24T10:30:00Z')).toBe('11월 24일(화) 19:30');
  expect(toDateKey('2026-12-31T15:00:00Z')).toBe('2027-01-01');
});
it('clamps month ends', () => {
  expect(addMonths('2027-01-31', 1)).toBe('2027-02-28');
  expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
});
it('builds full weeks from Sunday', () => {
  const grid = monthGrid('2026-11-24');
  expect(grid[0][0]).toBe('2026-11-01');
  expect(grid.at(-1)?.at(-1)).toBe('2026-12-05');
  expect(grid.every((week) => week.length === 7)).toBe(true);
});
it.each([['7', 7], [7, 7], ['0', null], ['-1', null], ['1.5', null], ['abc', null], ['', null], [undefined, null], ['9007199254740992', null]])(
  'parses %p to %p', (value, expected) => expect(parseId(value)).toBe(expected));
it('refetches my info when a query is forbidden', ...) // 403 AUTH_FORBIDDEN 쿼리 뒤 ['users','me']가 invalidated
it('does not refetch my info for other errors', ...)   // 404·네트워크 오류
it('shows the login notice to a guest / verification to a USER / children to MEMBER and ADMIN', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test kst parse-id query-client RoleGate`. Expected: 모듈 없음으로 FAIL.
- [ ] **Step 3: 구현**
  - `pnpm expo install @shopify/flash-list`. Jest 설정 파일이 필요하면 설치된 패키지의 것을 쓴다.
  - `queryClient`의 `QueryCache`·`MutationCache` `onError`에서 403 `AUTH_FORBIDDEN`이면 `['users','me']`를 무효화한다. `useSyncMe`가 등급을 반영한다.
  - 루트 레이아웃에 `export const unstable_settings = { anchor: '(tabs)' }`를 둔다. 설치된 expo-router 타입에서 이름을 확인한다.
  - `test-utils/native-mocks.ts`에는 `__tests__/login.test.tsx`에 반복되는 secure-store·splash·haptics mock 팩토리를 둔다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 회원 화면 공용 기반 추가`

---

### Task 2: 벙 목록과 달력

**Files:**
- Create: `types/api/meetup.ts`, `lib/meetup-api.ts`, `lib/meetup-status.ts`, `hooks/use-meetups.ts`, `components/meetup/{MeetupCard,MonthCalendar,ViewSwitch,StatusBadge}.tsx`
- Modify: `app/(tabs)/meetups.tsx`, `__tests__/app-start.test.tsx`(자리 문구 단언 교체)
- Test: `lib/__tests__/meetup-status.test.ts`, `__tests__/meetups.test.tsx`

**Interfaces:**
- Consumes: Task 1 전부
- Produces:
  - 타입은 서버 DTO와 같은 이름·필드: `MeetupSummaryResponse`, `MeetupResponse`, `MeetupDetailResponse`, `MyApplicationResponse`, `ParticipantResponse`, `MeetupStatus = 'OPEN' | 'CANCELED'`, `MeetupApplicationStatus = 'CONFIRMED' | 'WAITLISTED' | 'CANCELED' | 'REMOVED'`. `endsAt`·`capacity`·`placeName`·`host`·`description`은 null 가능.
  - `meetupApi.list(from, to)`, `.get(id)`, `.apply(id)`, `.cancel(id, reason?: string)`
  - `useMeetups(from, to)` 키 `['meetups', 'list', from, to]`, `useMeetup(id)` 키 `['meetups', 'detail', id]`
  - `useUpcomingMeetups()` = `useMeetups(today, addDays(today, 62))` (서버 한도 62일)
  - `meetupPhase(m, now: Date): 'CANCELED' | 'UPCOMING' | 'ONGOING' | 'ENDED'` (시작 정각은 ONGOING, 종료 정각은 ENDED, `endsAt`이 null이면 시작 뒤 ENDED)
  - `<MeetupCard meetup index />`: 누르면 `/meetups/{id}`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('changes phase exactly at the start and the end', ...)      // 시작 1ms 전 UPCOMING, 정각 ONGOING, 종료 정각 ENDED
it('requests 62 days from the Seoul today', ...)               // '/meetups?from=2026-11-24&to=2027-01-25'
it('shows counts and my status', ...)                          // '확정 8/12', '대기 3', '내 상태: 확정'
it('shows "확정 8" when capacity is null', ...)
it('marks canceled meetups and hides ended ones in the list', ...)
it('groups the list by Seoul date', ...)                       // 15:00Z 벙은 다음 날 머리글
it('shows an empty notice', ...)                               // '예정된 벙이 없어요'
it('keeps the list when a refetch fails', ...)
it('asks the server for the visible month in the calendar', ...) // 다음 달 → from=2026-12-01&to=2026-12-31
it('shows the meetups of the pressed day, including past ones', ...)
it('labels days that have meetups', ...)                       // accessibilityLabel '11월 24일(화), 벙 2개'
it('does not call the API for a USER', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test meetup`.
- [ ] **Step 3: 구현**
  - 탭 위쪽에서 "목록 / 달력"을 바꾸고 선택 표시가 미끄러진다. 목록은 FlashList, 날짜 머리글, 당겨서 새로고침.
  - 달력은 달마다 1일~말일을 조회하고 오늘이 처음 선택된다. 달을 옮기면 1일이 선택되고 방향대로 미끄러진다. 날짜 칸은 44pt 이상. 홀드 모양 점으로 벙을 표시하고, 내가 확정된 날은 강조색이다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 벙 목록과 달력 추가`

---

### Task 3: 벙 상세, 신청, 취소

**Files:**
- Create: `app/meetups/[id]/index.tsx`, `app/meetups/[id]/cancel.tsx`, `hooks/use-meetup-application.ts`, `components/meetup/{CapacityHolds,ParticipantList,ApplicationBar}.tsx`
- Modify: `app/_layout.tsx` (취소 화면 `presentation: 'modal'`)
- Test: `__tests__/meetup-detail.test.tsx`

**Interfaces:**
- Consumes: `useMeetup`, `meetupApi`, `meetupPhase`, `parseId`, `RoleGate`
- Produces: `useApply(id)`, `useCancelApplication(id)`. 성공·4xx 모두 `['meetups']` 무효화.

- [ ] **Step 1: 실패하는 테스트**

```ts
it('applies in one press and shows confirmed', ...)            // POST /meetups/1/applications 1회, '참여가 확정됐어요'
it('shows the waitlist position', ...)                         // '대기 2번'
it('sends one request when pressed twice', ...)
it('shows the server message and refetches when the meetup is stale', ...) // 409 MEETUP_ALREADY_APPLIED → 문구 + GET 2회
it('shows the roster message to an admin without a roster entry', ...)     // 403 MEETUP_ROSTER_REQUIRED 문구 그대로
it('hides apply and cancel after the start', ...)
it('shows no apply button when removed', ...)                  // '운영진이 내보낸 벙이에요'
it('allows applying again after canceling', ...)
it('lets an applicant of a canceled meetup cancel, without apply', ...)
it('shows participants in order when visible', ...)
it('shows only counts when participants is null', ...)         // 이름 없음, '명단은 공개되지 않아요'
it('warns a confirmed applicant from the late-cancel deadline on', ...)   // 정각부터 경고, 1ms 전엔 없음
it('does not warn a waitlisted applicant', ...)
it('sends the trimmed reason, and an empty body without one', ...)        // { reason: '부상' } / {}
it('limits the reason to 200 characters', ...)
it('returns to the detail with a refreshed status after canceling', ...)
it('shows not found without calling the API for a bad id', ...)           // /meetups/abc
it('shows not found with a way home on 404', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test meetup-detail`.
- [ ] **Step 3: 구현**
  - 신청에는 확인 창이 없다(두 번 탭 목표: 홈 카드 → 신청). 취소는 취소 화면에서 한 번 확인한다.
  - 경고 조건은 `CONFIRMED`이고 `now >= startsAt - lateCancelHours`. 문구는 "취소 기준 시각이 지났어요. 늦은 취소로 표시될 수 있어요." (실제 판정은 서버. 기준 뒤 승격된 사람은 표시되지 않으므로 단정하지 않는다.)
  - 화면을 열어 둔 채 기준 시각·시작 시각이 지나면 다시 그리도록 다음 경계에 타이머를 하나 건다. 24일을 넘는 대기는 `setTimeout` 한도를 넘으므로 걸지 않는다.
  - 정원은 홀드가 차오르는 표현, 확정 순간은 홀드가 켜지고 `notifySuccess`. 정원이 20을 넘거나 null이면 숫자만.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 벙 상세와 신청·취소 추가`

---

### Task 4: 홈

**Files:**
- Create: `types/api/{notice,app-setting}.ts`, `lib/{notice-api,app-setting-api}.ts`, `hooks/use-home.ts`, `components/home/{NextMeetups,RecentNotices,JoinBanner}.tsx`
- Modify: `app/(tabs)/index.tsx`
- Test: `__tests__/home.test.tsx`

**Interfaces:**
- Consumes: `useUpcomingMeetups`, `MeetupCard`
- Produces: `NoticeSummaryResponse`, `AppSettingsResponse { somoimJoinUrl: string | null; smartstoreUrl: string | null }`, `useRecentNotices()` 키 `['notices', 'recent']`, `useAppSettings()` 키 `['app-settings']`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('shows up to three open upcoming meetups to a MEMBER', ...)  // 취소·시작한 벙 제외
it('opens the detail when a card is pressed', ...)
it('shows up to three notices to a guest', ...)                 // GET /notices?page=0&size=3
it('opens the somoim link for a guest and a USER', ...)         // Linking.openURL
it('hides the join button for a MEMBER or without a link', ...)
it('shows a message when the link cannot open', ...)            // '링크를 열지 못했어요.'
it('still shows notices when the meetups request fails', ...)
it('does not request meetups for a guest', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test home`.
- [ ] **Step 3: 구현** — 구역마다 따로 로딩·오류를 보여준다. 공지 줄은 누를 수 없다(계획 7에서 웹뷰 연결). 당겨서 새로고침은 세 쿼리를 함께 부른다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 홈 화면 추가`

---

### Task 5: 동호회원 인증

**Files:**
- Create: `types/api/membership.ts`, `lib/membership-api.ts`, `hooks/use-membership.ts`, `app/membership.tsx`
- Modify: `hooks/use-push.ts` (인증 푸시 때 `['membership-requests']`도 무효화)
- Test: `__tests__/membership.test.tsx`, `hooks/__tests__/use-push.test.tsx`

**Interfaces:**
- Produces: `MembershipRequestResponse { id; somoimNickname; status: 'PENDING' | 'APPROVED' | 'REJECTED'; rejectReason: string | null; createdAt; reviewedAt: string | null }`, `useMyMembershipRequest()` 키 `['membership-requests', 'me']` (데이터 `MembershipRequestResponse | null`), `useApplyMembership()`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('shows the form when there is no request (204)', ...)
it('submits the trimmed somoim nickname', ...)                  // { somoimNickname: '별이' }
it('rejects blank and limits to 50 characters', ...)            // 공백이면 요청 없음
it('shows pending without a form', ...)                         // '운영진이 확인하고 있어요'
it('shows the reject reason and the form again', ...)
it('shows the form when a past approval was revoked', ...)      // 역할 USER + APPROVED
it('tells a MEMBER that verification is done', ...)
it('refetches when the server says it is already pending', ...) // 409
it('asks a guest to log in', ...)
it('invalidates the request on a membership push', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test membership use-push`.
- [ ] **Step 3: 구현** — `queryFn`은 204의 `undefined`를 `null`로 바꾼다(React Query는 `undefined`를 허용하지 않음). 409 `MEMBERSHIP_ALREADY_MEMBER`면 `['users','me']`도 무효화한다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 동호회원 인증 화면 추가`

---

### Task 6: 내 참석률과 블랙리스트 기록

**Files:**
- Create: `types/api/{attendance,penalty}.ts`, `lib/{attendance-api,penalty-api}.ts`, `hooks/{use-attendance-rate,use-my-penalties}.ts`, `lib/period.ts`, `app/attendance.tsx`, `app/penalties.tsx`, `components/attendance/{PeriodSwitch,RateWall}.tsx`
- Test: `lib/__tests__/period.test.ts`, `__tests__/attendance.test.tsx`, `__tests__/penalties.test.tsx`

**Interfaces:**
- Produces: `AttendancePeriod = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'`, `AttendanceRateResponse { period; from; to; attendedCount; meetupCount; rate: number | null }`, `PenaltyResponse { id; meetupId; meetupTitle; meetupStartsAt; type: 'LATE_CANCEL' | 'NO_SHOW'; active: boolean; revokeReason: string | null }`, `shiftPeriod(period, date: DateKey, step: 1 | -1): DateKey`, `useAttendanceRate(period, date)` 키 `['attendance-rates', period, date]`, `useMyPenalties()` 키 `['penalties', 'me']`

- [ ] **Step 1: 실패하는 테스트**

```ts
it.each([['DAY','2026-12-31',1,'2027-01-01'], ['WEEK','2026-12-28',1,'2027-01-04'], ['MONTH','2026-01-31',1,'2026-02-28'],
  ['QUARTER','2026-11-30',1,'2027-02-28'], ['YEAR','2028-02-29',1,'2029-02-28'], ['YEAR','2026-11-24',-1,'2025-11-24']] as const)(
  'shifts %s from %s by %s to %s', (p, d, s, out) => expect(shiftPeriod(p, d, s)).toBe(out));
it('starts with this month', ...)                       // ?period=MONTH&date=2026-11-24
it('shows the rate, the counts and the server range', ...)  // '66.7%', '3번 중 2번 참석', 11월 1일 ~ 11월 30일
it('shows no percentage when the rate is null', ...)    // '아직 기록된 벙이 없어요'
it('shows 0% when nothing was attended', ...)
it('moves to the previous period but not past the current one', ...) // 응답 to >= 오늘이면 다음 버튼 비활성
it('returns to today when the period changes', ...)
it('keeps the answer of the last selected period when responses arrive out of order', ...)
it('counts active records and shows revoked ones with the reason', ...)
it('shows an empty notice without penalties', ...)      // '기록이 없어요'
```

- [ ] **Step 2: 실패 확인** — `pnpm test period attendance penalties`.
- [ ] **Step 3: 구현**
  - 비율은 벽을 오르는 홀드로 표현하고 숫자가 올라간다. 동작 줄이기면 최종 값. 기간 경계는 서버의 `from`·`to`만 쓴다.
  - 블랙리스트 문구는 사실만 적는다: "늦은 취소", "노쇼", 취소된 기록은 "취소됨 · 사유". 활성 횟수는 받은 목록에서 `active`만 센다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 참석률과 블랙리스트 기록 추가`

---

### Task 7: 문의

**Files:**
- Create: `types/api/inquiry.ts`, `lib/inquiry-api.ts`, `hooks/use-inquiries.ts`, `app/inquiries/{index,new,[id]}.tsx`
- Test: `__tests__/inquiries.test.tsx`

**Interfaces:**
- Produces: `InquiryResponse { id; title; body; status: 'OPEN' | 'ANSWERED'; answer: string | null; answeredAt: string | null; createdAt }`, `useMyInquiries()` (`useInfiniteQuery`, 키 `['inquiries', 'me', 'list']`), `useMyInquiry(id)` 키 `['inquiries', 'me', id]`, `useCreateInquiry()`

- [ ] **Step 1: 실패하는 테스트**

```ts
it('loads the next page until the last one', ...)        // page=0&size=20, page=1&size=20, 그 뒤 없음
it('shows an item once when pages overlap', ...)         // 조회 사이 새 문의로 밀린 항목
it('creates an inquiry and returns to the refreshed list', ...)
it('limits the title to 100 and the body to 2000, and rejects blanks', ...)
it('keeps the text when sending fails', ...)
it('shows the answer, or that it is waiting', ...)       // '답변을 기다리고 있어요'
it('shows not found for a bad or missing id', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test inquiries`.
- [ ] **Step 3: 구현** — `getNextPageParam`은 `page + 1 < totalPages`일 때만 다음 페이지. 목록은 FlashList, ID로 중복 제거. 상세의 본문과 답변은 일반 글자(마크다운 아님).
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 문의 화면 추가`

---

### Task 8: 마이, 알림 설정, 닉네임 변경, 탈퇴

**Files:**
- Create: `types/api/notification-setting.ts`, `lib/{user-api,notification-setting-api}.ts`, `hooks/{use-notification-settings,use-profile}.ts`, `components/my/MenuRow.tsx`, `components/ui/Toggle.tsx`, `app/settings/{notifications,nickname,withdraw}.tsx`
- Modify: `app/(tabs)/my.tsx`
- Test: `__tests__/my.test.tsx`, `__tests__/settings.test.tsx`

**Interfaces:**
- Consumes: `nicknameError`, `sessionStore.updateUser`, `signOut`
- Produces: `userApi.changeNickname(nickname): Promise<UserResponse>` (PATCH `/users/me`), `userApi.withdraw(): Promise<void>` (DELETE `/users/me`), `useNotificationSettings()` 키 `['notification-settings', 'me']`, `<MenuRow label onPress index />` (6-B 운영진 메뉴가 재사용)

- [ ] **Step 1: 실패하는 테스트**

```ts
it('shows member menus to a MEMBER and ADMIN only', ...)     // 내 참석률, 내 블랙리스트 기록
it('shows verification to a USER only', ...)
it('toggles one setting and sends both values', ...)         // PUT { newMeetup: false, newNotice: true }
it('restores the switch and shows the error when saving fails', ...)
it('ignores presses while saving', ...)
it('offers the system settings when notifications are denied', ...) // Linking.openSettings
it('changes the nickname everywhere', ...)                   // PATCH 뒤 마이 탭에 새 이름, 저장소에도 저장
it('shows a taken nickname under the field', ...)            // 409 USER_NICKNAME_TAKEN
it('does not send an unchanged or invalid nickname', ...)
it('withdraws, then signs out on this device', ...)          // DELETE /users/me → 비로그인, 캐시 비움, 홈
it('stays signed in when withdrawing fails', ...)
it('needs the confirmation switch before withdrawing', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test my settings`.
- [ ] **Step 3: 구현**
  - 닉네임 변경 성공 시 `sessionStore.updateUser`와 `queryClient.setQueryData(['users','me'])`를 함께 한다. 진행 중이던 `['users','me']` 조회는 먼저 `cancelQueries`한다(옛 닉네임으로 되돌아가지 않게).
  - 탈퇴 화면에 지워지는 것(닉네임·소셜 연결), 취소되는 것(다가오는 벙 신청), 남는 것(이름 없는 참석 기록)을 적는다.
  - 알림 권한은 `expo-notifications`의 `getPermissionsAsync`로 화면 초점 때마다 확인한다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 마이 메뉴와 설정, 탈퇴 추가`

---

### Task 9: 푸시로 상세 열기

**Files:**
- Modify: `lib/push-route.ts`, `hooks/use-push.ts`
- Test: `lib/__tests__/push-route.test.ts`, `hooks/__tests__/use-push.test.tsx`

**Interfaces:**
- Consumes: `parseId`
- Produces: `routeForPush(data: unknown): Href | null` (시그니처 유지), `queryKeysForPush(data: unknown): QueryKey[]`

| type | data | 이동 | 무효화 |
|---|---|---|---|
| `MEETUP_*` | `meetupId` | `/meetups/{id}`, ID가 잘못되면 `/meetups` | `['meetups']` |
| `MEMBERSHIP_APPROVED` | | `/meetups` | `['users','me']`, `['membership-requests']` |
| `MEMBERSHIP_REJECTED` | | `/membership` | 위와 같음 |
| `INQUIRY_ANSWERED` | `inquiryId` | `/inquiries/{id}`, ID가 잘못되면 `/inquiries` | `['inquiries']` |
| `NOTICE_PUBLISHED` | | `/` | `['notices']` |

- [ ] **Step 1: 실패하는 테스트**

```ts
it('routes a meetup push to its detail', () =>
  expect(routeForPush({ type: 'MEETUP_PROMOTED', meetupId: 7 })).toBe('/meetups/7'));
it('falls back to the list for a bad id', () =>
  expect(routeForPush({ type: 'INQUIRY_ANSWERED', inquiryId: 'x' })).toBe('/inquiries'));
it('opens the pushed meetup over another meetup detail', ...)  // /meetups/3에서 7로, 내용도 7
it('goes back to the tabs from a pushed detail on a cold start', ...)
it('closes the login screen before opening the detail', ...)
it('refetches the open detail when a push arrives in the foreground', ...)
```

- [ ] **Step 2: 실패 확인** — `pnpm test push`.
- [ ] **Step 3: 구현** — 쌓인 화면이 있으면 `dismissAll` 뒤 `navigate`한다. 탭으로 가는 경우도 같은 경로를 쓴다. 기존 탭·콜드 스타트 테스트가 그대로 통과해야 한다. `isMembershipPush`는 `queryKeysForPush`로 대체하고 지운다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 푸시로 벙과 문의 상세 열기`

---

### Task 10: 문서와 화면 확인

**Files:**
- Modify: `docs/architecture.md` 14장(FlashList, 달력 직접 구현), `.claude/rules/mobile.md`(FlashList, `lib/kst.ts`, `RoleGate`)

- [ ] **Step 1: 자동 검사** — `pnpm lint && pnpm typecheck && pnpm test && pnpm expo install --check`. Expected: 모두 통과.
- [ ] **Step 2: 화면 확인** — 확인용 기기를 따로 띄우고 개발 서버는 8090(`CI=1`, `CODE_SIGNING_ALLOWED=NO` 쓰지 않음). iOS·Android에서 라이트·다크, 동작 줄이기, 가장 큰 글꼴로 모든 화면을 본다. 취소 사유 입력 때 키보드가 버튼을 가리지 않는지, Android 뒤로 가기가 맞는지 본다. 끝나면 기기를 끄고 새 시뮬레이터는 지운다. 로컬 API나 로그인 키가 없어 못 본 화면은 그대로 보고한다.
- [ ] **Step 3: 커밋** `Docs : 모바일 회원 화면 문서 반영`

---

## 이 계획이 끝나면 되는 것

- 회원용 1단계 네이티브 화면이 모두 동작한다.
- 6-B가 이어받는 것: `RoleGate`에 ADMIN 추가, `MenuRow`로 운영진 메뉴, 벙 상세의 운영진 동작, `lib/kst.ts`를 쓰는 날짜·시각 입력.
- 계획 7이 이어받는 것: 홈 공지 줄과 공지 푸시의 웹뷰 이동.
- 계획 8이 이어받는 것: Apple 심사는 Sign in with Apple 사용자가 탈퇴할 때 Apple 토큰 폐기를 요구한다. API가 하는지 확인하고 없으면 추가한다.
- 정해 둔 것(제안): 달력은 일요일 시작, 벙 목록은 오늘부터 62일(그 뒤는 달력), 신청은 확인 없이 바로, 상세 화면에서 늦은 취소는 단정하지 않고 "표시될 수 있다"고 안내.
