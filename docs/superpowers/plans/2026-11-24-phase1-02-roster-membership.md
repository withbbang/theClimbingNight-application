# 1단계-02 명부와 동호회원 인증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 동호회원 명부(roster)를 CSV로 일괄 등록하고, USER의 인증 신청을 운영진이 승인(개별·일괄)·거절해 명부 항목과 1:1로 연결하며, 자격 해제와 탈퇴 시 연결 해제까지 API로 완성한다.

**Architecture:** 새 도메인 패키지 `roster`(명부·CSV 등록)와 `membership`(신청·심사·회원 목록)을 만든다. 동시에 몰리는 쓰기(신청 중복, 같은 명부 항목에 두 계정 연결, 승인과 탈퇴의 경합)는 `PESSIMISTIC_WRITE` 행 잠금과 `uk_roster_members_user` 유니크 제약으로 막는다. 잠금 순서는 어디서나 **사용자 → 신청 → 명부**다. 탈퇴는 계획 1의 `UserWithdrawnEvent`를 같은 트랜잭션 안에서 구독해 처리한다.

**Tech Stack:** 계획 1과 같음 (Spring Boot 4.1.2, Java 17, JPA, Flyway, H2 MySQL 모드/MySQL 8.4) + `org.apache.commons:commons-csv:1.14.1`.

**Spec:** `docs/architecture.md` 6장 "동호회원 명부와 인증"·"가입과 로그인"의 탈퇴 항목, 9장 운영진 화면 목록, 10장 `roster_members`·`membership_requests`. 규약은 `.claude/rules/api.md`, `.claude/rules/testing.md`, `api-conventions` 스킬. 계획 1: `docs/superpowers/plans/2026-11-23-phase1-01-api-foundation-auth.md` (1단계 전체 계획 순서는 거기 있다).

## 작업 방식

- 브랜치: `dev`에서 `feat/api-membership`을 딴다. 태스크마다 커밋한다.
- 커밋 메시지: `.gitmessage.txt` 정책. 형식 `Feat(api) : 제목` (50자 이내), 본문 `-` 목록, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 모든 태스크 끝나면 `/code-review low feat/api-membership` → 버그만 고치고 테스트로 확인 → 리뷰 기록 → `/claude-md-management:revise-claude-md` → 병합은 요청 시 (CLAUDE.md 절차).
- 테스트 실행: `cd apps/api && ./gradlew test` (특정 클래스는 `--tests '<FQCN>'`). context-mode 훅이 Bash의 `./gradlew`를 막으면 `ctx_execute`(shell)로 실행한다.
- 새 에러 코드를 `ErrorCode`에 넣는 태스크는 같은 커밋에서 `api-conventions` 스킬의 에러 코드 표(`.claude/skills/api-conventions/SKILL.md`)에도 같은 줄을 추가한다.

## Global Constraints

- 계획 1의 Global Constraints를 그대로 따른다 (패키지 구조, 응답 래퍼, record DTO, Lombok 제한, 생성자 주입, `@Transactional(readOnly = true)`, `Clock` 주입, 한국어 테스트 이름, AssertJ).
- 엔티티 연관은 계획 1처럼 `@ManyToOne(fetch = LAZY)` + `@JoinColumn`. 상태 변경은 엔티티 메서드로만.
- 권한: 신청·내 신청 조회는 인증만(`@CurrentUser`), 그 밖의 이 계획 API는 전부 `@RequireRole(Role.ADMIN)`.
- 소모임 닉네임: 앞뒤 공백을 자른 뒤 1~50자. 비교는 항상 대소문자 무시(`lower()`). 명부에서 유니크가 아니다 (동명이인·닉네임 변경 대비, 스펙 10장).
- 잠금 순서: **사용자(`users`) → 신청(`membership_requests`) → 명부(`roster_members`)**. 승인·거절은 신청을 잠그지 않고 읽어 `userId`를 얻은 뒤 사용자를 잠그고, 그다음 신청을 잠가 상태를 다시 확인한다. 탈퇴 리스너는 신청을 먼저, 명부를 나중에 고친다.
- 페이징 목록: `?page=0&size=20`, `page >= 0`, `1 <= size <= 100` (범위 밖은 400 `COMMON_VALIDATION_FAILED`). 응답 `PageResponse` (`items, page, size, totalElements, totalPages`).
- 푸시 알림은 계획 4에서 붙인다. 이 계획은 `MembershipReviewedEvent`만 발행한다.

## Review Focus

1. **한국어 Excel에서 그냥 저장한 CSV(CP949)** → 글자가 깨진 채 명부에 들어가면 안 된다. 엄격한 UTF-8 디코딩으로 400 `ROSTER_CSV_ENCODING`. Task 2 테스트 `UTF8이_아니면_ROSTER_CSV_ENCODING이다`.
2. **두 사람이 같은 소모임 닉네임으로 신청했는데 명부 항목은 하나** → 각자 후보가 하나로 보여도 일괄 승인하면 먼저 처리된 쪽이 남의 기록을 가져갈 수 있다. 둘 다 일괄 승인 대상에서 빼서 운영진이 직접 본다. Task 4 테스트 `후보가_하나지만_다른_대기_신청과_겹치면_일괄_승인_대상이_아니다`.
3. **자격 해제 후 재신청** → 명부 연결이 남아 있으므로 후보는 연결된 그 항목 하나이고, 승인하면 같은 항목으로 이어진다. 새 항목을 만들거나 다른 항목을 고르면 409. Task 4 테스트 `자격_해제_후_재신청은_기존_명부로_이어진다`.
4. **승인 버튼을 두 운영진이 동시에 누름, 또는 승인 직후 사용자가 탈퇴** → 신청은 한 번만 승인되고(두 번째는 409), 데드락 없이 끝난다. 잠금 순서로 보장하고 Task 4 테스트 `이미_처리된_신청이면_409다`와 Task 3 리스너 테스트가 상태 재확인을 고정한다.
5. **같은 CSV를 두 번 올림, 파일 안에 같은 닉네임이 대소문자만 다르게 두 번** → 두 번째 업로드는 전부 건너뜀, 파일 안 중복은 첫 행만 추가. Task 2 테스트 `파일_안의_중복은_첫_행만_추가한다`, Task 6 통합 테스트.

---

### Task 1: 명부·신청 스키마와 엔티티

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V2__roster_membership.sql`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/entity/{RosterMember,RosterSource}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/repository/RosterMemberRepository.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/entity/{MembershipRequest,MembershipRequestStatus}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/repository/MembershipRequestRepository.java`
- Modify: `apps/api/src/main/java/com/theclimbingnight/user/entity/User.java` (`changeRole` 추가)
- Modify: `apps/api/src/main/java/com/theclimbingnight/user/repository/UserRepository.java` (`findByIdForUpdate` 추가)
- Test: `apps/api/src/test/java/com/theclimbingnight/roster/repository/RosterMemberRepositoryTest.java`, `apps/api/src/test/java/com/theclimbingnight/membership/repository/MembershipRequestRepositoryTest.java`

**Interfaces:**
- Consumes: `User`, `BaseTimeEntity`
- Produces:
  - 스키마 (`utf8mb4_0900_ai_ci`, 계획 1의 V1과 같은 꼴):
    - `roster_members`: `id`, `somoim_nickname VARCHAR(50) NOT NULL`, `user_id BIGINT NULL` FK `fk_roster_members_user` + `CONSTRAINT uk_roster_members_user UNIQUE (user_id)`, `note VARCHAR(255) NULL`, `source VARCHAR(16) NOT NULL`, `created_at`, `updated_at`. 인덱스 `idx_roster_members_nickname (somoim_nickname)`.
    - `membership_requests`: `id`, `user_id BIGINT NOT NULL` FK, `somoim_nickname VARCHAR(50) NOT NULL`, `status VARCHAR(16) NOT NULL`, `reject_reason VARCHAR(200) NULL`, `roster_member_id BIGINT NULL` FK, `reviewed_by BIGINT NULL` FK → users, `reviewed_at DATETIME(6) NULL`, `created_at`, `updated_at`. 인덱스 `idx_membership_requests_status (status, id)`, `idx_membership_requests_user (user_id, id)`.
  - `enum RosterSource { APP, IMPORT }`, `enum MembershipRequestStatus { PENDING, APPROVED, REJECTED }`
  - `RosterMember`: `static RosterMember create(String somoimNickname, String note, RosterSource source)`, `void linkTo(User user)`, `void unlink()`, `User getUser()` (nullable)
  - `MembershipRequest`: `static MembershipRequest create(User user, String somoimNickname)` (PENDING), `boolean isPending()`, `void approve(RosterMember rosterMember, User reviewer, Instant now)`, `void reject(String reason, User reviewer, Instant now)` (`reviewer`는 null 가능: 탈퇴 자동 종료). PENDING이 아닐 때 `approve`/`reject`는 `IllegalStateException` (서비스가 먼저 막으므로 방어용).
  - `User.changeRole(Role role)`
  - 아래 리포지토리 메서드 중 이름이 파생 쿼리 규칙에 맞지 않는 것(`...ForUpdate`, `findUnlinked...`, `findExisting...`, `search`, `unlinkUser`)은 모두 `@Query`(JPQL)로 쓴다. `...ForUpdate`는 `@Lock(PESSIMISTIC_WRITE)`도 붙인다.
  - `UserRepository`: `@Lock(PESSIMISTIC_WRITE) @Query("select u from User u where u.id = :id") Optional<User> findByIdForUpdate(@Param("id") long id)`
  - `RosterMemberRepository extends JpaRepository<RosterMember, Long>`:
    - `Optional<RosterMember> findByUserId(long userId)`
    - `List<RosterMember> findAllByUserIdIn(Collection<Long> userIds)`
    - `Optional<RosterMember> findByIdForUpdate(long id)` (`PESSIMISTIC_WRITE`)
    - `List<RosterMember> findUnlinkedByLowerNicknames(Collection<String> loweredNicknames)` — JPQL `lower(r.somoimNickname) in :lowered and r.user is null`
    - `List<String> findExistingLowerNicknames(Collection<String> loweredNicknames)` — `select distinct lower(r.somoimNickname) ... in :lowered`
    - `Page<RosterMember> search(String q, Boolean linked, Pageable pageable)` — `q`가 null이면 무시, 아니면 `lower(somoimNickname) like lower(concat('%', :q, '%'))`; `linked`가 null이면 무시, true면 `user is not null`, false면 `user is null`
    - `@Modifying(flushAutomatically = true, clearAutomatically = true) int unlinkUser(long userId)` — `update RosterMember r set r.user = null where r.user.id = :userId`
  - `MembershipRequestRepository extends JpaRepository<MembershipRequest, Long>`:
    - `boolean existsByUserIdAndStatus(long userId, MembershipRequestStatus status)`
    - `Optional<MembershipRequest> findFirstByUserIdOrderByIdDesc(long userId)`
    - `Optional<MembershipRequest> findByIdForUpdate(long id)` (`PESSIMISTIC_WRITE`)
    - `Page<MembershipRequest> findByStatus(MembershipRequestStatus status, Pageable pageable)`
    - `List<MembershipRequest> findAllByStatusOrderByIdAsc(MembershipRequestStatus status)`
    - `List<MembershipRequest> findAllByUserIdAndStatus(long userId, MembershipRequestStatus status)`

- [ ] **Step 1: 마이그레이션 작성** `V2__roster_membership.sql` (위 스키마 그대로. V1은 고치지 않는다)

- [ ] **Step 2: 실패하는 테스트 작성** (`@DataJpaTest`)
  ```java
  // RosterMemberRepositoryTest
  @Test void 한_계정은_명부_항목_하나에만_연결된다()
  // 두 항목에 같은 user를 linkTo → saveAndFlush 두 번째에서 DataIntegrityViolationException,
  // ConstraintViolations.violated(e)에 "uk_roster_members_user" 포함
  @Test void 대소문자와_무관하게_미연결_항목만_찾는다()
  // "Climber"(미연결), "climber"(연결됨), "other" → findUnlinkedByLowerNicknames(["climber"]) = ["Climber"] 1건
  @Test void 이미_있는_닉네임을_소문자로_돌려준다()
  // "Alpha" 저장 → findExistingLowerNicknames(["alpha","beta"]) = ["alpha"]
  @Test void 탈퇴_회원의_연결을_끊어도_명부_항목은_남는다()
  // linkTo(user) → unlinkUser(user.id) = 1 → findById 존재, getUser() null
  @Test void 명부_검색은_부분일치와_연결여부로_거른다()
  // "김철수"(연결), "박철수"(미연결), "영희" → search("철수", false) = ["박철수"], search(null, null) totalElements 3
  // MembershipRequestRepositoryTest
  @Test void 사용자의_가장_최근_신청을_찾는다()
  @Test void 상태별로_오래된_순서로_찾는다()
  ```

- [ ] **Step 3: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.roster.*' --tests 'com.theclimbingnight.membership.*'` → 컴파일 실패

- [ ] **Step 4: 엔티티·리포지토리 구현** (위 Interfaces 그대로)

- [ ] **Step 5: 통과 확인** 같은 명령 → 7개 통과, `ddl-auto: validate`로 스키마 일치. 이어서 `./gradlew test` 전체 통과

- [ ] **Step 6: 커밋** `Feat(api) : 명부·동호회원 신청 스키마 추가`

---

### Task 2: 명부 CSV 일괄 등록

**Files:**
- Modify: `apps/api/build.gradle` (`implementation 'org.apache.commons:commons-csv:1.14.1'`)
- Modify: `apps/api/src/main/resources/application.yml` (`spring.servlet.multipart.max-file-size: 1MB`, `max-request-size: 1MB`)
- Modify: `apps/api/src/main/java/com/theclimbingnight/common/ErrorCode.java`, `.claude/skills/api-conventions/SKILL.md`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/service/{RosterCsvParser,RosterCsvRow,RosterCsvResult,RosterImportService}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/dto/RosterImportResponse.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/controller/RosterMemberController.java`
- Create: `deploy/scripts/import-roster.sh` (실행 권한)
- Test: `apps/api/src/test/java/com/theclimbingnight/roster/service/{RosterCsvParserTest,RosterImportServiceTest}.java`, `apps/api/src/test/java/com/theclimbingnight/roster/controller/RosterMemberControllerTest.java`

**Interfaces:**
- Consumes: `RosterMemberRepository.findExistingLowerNicknames`, `RosterMember.create`, `@RequireRole`
- Produces:
  - CSV 형식 (운영진 안내용으로 스크립트 머리 주석에도 적는다): UTF-8 (BOM 있어도 됨), 첫 줄 머리글 `소모임닉네임,메모` (`메모` 열은 없어도 됨), 빈 줄 무시, 값 앞뒤 공백 자름, 데이터 최대 5,000행.
  - 에러 코드:

    | 코드 | 상태 | 메시지 |
    |---|---|---|
    | `ROSTER_CSV_INVALID` | 400 | CSV 첫 줄에 '소모임닉네임' 머리글이 있어야 합니다. |
    | `ROSTER_CSV_ENCODING` | 400 | CSV 파일을 UTF-8로 저장해 다시 올려 주세요. |
    | `ROSTER_CSV_TOO_MANY_ROWS` | 400 | CSV는 한 번에 5,000행까지 올릴 수 있습니다. |

  - `record RosterCsvRow(long line, String somoimNickname, String note)` — `line`은 파일의 줄 번호(머리글이 1), `note`는 빈 값이면 null
  - `record RosterCsvResult(List<RosterCsvRow> rows, List<RosterImportResponse.RowError> errors)`
  - `RosterCsvParser` (`@Component`): `RosterCsvResult parse(InputStream csv)`. 바이트를 `StandardCharsets.UTF_8.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT)`로 읽고 실패하면 `ROSTER_CSV_ENCODING`. 첫 글자가 `﻿`면 버린다. 포맷은 `CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).get()` (1.14.1에서 `build()`는 deprecated). 줄 번호는 레코드를 읽은 직후 `parser.getCurrentLineNumber()`. 행 오류 사유(정확한 문구): `"소모임닉네임이 비어 있습니다."`, `"소모임닉네임은 50자 이하여야 합니다."`, `"메모는 255자 이하여야 합니다."`, `"열 개수가 머리글과 다릅니다."` (`!record.isConsistent()`).
  - `record RosterImportResponse(int addedCount, List<SkippedRow> skipped, List<RowError> errors)`, 중첩 `record SkippedRow(long line, String somoimNickname, String reason)`, `record RowError(long line, String reason)`. 건너뜀 사유: `"이미 명부에 있습니다."`, `"파일 안에서 중복입니다."`
  - `RosterImportService`: `@Transactional RosterImportResponse importCsv(InputStream csv)`. 파일 안에서는 소문자 기준 첫 행만 남기고, 명부에 이미 있으면 건너뛰고, 나머지를 `RosterSource.IMPORT`로 `saveAll`. 오류 행이 있어도 나머지는 등록한다 (같은 파일을 고쳐 다시 올리면 추가분만 들어가므로).
  - 엔드포인트 (ADMIN): `POST /api/v1/roster-members/import`, `multipart/form-data` 파트 `file` → 200 `RosterImportResponse`. 파일이 없거나 1MB 초과면 400 `COMMON_VALIDATION_FAILED` (기존 `GlobalExceptionHandler`가 처리).
  - `deploy/scripts/import-roster.sh <csv경로>`: 환경변수 `API_URL`, `ADMIN_ACCESS_TOKEN` 필수(없으면 사용법 출력 후 종료 코드 1). `curl --fail-with-body -sS -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" -F "file=@$1;type=text/csv" "$API_URL/api/v1/roster-members/import"`. 액세스 토큰은 앱(계획 5) 로그인 후 얻으므로, 그 전에는 이 스크립트를 실제로 돌릴 일이 없다.
- 알려진 한계: 두 운영진이 같은 CSV를 동시에 올리면 중복 항목이 생길 수 있다 (닉네임이 유니크가 아니라 DB로 못 막음). ADMIN 전용·드문 작업이라 받아들인다.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // RosterCsvParserTest
  @Test void 머리글과_행을_읽고_앞뒤_공백을_자른다()        // "소모임닉네임,메모\n 철수 , 2019 가입 \n" → row(2,"철수","2019 가입")
  @Test void BOM이_있어도_머리글을_인식한다()
  @Test void 메모_열이_없어도_된다()                        // note null
  @Test void 따옴표로_감싼_쉼표를_한_칸으로_읽는다()          // "\"철수\",\"메모, 쉼표\"" → note "메모, 쉼표"
  @Test void 빈_줄은_건너뛰고_줄번호는_파일_기준이다()        // 머리글, 빈 줄, "영희" → line 3
  @Test void 머리글에_소모임닉네임이_없으면_ROSTER_CSV_INVALID다()
  @Test void UTF8이_아니면_ROSTER_CSV_ENCODING이다()        // "소모임닉네임\n철수\n".getBytes(Charset.forName("MS949"))
  @Test void 닉네임_메모_길이_경계()                         // 50자 OK, 51자 오류, 빈 값 오류, 메모 255 OK·256 오류 (사유 문구 정확히)
  @Test void 열_개수가_다르면_오류_행이다()                  // "철수,메모,초과"
  @Test void 데이터_5000행까지_받고_5001행이면_ROSTER_CSV_TOO_MANY_ROWS다()
  // RosterImportServiceTest (Mockito)
  @Test void 새_닉네임만_추가하고_기존_닉네임은_건너뛴다()     // 기존 "alpha" → 파일 "Alpha","beta" → added 1, skipped[0].reason "이미 명부에 있습니다."
  @Test void 파일_안의_중복은_첫_행만_추가한다()              // "철수"(2), "철수 "(3) → added 1, skipped line 3 "파일 안에서 중복입니다."
  @Test void 오류_행이_있어도_나머지는_등록한다()
  @Test void 추가한_항목의_출처는_IMPORT다()
  // RosterMemberControllerTest (@WebMvcTest, 인증 설정은 AuthControllerTest의 @Import 목록을 따른다)
  @Test void USER가_올리면_403이다()
  @Test void 파일_파트가_없으면_400이다()
  @Test void ADMIN이_올리면_결과를_돌려준다()               // $.data.addedCount
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.roster.*'` → 컴파일 실패

- [ ] **Step 3: 구현** (의존성·설정·에러 코드·api-conventions 표·스크립트 포함)

- [ ] **Step 4: 통과 확인** 같은 명령 → 전부 통과, `bash -n deploy/scripts/import-roster.sh` 문법 오류 없음

- [ ] **Step 5: 커밋** `Feat(api) : 명부 CSV 일괄 등록 API 추가`

---

### Task 3: 동호회원 인증 신청과 탈퇴 처리

**Files:**
- Modify: `ErrorCode.java`, `.claude/skills/api-conventions/SKILL.md`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/service/{MembershipService,MembershipWithdrawalListener}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/dto/{MembershipApplyRequest,MembershipRequestResponse}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/controller/MembershipRequestController.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/membership/service/{MembershipServiceTest,MembershipWithdrawalListenerTest}.java`, `apps/api/src/test/java/com/theclimbingnight/membership/controller/MembershipRequestControllerTest.java`

**Interfaces:**
- Consumes: Task 1 리포지토리, `UserRepository.findByIdForUpdate`, `UserWithdrawnEvent`, `@CurrentUser AuthenticatedUser`
- Produces:
  - 에러 코드:

    | 코드 | 상태 | 메시지 |
    |---|---|---|
    | `MEMBERSHIP_ALREADY_MEMBER` | 409 | 이미 동호회원입니다. |
    | `MEMBERSHIP_REQUEST_PENDING` | 409 | 이미 심사 중인 신청이 있습니다. |

  - `record MembershipApplyRequest(@NotBlank @Size(max = 50) String somoimNickname)` — 서비스에서 `strip()` 후 저장
  - `record MembershipRequestResponse(long id, String somoimNickname, MembershipRequestStatus status, String rejectReason, Instant createdAt, Instant reviewedAt)`, `static from(MembershipRequest)`
  - `MembershipService`:
    - `@Transactional MembershipRequestResponse apply(long userId, String somoimNickname)` — 사용자 잠금(`findByIdForUpdate`, 없거나 탈퇴면 `AUTH_REQUIRED`) → `role.atLeast(MEMBER)`면 `MEMBERSHIP_ALREADY_MEMBER` → PENDING 있으면 `MEMBERSHIP_REQUEST_PENDING` → 저장. 사용자 행 잠금이 "대기 중 신청은 한 사람당 하나"를 보장한다.
    - `Optional<MembershipRequestResponse> getMine(long userId)` — 가장 최근 신청
  - `MembershipWithdrawalListener`: `@EventListener @Transactional void on(UserWithdrawnEvent event)` — 탈퇴 트랜잭션 안에서 동기 실행. 순서: 그 사용자의 PENDING 신청을 `reject("회원 탈퇴로 신청이 종료되었습니다.", null, now)` → `rosterMemberRepository.unlinkUser(userId)`.
  - 엔드포인트 (인증 필요):

    | 메서드·경로 | 요청 | 응답 |
    |---|---|---|
    | `POST /api/v1/membership-requests` | `{ somoimNickname }` | 201 `MembershipRequestResponse` |
    | `GET /api/v1/membership-requests/me` | | 200 `MembershipRequestResponse`, 신청한 적 없으면 204 |

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // MembershipServiceTest (Mockito, NOW 고정)
  @Test void 신청하면_공백을_자르고_PENDING으로_저장한다()
  @Test void 심사_중_신청이_있으면_409다()
  @Test void 거절된_뒤에는_다시_신청할_수_있다()             // existsByUserIdAndStatus(PENDING)=false → 저장됨
  @Test void MEMBER나_ADMIN이면_409다()                     // MEMBERSHIP_ALREADY_MEMBER, save 호출 없음
  @Test void 신청할_때_사용자_행을_잠근다()                  // verify(userRepository).findByIdForUpdate(7L)
  @Test void 내_최근_신청을_돌려주고_없으면_비어있다()
  // MembershipWithdrawalListenerTest
  @Test void 탈퇴하면_대기_신청을_거절하고_명부_연결을_끊는다()
  // PENDING 신청 → status REJECTED, rejectReason "회원 탈퇴로 신청이 종료되었습니다.", reviewedBy null;
  // InOrder: 신청 처리 다음에 unlinkUser(7)
  // MembershipRequestControllerTest (@WebMvcTest)
  @Test void 비로그인이면_401이다()
  @Test void 닉네임이_공백뿐이거나_51자면_400이다()           // 50자는 201
  @Test void 신청한_적_없으면_204다()
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.membership.*'` → 컴파일 실패

- [ ] **Step 3: 구현**

- [ ] **Step 4: 통과 확인** 같은 명령 → 전부 통과. `./gradlew test` 전체 통과 (`AuthFlowIntegrationTest`의 탈퇴 흐름이 리스너와 함께 돈다)

- [ ] **Step 5: 커밋** `Feat(api) : 동호회원 인증 신청과 탈퇴 처리 추가`

---

### Task 4: 운영진 심사(후보·승인·거절)와 명부 검색

**Files:**
- Modify: `ErrorCode.java`, `.claude/skills/api-conventions/SKILL.md`
- Create: `apps/api/src/main/java/com/theclimbingnight/common/PageResponse.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/service/{CandidateMatcher,MembershipReviewService,MembershipReviewedEvent}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/dto/{MembershipReviewItemResponse,RosterCandidateResponse,MembershipApproveRequest,MembershipRejectRequest}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/roster/dto/RosterMemberResponse.java`
- Modify: `MembershipRequestController.java` (심사 엔드포인트), `RosterMemberController.java` (검색)
- Test: `apps/api/src/test/java/com/theclimbingnight/membership/service/{CandidateMatcherTest,MembershipReviewServiceTest}.java`, `MembershipRequestControllerTest.java`에 추가

**Interfaces:**
- Consumes: Task 1 리포지토리, `MembershipRequestResponse`, `UserResponse`, `Clock`, `ApplicationEventPublisher`
- Produces:
  - 에러 코드:

    | 코드 | 상태 | 메시지 |
    |---|---|---|
    | `MEMBERSHIP_REQUEST_ALREADY_REVIEWED` | 409 | 이미 처리된 신청입니다. |
    | `ROSTER_ALREADY_LINKED` | 409 | 다른 회원과 연결된 명부 항목입니다. |
    | `MEMBERSHIP_LINKED_ELSEWHERE` | 409 | 이 회원은 이미 다른 명부 항목에 연결되어 있습니다. |

  - `record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages)`, `static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper)`
  - `record RosterCandidateResponse(long id, String somoimNickname, String note, RosterSource source)`
  - `record RosterMemberResponse(long id, String somoimNickname, String note, RosterSource source, Long userId)`
  - `record MembershipReviewItemResponse(long id, UserResponse user, String somoimNickname, MembershipRequestStatus status, String rejectReason, Instant createdAt, List<RosterCandidateResponse> candidates, boolean bulkApprovable)`
  - `record MembershipApproveRequest(Long rosterMemberId, boolean createNew)` + `@AssertTrue(message = "rosterMemberId와 createNew 중 하나만 지정해야 합니다.") boolean isExactlyOneChoice()` (`rosterMemberId != null ^ createNew`)
  - `record MembershipRejectRequest(@NotBlank @Size(max = 200) String reason)`
  - `record MembershipReviewedEvent(long userId, MembershipRequestStatus status)` — 계획 4의 푸시가 구독한다
  - `CandidateMatcher` (`@Component`, 읽기 전용):
    - `Map<Long, List<RosterMember>> candidatesOf(Collection<MembershipRequest> requests)` — 키는 신청 ID. 신청자가 이미 명부에 연결돼 있으면(`findAllByUserIdIn`) 그 항목 하나만, 아니면 `findUnlinkedByLowerNicknames`로 닉네임(소문자)이 같은 미연결 항목들. 쿼리는 요청 묶음당 2번.
    - `Set<Long> bulkApprovableIds(Map<Long, List<RosterMember>> candidatesOfAllPending)` — 후보가 정확히 1개이고, 그 명부 ID가 **다른 어떤 대기 신청의 후보 목록에도 없는** 신청 ID
  - `MembershipReviewService`:
    - `PageResponse<MembershipReviewItemResponse> list(MembershipRequestStatus status, int page, int size)` — `id` 오름차순. `bulkApprovable`은 PENDING일 때 전체 대기 신청 기준으로 계산, 그 밖의 상태는 false·후보 빈 목록.
    - `@Transactional MembershipRequestResponse approve(long requestId, long reviewerId, MembershipApproveRequest body)` — 순서: 신청 조회(없으면 `COMMON_NOT_FOUND`) → 사용자 잠금 → 신청 잠금, PENDING 아니면 `MEMBERSHIP_REQUEST_ALREADY_REVIEWED` → 신청자의 기존 연결 `findByUserId` →
      - `createNew`: 기존 연결 있으면 `MEMBERSHIP_LINKED_ELSEWHERE`, 없으면 `RosterMember.create(신청 닉네임, null, APP)` 저장 후 연결
      - `rosterMemberId`: `findByIdForUpdate`(없으면 `COMMON_NOT_FOUND`). 이미 이 신청자에게 연결된 항목이면 그대로, 다른 사용자에게 연결돼 있으면 `ROSTER_ALREADY_LINKED`, 신청자가 다른 항목에 연결돼 있으면 `MEMBERSHIP_LINKED_ELSEWHERE`, 아니면 `linkTo`
      - 이어서 `request.approve(...)`, 사용자 역할이 USER면 `changeRole(MEMBER)` (ADMIN은 내리지 않음), `MembershipReviewedEvent(userId, APPROVED)` 발행
    - `@Transactional MembershipRequestResponse reject(long requestId, long reviewerId, String reason)` — 같은 잠금 순서, PENDING 아니면 409, `reason.strip()` 저장, `MembershipReviewedEvent(userId, REJECTED)` 발행
  - 엔드포인트 (ADMIN):

    | 메서드·경로 | 요청 | 응답 |
    |---|---|---|
    | `GET /api/v1/membership-requests?status=PENDING&page=0&size=20` | `status` 기본 `PENDING` | 200 `PageResponse<MembershipReviewItemResponse>` |
    | `POST /api/v1/membership-requests/{id}/approve` | `{ rosterMemberId }` 또는 `{ createNew: true }` | 200 `MembershipRequestResponse` |
    | `POST /api/v1/membership-requests/{id}/reject` | `{ reason }` | 200 `MembershipRequestResponse` |
    | `GET /api/v1/roster-members?q=&linked=&page=0&size=20` | `q`·`linked` 선택 | 200 `PageResponse<RosterMemberResponse>` (닉네임 오름차순) |

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // CandidateMatcherTest (Mockito)
  @Test void 닉네임이_같은_미연결_항목이_대소문자_무관하게_후보다()
  @Test void 이미_연결된_회원은_연결된_항목만_후보다()
  @Test void 후보가_하나뿐인_신청은_일괄_승인_대상이다()
  @Test void 후보가_없거나_둘_이상이면_일괄_승인_대상이_아니다()
  @Test void 후보가_하나지만_다른_대기_신청과_겹치면_일괄_승인_대상이_아니다()
  // 신청 A "철수", 신청 B "철수 " → 둘 다 후보 [R1] → bulkApprovableIds = {}
  // MembershipReviewServiceTest (Mockito, NOW 고정)
  @Test void 고른_명부_항목에_연결하고_MEMBER가_된다()       // roster.user == user, request APPROVED, reviewedBy·reviewedAt, role MEMBER
  @Test void 새로_만들기로_승인하면_APP_출처_명부를_만든다()
  @Test void 다른_회원에_연결된_명부를_고르면_409다()        // ROSTER_ALREADY_LINKED
  @Test void 이미_처리된_신청이면_409다()                    // APPROVED·REJECTED 둘 다
  @Test void 자격_해제_후_재신청은_기존_명부로_이어진다()     // findByUserId = R1, 고른 항목 R1 → 성공, 새 항목 저장 없음
  @Test void 다른_명부에_연결된_회원에게_다른_항목이나_새로_만들기를_고르면_409다()  // MEMBERSHIP_LINKED_ELSEWHERE
  @Test void 사용자_잠금이_신청_잠금보다_먼저다()             // InOrder: userRepository.findByIdForUpdate → requestRepository.findByIdForUpdate
  @Test void 거절하면_사유를_남기고_REJECTED다()
  @Test void 승인과_거절은_심사_이벤트를_발행한다()
  // MembershipRequestControllerTest에 추가
  @Test void MEMBER가_심사_목록을_보면_403이다()
  @Test void size가_101이면_400이다()
  @Test void 승인_요청에_명부ID와_새로만들기를_둘_다_주거나_둘_다_안_주면_400이다()
  @Test void 거절_사유가_비었거나_201자면_400이다()
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests 'com.theclimbingnight.membership.*'` → 컴파일 실패

- [ ] **Step 3: 구현**

- [ ] **Step 4: 통과 확인** 같은 명령 → 전부 통과

- [ ] **Step 5: 커밋** `Feat(api) : 동호회원 승인·거절과 명부 검색 추가`

---

### Task 5: 일괄 승인

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/service/MembershipBulkApproveService.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/dto/BulkApproveResponse.java`
- Modify: `MembershipRequestController.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/membership/service/MembershipBulkApproveServiceTest.java`

**Interfaces:**
- Consumes: `CandidateMatcher.candidatesOf`·`bulkApprovableIds`, `MembershipReviewService.approve`, `MembershipRequestRepository.findAllByStatusOrderByIdAsc`
- Produces:
  - `record BulkApproveResponse(int approvedCount, int skippedCount)`
  - `MembershipBulkApproveService.BulkApproveResponse approveAll(long reviewerId)` — **클래스에 `@Transactional`을 붙이지 않는다** (예외: 건마다 별도 트랜잭션이어야 하므로. 클래스 주석에 이유를 적는다). 대기 신청 전체와 후보를 읽어 대상 ID를 구하고, `id` 오름차순으로 각 건을 `reviewService.approve(id, reviewerId, new MembershipApproveRequest(그 후보 ID, false))`로 승인한다. `BusinessException`이 나면 그 건은 `skippedCount`로 세고 계속한다 (그사이 다른 운영진이 처리했거나 명부가 연결된 경우).
  - 엔드포인트 (ADMIN): `POST /api/v1/membership-requests/bulk-approve` → 200 `BulkApproveResponse`. 경로 `bulk-approve`는 `{id}`보다 구체적이라 `/{id}/approve`와 충돌하지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  @Test void 대상인_신청만_그_후보로_승인한다()             // 대상 {1,3}, 2는 제외 → approve(1, ..., (R1,false)), approve(3, ..., (R3,false)), approve(2,...) 호출 없음
  @Test void 승인_중_실패한_건은_건너뛰고_계속한다()         // approve(1) → BusinessException(ROSTER_ALREADY_LINKED) → approved 1, skipped 1
  @Test void 대기_신청이_없으면_0건이다()
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*MembershipBulkApproveServiceTest'` → 컴파일 실패

- [ ] **Step 3: 구현**

- [ ] **Step 4: 통과 확인** 같은 명령 → 3개 통과

- [ ] **Step 5: 커밋** `Feat(api) : 동호회원 일괄 승인 추가`

---

### Task 6: 회원 목록·자격 해제와 통합 흐름

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/repository/MemberQueryRepository.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/service/MemberService.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/dto/MemberResponse.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/membership/controller/MemberController.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/membership/service/MemberServiceTest.java`, `apps/api/src/test/java/com/theclimbingnight/membership/repository/MemberQueryRepositoryTest.java`, `apps/api/src/test/java/com/theclimbingnight/MembershipFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByIdForUpdate`, `User.changeRole`, `PageResponse`, `JwtTokenService.issueAccessToken(long)`
- Produces:
  - `record MemberResponse(long userId, String nickname, Role role, Long rosterMemberId, String somoimNickname)`
  - `MemberQueryRepository extends Repository<User, Long>`: `Page<MemberResponse> search(String q, Pageable pageable)` — JPQL `select new ...MemberResponse(u.id, u.nickname, u.role, r.id, r.somoimNickname) from User u left join RosterMember r on r.user = u where u.status = ACTIVE and u.role in (MEMBER, ADMIN) and (:q is null or lower(u.nickname) like ... or lower(r.somoimNickname) like ...)`, `countQuery` 별도, 정렬 `u.nickname` 오름차순
  - `MemberService`: `PageResponse<MemberResponse> list(String q, int page, int size)`, `@Transactional void revoke(long userId)` — 사용자 잠금, ACTIVE이고 역할이 MEMBER가 아니면 `COMMON_NOT_FOUND` (ADMIN은 이 API로 내리지 않는다), `changeRole(USER)`. 명부 연결은 그대로 둔다 (스펙: 재승인 시 이어짐).
  - 엔드포인트 (ADMIN):

    | 메서드·경로 | 요청 | 응답 |
    |---|---|---|
    | `GET /api/v1/members?q=&page=0&size=20` | | 200 `PageResponse<MemberResponse>` |
    | `DELETE /api/v1/members/{userId}` | | 204 |

- [ ] **Step 1: 실패하는 테스트 작성**
  ```java
  // MemberQueryRepositoryTest (@DataJpaTest)
  @Test void MEMBER와_ADMIN만_명부_닉네임과_함께_나온다()   // USER·탈퇴자 제외, 명부 없는 ADMIN은 rosterMemberId null
  @Test void 앱_닉네임이나_소모임_닉네임으로_검색한다()
  // MemberServiceTest (Mockito)
  @Test void 자격을_해제하면_USER가_되고_명부_연결은_남는다()  // rosterMemberRepository와 상호작용 없음
  @Test void MEMBER가_아니면_404다()                         // USER, ADMIN, 탈퇴자
  // MembershipFlowIntegrationTest (@SpringBootTest + @AutoConfigureMockMvc)
  // ADMIN은 User.create → changeRole(ADMIN) 저장, 일반 사용자도 리포지토리로 만들고, 토큰은 JwtTokenService.issueAccessToken
  @Test void 명부_등록부터_승인_해제_재승인_탈퇴까지()
  // CSV "소모임닉네임,메모\n철수,\n영희,\n" 업로드 → added 2 → 같은 파일 재업로드 → added 0, skipped 2
  // A "철수", B "영희 ", C "없는사람" 신청 → 목록 3건, A·B bulkApprovable true, C candidates [] false
  // bulk-approve → approved 2, skipped 0 → A·B GET /users/me role MEMBER, C 여전히 PENDING
  // C를 { createNew: true }로 승인 → C MEMBER, 명부에 source APP "없는사람" 생김
  // DELETE /members/{A} → A USER → A가 "철수" 재신청 → 후보 = A에 연결된 그 항목 → 승인 → 같은 rosterMemberId
  // A 탈퇴(DELETE /users/me) → 그 명부 항목은 남고 userId null (GET /roster-members?q=철수)
  @Test void 같은_닉네임_두_신청은_일괄_승인에서_빠진다()
  // 명부 "철수" 하나, A·D 둘 다 "철수" 신청 → bulk-approve → approved 0, 둘 다 PENDING
  ```

- [ ] **Step 2: 실패 확인** Run: `cd apps/api && ./gradlew test --tests '*MemberQueryRepositoryTest' --tests '*MemberServiceTest' --tests '*MembershipFlowIntegrationTest'` → 컴파일 실패

- [ ] **Step 3: 구현**

- [ ] **Step 4: 전체 테스트 통과 확인** Run: `cd apps/api && ./gradlew test` → BUILD SUCCESSFUL

- [ ] **Step 5: 커밋** `Feat(api) : 회원 목록과 자격 해제 추가`

---

## 이 계획이 끝나면 되는 것

- 운영진이 기존 동호회원 명부를 CSV로 올리고, 신청을 개별·일괄 승인해 MEMBER를 만든다. 계획 3(벙)은 `RosterMemberRepository.findByUserId`로 신청자의 명부 항목을 찾아 신청·참석 기록을 붙인다.
- 계획 4의 푸시 알림은 `MembershipReviewedEvent`를 구독해 승인·거절을 알린다.
- 아직 없는 것: ADMIN 지정은 여전히 DB에서 직접 한다 (관리자 웹은 3단계). 앱의 "동호회 가입하기" 링크(`app_settings.somoim_join_url`)는 계획 4.
