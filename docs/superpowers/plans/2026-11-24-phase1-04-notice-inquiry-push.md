# 1단계-04 참석률·공지·문의·앱 설정·푸시 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1단계 API의 남은 기능(내 참석률, 공지, 앱 설정, 1:1 문의, 푸시 알림)을 완성한다.

**Architecture:** 새 패키지 `notice`, `setting`, `inquiry`, `push`를 만들고 참석률은 `attendance`에 둔다. 푸시는 도메인 이벤트를 `@TransactionalEventListener(AFTER_COMMIT)`로 받아 단일 스레드 실행기에서 보낸다. DB 트랜잭션 안에서는 Expo를 호출하지 않는다. 푸시는 보조 수단이라 서버가 재시작되면 큐에 있던 알림은 유실된다 (아웃박스는 만들지 않는다).

**Tech Stack:** 계획 1~3과 같음 (Spring Boot 4.1.2, Java 17, JPA, Flyway, H2 MySQL 모드/MySQL 8.4). 새 의존성 없음. Expo Push API는 `RestClient`로 호출한다.

**Spec:** `docs/architecture.md` 8장 "참석률", 9장 화면 목록, 10장 `notices`·`inquiries`·`push_tokens`·`app_settings`, 11장 "푸시 알림"·"문의". 규약은 `.claude/rules/api.md`, `.claude/rules/testing.md`, `api-conventions` 스킬. Expo 규격은 https://docs.expo.dev/push-notifications/sending-notifications/ (2026-11-24 확인).

## 작업 방식

- 브랜치: `dev`에서 `feat/api-notice-push`를 딴다. 태스크마다 커밋한다.
- 커밋 메시지: `.gitmessage.txt` 정책. 제목 50자 이내, 본문 `-` 목록, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 모든 태스크가 끝나면 CLAUDE.md 절차(`/code-review` → 기록 → revise-claude-md → 요청 시 병합)를 따른다. 커밋 뒤 비동기 발송과 잠금 변경이 있어 강도는 `medium`을 제안한다 (실행 전 승인).
- 테스트: `cd apps/api && ./gradlew test` (특정 클래스는 `--tests '<FQCN>'`). 훅이 Bash의 `./gradlew`를 막으면 `ctx_execute`(shell)로 실행한다.
- 새 에러 코드는 같은 커밋에서 `.claude/skills/api-conventions/SKILL.md` 표에도 넣는다.
- 경로 약어: `main/` = `apps/api/src/main/java/com/theclimbingnight/`, `test/` = `apps/api/src/test/java/com/theclimbingnight/`, `db/` = `apps/api/src/main/resources/db/migration/`.

## Global Constraints

- 계획 1~3의 Global Constraints를 그대로 따른다. 시각은 UTC `Instant` 저장, 날짜 경계는 `TimeZones.KST`, 시계는 `Clock` 주입.
- 잠금 순서: **사용자 → 벙 → 신청·참석·블랙리스트 → 동호회원 신청 → 명부 → 푸시 토큰**.
- 역할: 공지 조회·앱 설정 조회는 `@PublicApi`, 문의·알림 설정·푸시 토큰은 `@RequireRole(Role.USER)`, 참석률은 `Role.MEMBER`, 그 밖은 `Role.ADMIN`.
- 다른 사람의 문의는 403이 아니라 404 `COMMON_NOT_FOUND` (존재 여부를 숨긴다).
- 목록 페이징은 기존 컨트롤러와 같다: `page` 기본 0 `@Min(0) @Max(10000)`, `size` 기본 20 `@Min(1) @Max(100)`.
- 새 테이블은 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`, `created_at`·`updated_at DATETIME(6) NOT NULL`, 엔티티는 `BaseTimeEntity` 상속. V1~V3은 고치지 않는다.
- 푸시 실패는 본래 요청을 실패시키지 않는다. 푸시 토큰 원문과 Expo access token은 로그에 남기지 않는다.
- 테스트에서 실제 Expo를 호출하지 않는다 (`app.push.enabled` 기본값 `false`).

## Review Focus

1. **진행 중인 달의 참석률** → 아직 열리지 않은 벙이 분모에 들어가 비율이 낮아지면 안 된다. 분모는 참석 기록이 끝난 벙만. Task 2 `아직_기록되지_않은_벙은_분모에서_빠진다`.
2. **벙 취소가 롤백됨** → 푸시가 나가면 안 된다. Task 7 `롤백되면_푸시를_보내지_않는다`.
3. **한 기기에서 A가 로그아웃하고 B가 로그인** → A의 알림이 그 기기로 오면 안 된다. Task 5 `같은_토큰을_다른_사용자가_등록하면_소유자가_바뀐다`, `로그아웃하면_내_토큰만_지운다`.
4. **리마인드 후 벙 날짜가 바뀌거나, 전날 20시에 서버가 꺼져 있었음** → 새 날짜로 다시 보내고, 놓친 건은 당일 09시 이후 시작 전까지 한 번 보낸다. Task 7 `시작_시각이_바뀌면_리마인드를_다시_보낸다`, `놓친_리마인드는_당일_아홉시부터_보낸다`.
5. **묶음 중 일부 토큰이 무효이거나 Expo가 일시 장애** → 무효 토큰만 지우고 나머지 묶음은 계속 보낸다. Task 6 `무효_토큰은_지우고_나머지는_보낸다`, `한_묶음이_실패해도_다음_묶음을_보낸다`.

---

### Task 1: 계획 3 이월 지적과 문서 정리

**Files:**
- Modify: `main/meetup/service/{MeetupApplicationService,WaitlistPromoter}.java`, `main/attendance/service/{AttendanceService,AttendanceAutoRecordJob}.java`, `main/penalty/service/PenaltyService.java`
- Create: `main/meetup/service/SeatCounter.java`
- Modify: `CLAUDE.md`, `docs/superpowers/plans/2026-11-24-phase1-03-meetup.md`
- Test: `test/attendance/controller/AttendanceControllerTest.java`(새로), 기존 `AttendanceServiceTest`·`PenaltyServiceTest`·`AttendanceAutoRecordJobTest`·`WaitlistPromoterTest`·`MeetupApplicationServiceTest`

**Interfaces:**
- Produces:
  - `SeatCounter` (`@Component`): `long freeSeats(Meetup meetup)` — 정원이 null이면 `Long.MAX_VALUE`, 아니면 `max(0, capacity - 확정자 수)`. `WaitlistPromoter`와 `MeetupApplicationService`가 생성자 주입으로 쓴다. 단 `WaitlistPromoter`는 정원 null이면 지금처럼 승격하지 않는다.
  - `AttendanceService.correct`, `PenaltyService.addLateCancel`: 벙을 잠그기 전에 `userRepository.findByIdForUpdate(adminId)`로 운영진 행을 먼저 잠근다 (없으면 `AUTH_REQUIRED`). `getReferenceById` 대신 이 엔티티를 쓴다. 대상이 없을 때의 404는 사용자 잠금 뒤에 그대로 나온다.
  - `AttendanceAutoRecordJob`: 실패한 벙 ID를 `Set<Long>`에 기억해 첫 실패만 스택과 함께 `log.error`, 같은 벙의 반복 실패는 `log.debug`. 성공하면 집합에서 뺀다. 매 실행 때 대상 목록에 없는 ID도 뺀다.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // AttendanceServiceTest, PenaltyServiceTest에 추가 (InOrder)
  @Test void 운영진_행을_벙보다_먼저_잠근다()   // userRepository.findByIdForUpdate → meetupRepository.findByIdForUpdate
  // AttendanceAutoRecordJobTest에 추가 (로거에 ListAppender)
  @Test void 같은_벙이_계속_실패하면_error는_한_번만_남긴다()     // run() 두 번 → ERROR 1건
  @Test void 성공했다가_다시_실패하면_error를_다시_남긴다()       // 실패, 성공, 실패 → ERROR 2건
  // AttendanceControllerTest (@WebMvcTest, PenaltyControllerTest와 같은 @Import)
  @Test void 동호회원이_참석을_보정하면_403이다()               // MEMBER 토큰 PUT → AUTH_FORBIDDEN
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.attendance.*' --tests 'com.theclimbingnight.penalty.*'` → 새 잠금·로그 테스트 3개 실패 (403 테스트는 기존 동작이라 통과)
- [ ] **Step 3: 구현.** `MeetupApplicationService` 클래스 주석의 잠금 순서를 `사용자 → 벙 → 신청`으로 고친다. 기존 테스트의 생성자 호출과 스텁을 맞춘다.
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Fix(api) : 운영진 행 잠금 순서와 반복 로그 보완`
- [ ] **Step 6: 문서 정리.** `CLAUDE.md`의 "아직 앱 디렉터리는 없다. 만들기 전에 …" 줄을 `앱 디렉터리는 apps/api만 있다.`로, `docs/architecture.md` (작성 예정)의 괄호를 삭제. 계획 3 문서 "정해 둔 것"의 자격 해제 문장을 `동호회원 자격이 해제되면 다가오는 벙 신청을 취소하고 대기자를 승격한다 (2026-11-24 변경)`으로 고친다.
- [ ] **Step 7: 커밋** `Docs : 앱 디렉터리와 자격 해제 설명 정정`

---

### Task 2: 내 참석률

**Files:**
- Create: `main/attendance/service/{AttendancePeriod,DateRange,AttendanceRateService}.java`, `main/attendance/dto/AttendanceRateResponse.java`, `main/attendance/controller/AttendanceRateController.java`
- Modify: `main/attendance/repository/AttendanceRepository.java`, `main/meetup/repository/MeetupRepository.java`
- Test: `test/attendance/service/{AttendancePeriodTest,AttendanceRateServiceTest}.java`, `test/attendance/repository/AttendanceRepositoryTest.java`, `test/attendance/controller/AttendanceRateControllerTest.java`

**Interfaces:**
- Produces:
  - `record DateRange(LocalDate from, LocalDate to)` (둘 다 포함), `Instant start()` (from 00:00 KST), `Instant endExclusive()` (to+1일 00:00 KST)
  - `enum AttendancePeriod { DAY, WEEK, MONTH, QUARTER, YEAR }`, `DateRange rangeOf(LocalDate date)` (주는 월~일)
  - `MeetupRepository.long countHeld(Instant from, Instant to)` — `status = OPEN`, `startsAt >= from and < to`, `attendanceRecordedAt is not null or source = IMPORT`
  - `AttendanceRepository.long countAttended(long rosterMemberId, Instant from, Instant to)` — 위와 같은 벙 조건 + `result = ATTENDED`
  - `record AttendanceRateResponse(AttendancePeriod period, LocalDate from, LocalDate to, long attendedCount, long meetupCount, BigDecimal rate)` — `rate`는 백분율, 소수 첫째 자리 `HALF_UP`, 분모 0이면 `null`
  - `AttendanceRateService.mine(long userId, AttendancePeriod period, LocalDate date)` — `date` null이면 오늘(KST). 2000-01-01~2100-12-31 밖이면 `COMMON_VALIDATION_FAILED`. 명부가 없으면 `attendedCount` 0.
  - `GET /api/v1/attendance-rates/me?period=MONTH&date=2026-11-24` (MEMBER, `period` 필수, `date` 선택) → 200

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // AttendancePeriodTest
  @Test void 주는_월요일부터_일요일이다()        // 2026-11-23(월), 2026-11-29(일) 모두 → 11-23~11-29; 2027-01-01 → 2026-12-28~2027-01-03
  @Test void 월과_분기와_연의_첫날과_마지막날()  // 2028-02-29 MONTH → 02-01~02-29; 2026-12-31 QUARTER → 10-01~12-31; YEAR → 01-01~12-31
  @Test void 범위는_서울_자정_기준이다()         // 2026-11-24 DAY → start 2026-11-23T15:00:00Z, endExclusive 2026-11-24T15:00:00Z
  // AttendanceRepositoryTest (@DataJpaTest)
  @Test void 기간_시작_정각은_포함하고_끝_정각은_제외한다()
  @Test void 아직_기록되지_않은_벙은_분모에서_빠진다()
  @Test void 취소된_벙은_분모와_분자에서_빠진다()
  @Test void 이관된_벙은_분모와_분자에_들어간다()
  @Test void 불참과_노쇼와_남의_기록은_분자에_없다()
  // AttendanceRateServiceTest
  @Test void 비율은_소수_첫째_자리로_반올림한다()   // 2/3 → 66.7
  @Test void 열린_벙이_없으면_비율이_null이다()
  @Test void 명부가_없으면_참석_0회다()
  @Test void 날짜가_없으면_서울_오늘이다()         // clock 2026-11-23T15:30:00Z → 11-24
  @Test void 허용_날짜_바깥이면_400이다()          // 1999-12-31, 2101-01-01 거부 / 2000-01-01, 2100-12-31 허용
  // AttendanceRateControllerTest
  @Test void 일반회원은_403이다()
  @Test void 없는_기간이면_400이다()               // period=DECADE
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.attendance.*'` → 컴파일 실패
- [ ] **Step 3: 구현** (위 Interfaces 그대로)
- [ ] **Step 4: 통과 확인** 같은 명령 → 통과
- [ ] **Step 5: 커밋** `Feat(api) : 내 참석률 조회 추가`

---

### Task 3: 공지와 앱 설정

**Files:**
- Create: `db/V4__notices_app_settings.sql`
- Create: `main/notice/` 아래 `entity/Notice`, `repository/NoticeRepository`, `dto/{NoticeRequest,NoticeSummaryResponse,NoticeResponse}`, `service/{NoticeService,NoticePublishedEvent}`, `controller/NoticeController`
- Create: `main/setting/` 아래 `entity/{AppSetting,AppSettingKey}`, `repository/AppSettingRepository`, `dto/{AppSettingsUpdateRequest,AppSettingsResponse}`, `service/AppSettingService`, `controller/AppSettingController`
- Test: `test/notice/{service/NoticeServiceTest,repository/NoticeRepositoryTest,controller/NoticeControllerTest}.java`, `test/setting/{service/AppSettingServiceTest,controller/AppSettingControllerTest}.java`

**Interfaces:**
- Produces:
  - `notices`: `id`, `title VARCHAR(100)`, `body TEXT`, `pinned BOOLEAN DEFAULT FALSE`, `author_id` FK → users, `published_at DATETIME(6)` (모두 NOT NULL). 인덱스 `idx_notices_list (pinned, published_at, id)`.
  - `app_settings`: `setting_key VARCHAR(50)` PK, `setting_value VARCHAR(500) NOT NULL` (`key`는 예약어라 이름을 바꾼다). 초기 행은 넣지 않는다.
  - `record NoticeRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 10000) String body, boolean pinned)` (작성·수정 공용)
  - `record NoticeSummaryResponse(long id, String title, boolean pinned, Instant publishedAt)`, `record NoticeResponse(long id, String title, String body, boolean pinned, Instant publishedAt, Instant updatedAt)` (작성자는 공개하지 않는다)
  - `NoticeService`: `PageResponse<NoticeSummaryResponse> list(int page, int size)` (`pinned desc, publishedAt desc, id desc`), `get(long id)`, `create(long adminId, NoticeRequest)`, `update(long id, NoticeRequest)`, `delete(long id)`. 제목은 `strip()`, 본문은 그대로(마크다운). 없으면 404.
  - `record NoticePublishedEvent(long noticeId, long authorId, String title)` — `create`에서만, 트랜잭션 안에서 발행
  - `enum AppSettingKey { SOMOIM_JOIN_URL, SMARTSTORE_URL }` (DB 키는 enum 이름의 소문자)
  - `record AppSettingsResponse(String somoimJoinUrl, String smartstoreUrl)` (없으면 null)
  - `record AppSettingsUpdateRequest(@Size(max = 500) @Pattern(regexp = "https://\\S+") String somoimJoinUrl, 같은 제약 String smartstoreUrl)` — 전체 교체. null이면 그 행을 지운다.
  - `AppSettingService.get()`, `update(AppSettingsUpdateRequest)`
  - 엔드포인트: `GET /api/v1/notices`·`GET /api/v1/notices/{id}`·`GET /api/v1/app-settings` (공개 200), `POST /api/v1/notices` (ADMIN 201), `PUT /api/v1/notices/{id}`·`PUT /api/v1/app-settings` (ADMIN 200), `DELETE /api/v1/notices/{id}` (ADMIN 204)

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // NoticeRepositoryTest
  @Test void 고정_공지가_먼저고_그다음은_최신순이다()
  // NoticeServiceTest
  @Test void 작성하면_이벤트를_발행한다()
  @Test void 수정은_게시_시각을_바꾸지_않고_이벤트도_없다()
  @Test void 없는_공지를_수정하거나_지우면_404다()
  // NoticeControllerTest
  @Test void 토큰_없이_목록과_상세를_본다()
  @Test void 동호회원이_작성하면_403이다()
  @Test void 본문은_만_자까지다()               // 10000 → 201, 10001 → 400
  // AppSettingServiceTest
  @Test void 값이_없으면_null이다()
  @Test void null로_바꾸면_행을_지운다()
  // AppSettingControllerTest
  @Test void 토큰_없이_설정을_본다()
  @Test void https가_아니면_400이다()           // http://a.com, javascript:alert(1)
  @Test void 동호회원이_바꾸면_403이다()
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.notice.*' --tests 'com.theclimbingnight.setting.*'` → 컴파일 실패
- [ ] **Step 3: 구현**
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Feat(api) : 공지와 앱 설정 추가`

---

### Task 4: 1:1 문의

**Files:**
- Create: `db/V5__inquiries.sql`
- Create: `main/inquiry/` 아래 `entity/{Inquiry,InquiryStatus}`, `repository/InquiryRepository`, `dto/{InquiryCreateRequest,InquiryAnswerRequest,InquiryResponse,InquiryAdminResponse}`, `service/{InquiryService,InquiryAnsweredEvent}`, `controller/InquiryController`
- Test: `test/inquiry/{service/InquiryServiceTest,repository/InquiryRepositoryTest,controller/InquiryControllerTest}.java`

**Interfaces:**
- Produces:
  - `inquiries`: `id`, `user_id NOT NULL` FK, `title VARCHAR(100) NOT NULL`, `body TEXT NOT NULL`, `status VARCHAR(16) NOT NULL`, `answer TEXT NULL`, `answered_by NULL` FK → users, `answered_at NULL`. 인덱스 `(user_id, id)`, `(status, id)`.
  - `enum InquiryStatus { OPEN, ANSWERED }`
  - `Inquiry`: `static create(User, String title, String body)`, `void answer(String answer, User admin, Instant now)`, `boolean isAnswered()`
  - `record InquiryCreateRequest(@NotBlank @Size(max = 100) title, @NotBlank @Size(max = 2000) body)`, `record InquiryAnswerRequest(@NotBlank @Size(max = 2000) answer)`
  - `record InquiryResponse(long id, String title, String body, InquiryStatus status, String answer, Instant answeredAt, Instant createdAt)`
  - `record InquiryAdminResponse(InquiryResponse의 필드 전부 + long userId, String nickname)` — 탈퇴한 문의자는 `nickname` null
  - `InquiryRepository`: `findByIdForUpdate(long id)` (사용자 fetch 없음), 내 목록 `findAllByUserId(long, Pageable)`, 운영진 목록은 `join fetch user` + `countQuery`
  - `InquiryService`: `create(userId, request)`, `PageResponse<InquiryResponse> mine(userId, page, size)` (id desc), `getMine(userId, id)` (남의 것 404), `PageResponse<InquiryAdminResponse> list(InquiryStatus status, page, size)` (status null이면 전체, id desc), `InquiryAdminResponse get(id)`, `answer(id, adminId, request)`
  - `answer`: 문의 행을 잠근다. 이미 답변된 문의는 답변을 덮어쓰고(오타 수정) 이벤트는 첫 답변에만 발행한다. `record InquiryAnsweredEvent(long inquiryId, long userId)`.
  - 엔드포인트: `POST /api/v1/inquiries` (USER 201), `GET /api/v1/inquiries/me`·`GET /api/v1/inquiries/me/{id}` (USER), `GET /api/v1/inquiries?status=OPEN`·`GET /api/v1/inquiries/{id}`·`PUT /api/v1/inquiries/{id}/answer` (ADMIN 200)

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // InquiryServiceTest
  @Test void 남의_문의를_조회하면_404다()
  @Test void 첫_답변에만_이벤트를_발행한다()       // 두 번 답변 → 이벤트 1번, 답변은 마지막 값
  @Test void 탈퇴한_문의자의_닉네임은_null이다()
  // InquiryRepositoryTest
  @Test void 내_문의만_최신순으로_나온다()
  @Test void 상태로_거르고_없으면_전체다()
  // InquiryControllerTest
  @Test void 일반회원이_문의를_남긴다()
  @Test void 동호회원이_전체_목록을_보면_403이다()
  @Test void 본문은_이천_자까지다()               // 2000 → 201, 2001 → 400
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.inquiry.*'` → 컴파일 실패
- [ ] **Step 3: 구현**
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Feat(api) : 1대1 문의와 답변 추가`

---

### Task 5: 푸시 토큰과 알림 설정

**Files:**
- Create: `db/V6__push.sql`
- Create: `main/push/` 아래 `entity/{PushToken,PushPlatform,NotificationSetting,PushTicket}`, `repository/{PushTokenRepository,NotificationSettingRepository,PushTicketRepository}`, `dto/{PushTokenRegisterRequest,NotificationSettingRequest,NotificationSettingResponse}`, `service/{PushTokenService,PushTokenWriter,NotificationSettingService,PushWithdrawalListener}`, `controller/{PushTokenController,NotificationSettingController}`
- Modify: `main/auth/dto/LogoutRequest.java`, `main/auth/controller/AuthController.java`, `main/auth/service/AuthService.java`
- Test: `test/push/repository/PushTokenRepositoryTest.java`, `test/push/service/{PushTokenServiceTest,PushTokenWriterTest,NotificationSettingServiceTest,PushWithdrawalListenerTest}.java`, `test/push/controller/PushTokenControllerTest.java`, 기존 `AuthServiceTest`·`AuthControllerTest`

**Interfaces:**
- Produces:
  - `push_tokens`: `id`, `user_id` FK, `expo_push_token VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` (대소문자를 구분하므로 기본 콜레이션을 쓰지 않는다), `platform VARCHAR(16)`, `last_seen_at`. `uk_push_tokens_token UNIQUE (expo_push_token)`, 인덱스 `(user_id)`.
  - `notification_settings`: `user_id` PK·FK, `new_meetup BOOLEAN NOT NULL`, `new_notice BOOLEAN NOT NULL`. **행이 없으면 둘 다 켜짐.**
  - `push_tickets`: `id`, `ticket_id VARCHAR(64) NOT NULL`, `expo_push_token` (위와 같은 타입), `sent_at`. 인덱스 `(sent_at, id)`.
  - `meetups`에 `reminder_sent_at DATETIME(6) NULL` 추가 (엔티티 매핑은 Task 7)
  - `record PushTokenRegisterRequest(@NotNull @Pattern(regexp = "Expo(nent)?PushToken\\[[A-Za-z0-9_-]{1,200}\\]") String expoPushToken, @NotNull PushPlatform platform)`; `enum PushPlatform { IOS, ANDROID }`
  - `PushTokenWriter` (`@Transactional`): `void upsert(long userId, String token, PushPlatform platform)` — 사용자 행 잠금(활성 아니면 `AUTH_REQUIRED`) → 토큰이 있으면 소유자·플랫폼·`lastSeenAt` 갱신, 없으면 `saveAndFlush`
  - `PushTokenService`: `register(...)` (**트랜잭션 없음**. `upsert`가 `uk_push_tokens_token` 위반이면 한 번만 다시 부른다), `@Transactional void deleteOwned(long userId, String token)`
  - `PushTokenRepository` 발송 대상 조회 (모두 `List<String>` 토큰, 활성 사용자만): `findTokensByUserIds(Collection<Long>)`, `findTokensForNewMeetup(long excludeUserId)` (역할 MEMBER·ADMIN, 설정 꺼진 사람 제외), `findTokensForNewNotice(long excludeUserId)` (전 역할, 설정 꺼진 사람 제외), `int deleteIfNotSeenSince(String token, Instant sentAt)` (`lastSeenAt <= sentAt`일 때만 삭제), `int deleteAllByUserId(long)`
  - `PushWithdrawalListener.on(UserWithdrawnEvent)` — 동기 `@EventListener @Transactional`. 토큰·설정 삭제
  - `record LogoutRequest(@NotBlank String refreshToken, String expoPushToken)`, `AuthService.logout(String refreshToken, String expoPushToken)` — 리프레시 토큰이 DB에 있고 `expoPushToken`이 있으면 그 토큰 소유자 기준 `deleteOwned`
  - `NotificationSettingRequest`/`Response(boolean newMeetup, boolean newNotice)` (요청은 `@NotNull Boolean`)
  - 엔드포인트 (모두 USER): `PUT /api/v1/push-tokens` 204, `GET /api/v1/notification-settings/me` 200, `PUT /api/v1/notification-settings/me` 200

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // PushTokenRepositoryTest
  @Test void 대소문자만_다른_토큰은_다른_토큰이다()
  @Test void 새_벙_대상은_활성_동호회원과_운영진이고_개설자와_끈_사람은_빠진다()
  @Test void 새_공지_대상은_일반회원도_포함하고_탈퇴자와_끈_사람은_빠진다()
  @Test void 설정_행이_없으면_대상이다()
  @Test void 발송_뒤_다시_등록된_토큰은_지우지_않는다()      // lastSeenAt > sentAt 유지, == sentAt 삭제
  // PushTokenWriterTest
  @Test void 같은_토큰을_다른_사용자가_등록하면_소유자가_바뀐다()
  @Test void 탈퇴한_사용자는_등록할_수_없다()               // AUTH_REQUIRED
  // PushTokenServiceTest
  @Test void 동시_등록으로_유니크_위반이면_한_번_다시_시도한다()
  @Test void 다른_제약_위반은_그대로_던진다()
  // NotificationSettingServiceTest
  @Test void 설정이_없으면_모두_켜짐이다()
  @Test void 설정을_바꾸면_저장된다()
  // PushWithdrawalListenerTest
  @Test void 탈퇴하면_토큰과_설정을_지운다()
  // PushTokenControllerTest
  @Test void 형식이_다른_토큰은_400이다()                  // "abc", "ExponentPushToken[]", 빈칸 포함
  // AuthServiceTest
  @Test void 로그아웃하면_내_토큰만_지운다()
  @Test void 푸시_토큰_없이도_로그아웃된다()
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.push.*' --tests 'com.theclimbingnight.auth.*'` → 컴파일 실패
- [ ] **Step 3: 구현.** 설정 저장은 사용자 행을 잠근 뒤 없으면 만들고 있으면 고친다 (동시 요청의 PK 충돌 방지).
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Feat(api) : 푸시 토큰과 알림 설정 추가`

---

### Task 6: Expo 발송과 수신 확인

**Files:**
- Create: `main/push/{PushProperties,PushConfig}.java`, `main/push/expo/{PushGateway,ExpoPushClient,NoopPushGateway,PushMessage,PushTicketResult,PushReceipt,PushTransientException,PushRequestException}.java`, `main/push/service/{PushSender,PushTicketWriter,PushReceiptJob,Pause}.java`
- Modify: `apps/api/src/main/resources/application.yml`, `application-prod.yml`, `apps/api/.env.example` (`EXPO_ACCESS_TOKEN=`)
- Test: `test/push/expo/ExpoPushClientTest.java`, `test/push/service/{PushSenderTest,PushReceiptJobTest}.java`

**Interfaces:**
- Consumes: Task 5 리포지토리
- Produces:
  - 설정: `app.push.enabled: false`(기본)·`true`(prod), `app.push.base-url: https://exp.host`, `app.push.access-token: ${EXPO_ACCESS_TOKEN:}`. `record PushProperties(boolean enabled, String baseUrl, String accessToken)` `@ConfigurationProperties("app.push")`
  - `record PushMessage(String to, String title, String body, Map<String, Object> data)`
  - `record PushTicketResult(String ticketId, String error)` — 성공이면 `error` null, 실패면 `details.error` (없으면 `"UNKNOWN"`)
  - `record PushReceipt(String error)` — 성공이면 null
  - `interface PushGateway { List<PushTicketResult> send(List<PushMessage> messages); Map<String, PushReceipt> receipts(List<String> ticketIds); }`
  - `ExpoPushClient` (`enabled=true`일 때만 빈): `POST {baseUrl}/--/api/v2/push/send` 본문은 메시지 배열, 각 메시지에 `sound: "default"`, `channelId: "default"`, `priority: "high"`를 더한다. `POST {baseUrl}/--/api/v2/push/getReceipts` 본문 `{"ids": [...]}`. access token이 비어 있지 않으면 `Authorization: Bearer`. 429·5xx·연결 실패 → `PushTransientException`, 그 밖의 4xx·응답 `errors`·티켓 수 불일치 → `PushRequestException`(코드만 담고 토큰은 담지 않음). 타임아웃 5초(`SocialConfig`와 같은 방식, 빈 이름 `pushRestClient`).
  - `NoopPushGateway` (`enabled`가 true가 아닐 때): 성공도 실패도 아닌 빈 결과. `PushSender`는 빈 결과면 티켓을 저장하지 않는다.
  - `PushConfig`: `@Bean TaskExecutor pushExecutor` (구현은 `ThreadPoolTaskExecutor`) — core·max 1, 큐 1000, 종료 시 대기 10초
  - `interface Pause { void sleep(Duration d); }` (테스트에서 대기 없이)
  - `PushSender.void send(Collection<String> tokens, String title, String body, Map<String, Object> data)` — 중복 제거 후 100개씩. `PushTransientException`이면 2초, 4초 뒤 다시(총 3번). 끝내 실패하거나 `PushRequestException`이면 `log.error` 후 다음 묶음. 티켓 `DeviceNotRegistered` → `deleteIfNotSeenSince(token, 발송 시각)`, 그 밖의 티켓 오류 → 오류 코드만 `log.warn`. 자신은 트랜잭션이 없고, 저장·삭제는 `PushTicketWriter`(`REQUIRES_NEW`)가 한다 (커밋 후 단계에서 불려도 저장되게).
  - `PushReceiptJob.run()` `@Scheduled(fixedDelay = 900_000)` — `sentAt <= now-15분`인 티켓을 id 순 1000개씩(마지막 id 이후로 이어서) 조회. 결과가 온 티켓은 삭제, `DeviceNotRegistered`면 `deleteIfNotSeenSince(token, sentAt)`, 다른 오류는 `log.error`(코드만). 결과가 없는 티켓은 남기고 24시간이 지나면 삭제. 게이트웨이 예외는 잡아 로그만 남긴다.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // ExpoPushClientTest (MockRestServiceServer)
  @Test void 메시지_배열을_보내고_티켓을_순서대로_돌려준다()
  @Test void 토큰이_있으면_인증_헤더를_붙이고_없으면_뺀다()
  @Test void 오류_티켓은_details의_error를_담는다()           // DeviceNotRegistered
  @Test void 과다_요청과_서버_오류는_일시_오류다()            // 429, 503
  @Test void 잘못된_요청과_errors_응답은_요청_오류다()        // 400, 200 {"errors":[{"code":"UNAUTHORIZED"}]}
  @Test void 티켓_수가_다르면_요청_오류다()
  @Test void 수신_확인을_ID별로_돌려준다()
  // PushSenderTest
  @Test void 백_개씩_나눠_보낸다()                           // 100 → 1번, 101 → 2번(100+1), 0 → 0번
  @Test void 무효_토큰은_지우고_나머지는_보낸다()
  @Test void 일시_오류는_두_번까지_다시_보낸다()              // Pause 2초·4초, 세 번째 성공
  @Test void 요청_오류는_다시_보내지_않는다()
  @Test void 한_묶음이_실패해도_다음_묶음을_보낸다()
  @Test void 성공한_티켓만_저장한다()
  // PushReceiptJobTest
  @Test void 십오_분이_지난_티켓만_확인한다()                 // 정각 포함
  @Test void 무효_기기면_토큰과_티켓을_지운다()
  @Test void 결과가_없는_티켓은_남기고_하루가_지나면_지운다()
  @Test void 천_개가_넘으면_나눠_조회한다()
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.push.*'` → 컴파일 실패
- [ ] **Step 3: 구현.** `application.yml`의 값은 위 설정대로. `.env.example`은 그 파일만 다루는 명령으로 고친다 (`deploy/`의 compose 등은 아직 없어 계획 8에서 반영).
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Feat(api) : Expo 푸시 발송과 수신 확인 추가`

---

### Task 7: 이벤트 연결, 벙 리마인드, 통합 흐름

**Files:**
- Create: `main/meetup/service/{MeetupCreatedEvent,MeetupReminderDueEvent,MeetupReminderService,MeetupReminderJob}.java`, `main/push/service/{PushEventListener,PushNotifier}.java`
- Modify: `main/roster/repository/RosterMemberRepository.java`, `main/meetup/service/MeetupAdminService.java`, `main/meetup/entity/Meetup.java`, `main/meetup/repository/MeetupRepository.java`
- Modify: `docs/architecture.md` (10장에 `notification_settings`·`push_tickets`·`meetups.reminder_sent_at`, `app_settings` 컬럼명. 8장 참석률 분모 기준. 11장 리마인드 시각)
- Test: `test/push/service/{PushEventListenerTest,PushNotifierTest}.java`, `test/meetup/service/{MeetupReminderServiceTest,MeetupReminderJobTest,MeetupAdminServiceTest}.java`, `test/PushFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `PushSender`, `pushExecutor`, `PushTokenRepository`, `MeetupCanceledEvent`, `MeetupApplicationPromotedEvent`, `MembershipReviewedEvent`, `NoticePublishedEvent`, `InquiryAnsweredEvent`, `RosterMemberRepository`
- Produces:
  - `record MeetupCreatedEvent(long meetupId, long hostUserId)` — `MeetupAdminService.create`에서 (복제는 `create`를 거치므로 한 번만)
  - `record MeetupReminderDueEvent(long meetupId, List<Long> rosterMemberIds)`
  - `Meetup.reminderSentAt` 매핑, `void markReminderSent(Instant now)`. `apply(spec)`에서 `startsAt`이 달라지면 `reminderSentAt = null`
  - `MeetupRepository.List<Long> findReminderDueIds(Instant now, Instant until)` — `OPEN`·`APP`, `reminderSentAt is null`, `startsAt > now and < until`, id 순
  - `MeetupReminderService.@Transactional boolean remind(long meetupId, Instant until)` — 벙 잠금 뒤 위 조건을 다시 확인하고 표시. 확정자가 있으면 이벤트 발행
  - `MeetupReminderJob.run()` `@Scheduled(fixedDelay = 60_000)`, 트랜잭션 없음, 벙마다 예외를 잡는다. KST 시각이 09시 전이면 아무것도 안 함, 09:00~19:59면 `until` = 내일 00:00, 20:00부터는 `until` = 모레 00:00
  - `PushEventListener`: 이벤트마다 `@TransactionalEventListener(phase = AFTER_COMMIT)` 메서드 하나. `pushExecutor.execute(() -> notifier.xxx(event))`만 한다. 작업 안의 예외는 잡아 `log.error`, `RejectedExecutionException`은 `log.warn`
  - `PushNotifier`: 대상 조회와 문구. 조회는 리포지토리만 쓰고 LAZY 연관을 건드리지 않는다. 명부 ID는 `RosterMemberRepository.findLinkedUserIds(Collection<Long> rosterIds)`(추가, JPQL `select r.user.id … where r.user is not null`)로 바꾼다. 벙이 그사이 취소됐으면 새 벙·승격·리마인드는 보내지 않는다.

    시각 표기는 KST `M월 d일(E) HH:mm` (`Locale.KOREAN`).

    | 이벤트 | 받는 사람 | 제목 | 본문 | data |
    |---|---|---|---|---|
    | 인증 승인 | 신청자 | 동호회원 인증 완료 | 이제 벙에 신청할 수 있어요. | `type: MEMBERSHIP_APPROVED` |
    | 인증 거절 | 신청자 | 동호회원 인증 결과 | 인증 신청이 거절되었어요. 사유를 확인해 주세요. | `type: MEMBERSHIP_REJECTED` |
    | 승격 | 해당 회원 | 벙 참여 확정 | {제목} 대기에서 확정되었어요. {시각} | `type: MEETUP_PROMOTED, meetupId` |
    | 벙 취소 | 확정·대기자 | 벙 취소 | {제목} 벙이 취소되었어요. {시각} | `type: MEETUP_CANCELED, meetupId` |
    | 새 벙 | MEMBER·ADMIN (개설자 제외, 설정) | 새 벙이 열렸어요 | {제목} · {시각} | `type: MEETUP_CREATED, meetupId` |
    | 새 공지 | 활성 사용자 (작성자 제외, 설정) | 새 공지 | {제목} | `type: NOTICE_PUBLISHED, noticeId` |
    | 문의 답변 | 문의자 | 문의 답변 도착 | 문의하신 내용에 답변이 등록되었어요. | `type: INQUIRY_ANSWERED, inquiryId` |
    | 리마인드 | 확정자 | 벙 일정 알림 | {제목} · {시각} | `type: MEETUP_REMINDER, meetupId` |

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // PushNotifierTest
  @Test void 이벤트별_문구와_대상()                      // 표의 8행을 각각 단언 (@ParameterizedTest 또는 개별)
  @Test void 명부_연결이_없으면_건너뛴다()
  @Test void 그사이_취소된_벙은_새_벙_알림을_보내지_않는다()
  // PushEventListenerTest
  @Test void 큐가_차도_예외를_던지지_않는다()
  @Test void 발송_실패가_밖으로_새지_않는다()
  // MeetupReminderServiceTest
  @Test void 확정자만_대상이고_한_번만_보낸다()
  @Test void 확정자가_없으면_표시만_하고_이벤트는_없다()
  @Test void 시작_시각이_바뀌면_리마인드를_다시_보낸다()   // 시각이 같은 수정은 유지
  // MeetupReminderJobTest (KST)
  @Test void 아홉시_전에는_보내지_않는다()                 // 08:59:59 없음, 09:00 실행
  @Test void 놓친_리마인드는_당일_아홉시부터_보낸다()       // 09:00 until = 내일 00:00
  @Test void 스무시부터_내일_벙까지_보낸다()               // 19:59:59 → 내일 00:00, 20:00 → 모레 00:00
  @Test void 한_벙이_실패해도_다음_벙을_처리한다()
  // MeetupAdminServiceTest에 추가
  @Test void 복제하면_개설_이벤트를_한_번_발행한다()
  // PushFlowIntegrationTest (@SpringBootTest, 기록용 @Primary PushGateway,
  //   "pushExecutor"는 @TestBean으로 SyncTaskExecutor 교체, 닉네임 접두사 "푸시플로우",
  //   전후로 push_tickets·push_tokens·notification_settings까지 정리)
  @Test void 벙을_취소하면_확정자와_대기자에게만_간다()
  @Test void 롤백되면_푸시를_보내지_않는다()               // TransactionTemplate에서 cancel 뒤 setRollbackOnly
  @Test void 공지를_끈_사람에게는_공지가_가지_않는다()
  @Test void 무효_토큰은_발송_뒤_사라진다()                // REQUIRES_NEW 저장 확인
  @Test void 탈퇴하면_더는_받지_않는다()
  ```
- [ ] **Step 2: 실패 확인** Run: `./gradlew test --tests 'com.theclimbingnight.push.*' --tests 'com.theclimbingnight.meetup.*' --tests '*PushFlowIntegrationTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** `PushEventListener`는 `@Qualifier("pushExecutor") TaskExecutor`를 받는다. 통합 테스트는 이 빈을 `@TestBean(name = "pushExecutor")`(`org.springframework.test.context.bean.override.convention`)로 바꾼다.
- [ ] **Step 4: 전체 통과 확인** Run: `./gradlew test` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** `Feat(api) : 도메인 이벤트 푸시와 벙 리마인드 추가`
- [ ] **Step 6: `docs/architecture.md` 반영 후 커밋** `Docs : 참석률 분모와 푸시 데이터 모델 반영`

---

## 이 계획이 끝나면 되는 것

- 1단계 API가 모두 갖춰진다. 다음은 계획 5(모바일 앱 기반).
- 계획 5·6이 이어받는 것: 앱은 로그인 뒤와 실행 때마다 `PUT /push-tokens`, 로그아웃 때 `expoPushToken`을 함께 보낸다. Android 알림 채널 ID는 `default`. 푸시를 누르면 `data.type`으로 화면을 고른다.
- 계획 8이 이어받는 것: `EXPO_ACCESS_TOKEN`을 `deploy/`에 반영, Expo 대시보드에서 enhanced push security 켜기, 운영 MySQL 격리 수준 확인.
- 정해 둔 것(스펙에 없던 결정):
  - 참석률 분모는 참석 기록이 끝난 벙(이관 벙 포함)이다.
  - 새 벙·새 공지는 만든 운영진 본인에게 보내지 않는다.
  - 리마인드는 전날 20시, 놓치면 당일 09시부터 시작 전까지 한 번.
  - 답변을 고쳐도 푸시는 다시 보내지 않는다.
  - 탈퇴해도 문의 글은 남고 작성자 닉네임만 사라진다.
  - 앱 설정은 운영진 API로 바꾼다 (화면은 1단계에 없음).
  - 알림은 유실될 수 있다 (서버 재시작, 큐 초과).
