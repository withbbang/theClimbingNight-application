# 2단계-01 사진 피드·신고·차단·워커 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 사진 1~10장으로 게시물을 올리고, 동호회 전체 피드에서 최신순으로 본다. 비로그인은 최신 3개만 본다. 신고·차단·운영진 숨김이 있어 이 상태로 출시할 수 있다.

**Architecture:** 앱이 API에서 presigned PUT URL을 받아 R2 비공개 버킷에 사진을 올리고 게시를 알린다. API는 `media_jobs`에 작업을 넣는다. 같은 `apps/api` 코드를 `worker` 설정으로 띄운 별도 컨테이너가 작업을 하나씩 가져온다. 원본은 NAS에 보관하고, ffmpeg로 WebP를 만들어 R2 공개 버킷에 올린다. 숨기면 공개 사본을 비공개 버킷의 격리 영역으로 옮기고 Cloudflare 캐시를 비운다. 복원하면 새 키로 되돌린다.

**Tech Stack:** Spring Boot 4.1.2 / Java 17, AWS SDK for Java v2 `s3`(R2 S3 호환, presigner 포함), ffmpeg(워커 이미지의 apt 패키지), MySQL 8.4 / H2(테스트), Expo SDK 58(`expo-image`, `expo-image-picker`, `expo-image-manipulator` — `pnpm expo install`), FlashList 2.3.2, React Query 5.

**Spec:** `docs/superpowers/specs/2026-11-26-phase2-feed-design.md` (11장 1번). 정본 설계 `docs/architecture.md`. 규약은 `.claude/rules/{api,mobile,testing}.md`, `api-conventions` 스킬.

**이 계획에 없는 것:** 댓글·좋아요·프로필(2번), 영상(3번), 이미지 편집(4번). 도메인 구매와 R2·Cloudflare 콘솔 설정(사용자가 할 일, README 8장에 적기만 함). `[확인 필요]` 문안 채우기.

## 작업 방식

- 브랜치 `feat/feed-photo`(이미 있음, 설계 문서 커밋됨). 첫 커밋은 이 문서(`Docs : 사진 피드 구현 계획 추가`). 커밋 전 `.gitmessage.txt`를 읽고, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 코드 태스크: 실패하는 테스트 → 실패 확인 → 구현 → 바뀐 앱의 lint·test(웹은 build도) → 커밋.
- API 테스트는 `./gradlew test`(context-mode가 켜져 있으면 `ctx_execute`). 결과는 `build/test-results/test/*.xml`로 센다.
- `.env.example`은 그 파일만 다루는 명령으로 고친다.
- 리뷰 강도는 `high`를 제안한다. 새 컨테이너·외부 저장소·운영진 조치 경로가 함께 들어가고, 숨김 경합 같은 버그는 출시 뒤 개인정보 사고가 되기 때문이다. 실행 전 승인받는다.

## 설계 문서와 달라지는 점 (구현 중 정함)

- **숨김은 삭제 대신 격리:** 설계 문서는 숨기면 R2 공개본을 지우고, 복원할 때 NAS 원본으로 다시 변환한다고 했다. 이 계획은 공개본을 비공개 버킷의 `quarantine/`으로 옮긴다(복사 후 삭제). 운영진은 신고 목록에서 presigned GET(5분)으로 그 사진을 보고 판단할 수 있다. 복원하면 새 키로 공개 버킷에 다시 복사한다. 공개 URL이 즉시 막히고 예전 URL이 되살아나지 않는다는 결과는 같다. 공개본을 지우면 운영진이 신고된 사진을 볼 방법이 없어서 바꿨다.
- **작업 가져오기:** `SELECT ... FOR UPDATE SKIP LOCKED` 대신 조건부 UPDATE(`WHERE id=? AND status='QUEUED'`)로 가져온다. H2 테스트와 MySQL에서 똑같이 동작하고, 워커 동시성이 1이라 충분하다.
- **작업 종류:** `PROCESS`, `QUARANTINE`, `RELEASE`, `DELETE_MEDIA`, `PURGE_ORIGINAL`. 우선순위는 `QUARANTINE`·`DELETE_MEDIA`(0) > `RELEASE`(1) > `PROCESS`(2) > `PURGE_ORIGINAL`(3).
- **비공개 버킷 구성:** `R2_MEDIA_INCOMING_BUCKET` 안에 `uploads/`(수명 주기 1일)와 `quarantine/`(수명 주기 없음)을 둔다.

## Global Constraints

- 새 환경변수(API·워커 공통): `R2_ACCOUNT_ID`(기존 키 재사용), `R2_MEDIA_ACCESS_KEY_ID`, `R2_MEDIA_SECRET_ACCESS_KEY`, `R2_MEDIA_INCOMING_BUCKET`, `R2_MEDIA_PUBLIC_BUCKET`, `MEDIA_PUBLIC_BASE_URL`. 워커만: `MEDIA_ORIGINALS_DIR`(컨테이너 안 경로, 기본 `/media/originals`), `CLOUDFLARE_ZONE_ID`, `CLOUDFLARE_CACHE_PURGE_TOKEN`. `application.yml`·`apps/api/.env.example`·`deploy/.env.example`·`deploy/compose.yml`·`deploy/README.md` 표를 함께 고친다.
- 미디어 키가 비면 `app.media.enabled=false`. 그러면 게시물 작성은 `MEDIA_UNAVAILABLE`(503)로 거절하고, 조회는 그대로 된다(push의 `NoopPushGateway`와 같은 방식).
- 사진 업로드 규칙: `image/jpeg`만, 1~15,728,640바이트(15MB), 한 게시물 1~10장, 본문 0~2,200자, presigned PUT 유효 15분.
- 변환 결과: WebP 긴 변 1080px(`display`)와 480px(`thumb`). 원본이 더 작으면 키우지 않는다. 메타데이터는 모두 지운다(`-map_metadata -1`). ffmpeg 타임아웃 30초, `-threads 2`.
- 키: 업로드 `uploads/{uuid}.jpg`, 원본 `{MEDIA_ORIGINALS_DIR}/{yyyy}/{MM}/{postMediaId}.jpg`(DB에는 `{yyyy}/{MM}/{id}.jpg` 상대 경로), 공개 `p/{uuid}/d.webp`·`p/{uuid}/t.webp`, 격리 `quarantine/{같은 공개 키}`. uuid는 `SecureRandom` 빈으로 만든다.
- 응답의 사진 URL은 `MEDIA_PUBLIC_BASE_URL` + `/` + 키로 만든다. DB에는 키만 둔다.
- 재시도: 실패하면 1분 → 5분 → 30분 뒤에 다시 하고, 3번째 실패에서 `FAILED`. `RUNNING`으로 10분 넘게 있는 작업은 다시 `QUEUED`로 돌린다(attempts는 늘린다).
- 워커 컨테이너: `mem_limit: 512m`, `JAVA_TOOL_OPTIONS: -Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`, `SPRING_PROFILES_ACTIVE=prod,worker`. 워커는 웹 서버를 띄우지 않고(`spring.main.web-application-type=none`), Flyway를 돌리지 않으며(`spring.flyway.enabled=false`, api가 healthy가 된 뒤 뜸), 기존 `@Scheduled` 작업도 돌리지 않는다(`app.scheduling.enabled=false`). 작업 루프는 `@Scheduled`가 아니라 자체 스레드 하나로 돈다.
- API 컨테이너는 워커 루프를 돌리지 않는다(`app.worker.enabled` 기본 `false`).
- 에러 코드(`ErrorCode`와 `api-conventions` 표에 함께 추가):
  - `MEDIA_UNAVAILABLE` 503 "사진 올리기를 잠시 쓸 수 없습니다. 잠시 후 다시 시도해 주세요."
  - `POST_UPLOAD_INCOMPLETE` 409 "아직 올라가지 않은 사진이 있습니다. 다시 올려 주세요."
  - `POST_NOT_DRAFT` 409 "이미 게시한 글입니다."
  - `POST_NOT_EDITABLE` 409 "숨겨지거나 삭제된 글은 고칠 수 없습니다."
  - `BLOCK_SELF` 400 "자기 자신은 차단할 수 없습니다."
  - `REPORT_SELF` 400 "내 글은 신고할 수 없습니다."
  - `REPORT_ALREADY_SUBMITTED` 409 "이미 신고한 항목입니다."
- 남의 글·차단 관계의 글·없는 글은 모두 `COMMON_NOT_FOUND`(404). 존재 여부를 흘리지 않는다.
- 비밀값(R2 키, Cloudflare 토큰, presigned URL 전체)은 로그에 남기지 않는다.

## Review Focus

1. **사진 처리 중에 게시물이 숨겨지거나 삭제된다** → 처리가 끝나도 공개 버킷에 사본이 남지 않고 게시물은 피드에 나오지 않는다. 워커는 업로드 전과 후에 게시물 상태를 다시 읽고, `PENDING`이 아니면 방금 올린 공개 사본을 지운다. 숨김·삭제는 아직 `QUEUED`인 `PROCESS` 작업을 취소한다. Task 7.
2. **워커 컨테이너가 API의 예약 작업을 같이 돌린다** → 벙 리마인드 푸시가 두 번 가지 않는다. 워커 설정에서 기존 `@Scheduled` 빈이 하나도 등록되지 않는다. Task 7.
3. **비로그인 사용자가 4번째 게시물 ID로 상세를 부르거나 커서를 만들어 보낸다** → 401 `AUTH_REQUIRED`. 차단 관계의 글을 ID로 부르면 404. Task 4.
4. **같은 게시물에 게시 요청이 두 번 동시에 온다** → 하나만 성공하고 작업이 사진 수만큼만 생긴다. 게시물 행을 잠근 뒤 상태를 본다. Task 3.
5. **신고가 연달아 들어온다** → 같은 사람의 두 번째 신고는 409, 운영진 푸시는 대상마다 처음 한 번만 간다. 자동 숨김 판정은 서로 다른 신고자 수로 하고, 이미 숨긴 글을 다시 숨기지 않는다. Task 5.

---

### Task 1: API — R2 미디어 저장소

**Files:**
- Modify: `apps/api/build.gradle` (AWS SDK v2 BOM과 `software.amazon.awssdk:s3`. 버전은 Gradle 캐시·Maven Central에서 최신 2.x를 확인해 고정)
- Create: `apps/api/src/main/java/com/theclimbingnight/media/MediaProperties.java`, `MediaConfig.java`, `storage/MediaStorage.java`, `storage/R2MediaStorage.java`, `storage/DisabledMediaStorage.java`, `storage/PresignedUpload.java`, `MediaUrls.java`
- Modify: `application.yml`, `apps/api/.env.example`, `common/ErrorCode.java`(`MEDIA_UNAVAILABLE`), `.claude/skills/api-conventions/SKILL.md` 에러 표
- Test: `apps/api/src/test/java/com/theclimbingnight/media/storage/R2MediaStorageTest.java`, `media/MediaUrlsTest.java`

**Interfaces:**
- Produces:
  - `record MediaProperties(boolean enabled, String accountId, String accessKeyId, String secretAccessKey, String incomingBucket, String publicBucket, String publicBaseUrl, Path originalsDir)` — `app.media.*`. `enabled`는 키 다섯 개가 모두 있을 때만 true가 되게 `MediaConfig`에서 판정.
  - `interface MediaStorage`:
    - `PresignedUpload presignUpload(String key, String contentType, long byteSize, Duration ttl)`
    - `OptionalLong uploadedSize(String key)` (비공개 버킷 HEAD, 없으면 empty)
    - `InputStream openUpload(String key)`
    - `void deleteUpload(String key)`
    - `void putPublic(String key, Path file, String contentType)` (`Cache-Control: public, max-age=31536000, immutable`)
    - `void deletePublic(String key)`
    - `void copyPublicToQuarantine(String publicKey)` / `void copyQuarantineToPublic(String quarantineKey, String newPublicKey)` / `void deleteQuarantine(String publicKey)`
    - `URI presignQuarantineGet(String publicKey, Duration ttl)`
  - `record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt)`
  - `MediaUrls.publicUrl(String key): String`
  - `DisabledMediaStorage`는 모든 메서드에서 `BusinessException(MEDIA_UNAVAILABLE)`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `R2MediaStorageTest.presign_URL은_비공개_버킷과_키_만료_서명_길이를_담는다()` — 고정 자격증명, endpoint `https://acc.r2.cloudflarestorage.com`, 키 `uploads/abc.jpg`, ttl 15분. `assertThat(url).contains("/media-in/uploads/abc.jpg").contains("X-Amz-Expires=900")`, `headers`에 `Content-Type=image/jpeg`, `Content-Length` 서명 포함(`X-Amz-SignedHeaders`에 `content-length`). presigner는 네트워크를 쓰지 않는다.
  - `MediaUrlsTest.기본_주소_끝의_슬래시와_무관하게_한_번만_붙는다()` — `https://media.x/` + `p/u/d.webp` → `https://media.x/p/u/d.webp`.
  - `MediaConfig` 조건: 키 하나라도 비면 `DisabledMediaStorage` 빈 (`ApplicationContextRunner`).
- [ ] **Step 2: 실패 확인** — `./gradlew test`. Expected: 컴파일 실패.
- [ ] **Step 3: 구현.** R2 endpoint `https://{accountId}.r2.cloudflarestorage.com`, region `auto`, path-style. S3 클라이언트는 `UrlConnectionHttpClient`나 기본 Apache 중 의존성이 가벼운 쪽(타임아웃 연결 5초·읽기 60초). 업로드·다운로드는 스트림/파일로만(`byte[]` 금지).
- [ ] **Step 4: 통과 확인** — `./gradlew test`. Expected: 실패 0.
- [ ] **Step 5: 커밋** `Feat(api) : R2 미디어 저장소 추가`

---

### Task 2: API — 피드 스키마와 조회 쿼리

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V8__feed.sql`
- Create: `apps/api/src/main/java/com/theclimbingnight/feed/entity/{Post,PostStatus,PostMedia,PostMediaStatus,MediaType}.java`, `feed/repository/{PostRepository,PostMediaRepository}.java`, `media/job/{MediaJob,MediaJobKind,MediaJobStatus,MediaJobRepository}.java`, `moderation/entity/{Report,ReportTargetType,ReportReason,ReportStatus,UserBlock}.java`, `moderation/repository/{ReportRepository,UserBlockRepository}.java`
- Test: `feed/repository/PostRepositoryTest.java`, `media/job/MediaJobRepositoryTest.java`

**Interfaces:**
- Produces (설계 문서 9장 컬럼 그대로, 영상·댓글·좋아요·프로필 컬럼은 이번에 만들지 않는다. `like_count`·`comment_count`는 0 기본값으로 지금 만든다):
  - `PostStatus { DRAFT, PENDING, VISIBLE, FAILED, HIDDEN_PENDING, HIDDEN }`, `Post`: `author_id`, `body`, `status`, `published_at`(nullable), `deleted_at`(nullable). 메서드 `publish(String body)`, `markVisible(Instant now)`(이미 `published_at`이 있으면 유지), `markFailed()`, `hide(boolean pending)`, `restore()`(→ `PENDING`), `editBody(String)`, `softDelete(Instant)`.
  - `PostMedia`: `post_id`, `type`, `status`, `sort_order`, `upload_key`, `original_path`, `public_key`(공개 사본 폴더 `p/{uuid}`), `width`, `height`, `byte_size`. 표시 키는 `public_key + "/d.webp"`, `"/t.webp"`.
  - `MediaJob`: `kind`, `priority`, `target_id`(post_media id), `status { QUEUED, RUNNING, DONE, FAILED, CANCELED }`, `attempts`, `next_run_at`, `locked_at`, `last_error`(500자로 자름). 인덱스 `(status, priority, next_run_at)`.
  - `Report`: UNIQUE `(reporter_id, target_type, target_id)`. `UserBlock`: PK `(blocker_id, blocked_id)`.
  - `PostRepository.findFeed(Long viewerId, Instant cursorPublishedAt, Long cursorId, Pageable limit): List<Post>` — `VISIBLE`, `deleted_at IS NULL`, `published_at`·`id` 내림차순, 커서보다 뒤, `viewerId`가 null이 아니면 양방향 차단 제외(`NOT EXISTS`).
  - `PostRepository.findLatestVisibleIds(int n): List<Long>` (비로그인 판정용, 차단 무관)
  - `PostRepository.findByIdForUpdate(long id)` (`PESSIMISTIC_WRITE`)
  - `MediaJobRepository.findClaimable(Instant now, Pageable one): List<Long>` (`QUEUED`, `next_run_at <= now`, priority·id 오름차순) / `int claim(long id, Instant now)` (`@Modifying` 조건부 UPDATE, 성공 1) / `int requeueStale(Instant lockedBefore)` / `int cancelQueued(Collection<Long> mediaIds, MediaJobKind kind)`
  - 인덱스 `posts(status, published_at, id)`, `post_media(post_id, sort_order)`, `user_blocks(blocked_id)`, `reports(target_type, target_id)`.

- [ ] **Step 1: 실패하는 테스트 작성** (`@DataJpaTest`)
  - `피드는_보이는_글만_게시_시각_최신순으로_준다()` — VISIBLE 3개(published_at 서로 다름) + PENDING·HIDDEN·삭제된 VISIBLE 각 1개 → 3개, 순서 확인.
  - `같은_게시_시각이면_id_내림차순이고_커서_다음부터_준다()`
  - `내가_차단한_사람과_나를_차단한_사람의_글은_빠진다()`
  - `비로그인_판정용_최신_3개는_차단과_무관하다()`
  - `조건부_claim은_한_번만_성공한다()` — 같은 id에 `claim` 두 번 → 1, 0.
  - `10분_넘게_RUNNING인_작업만_다시_QUEUED가_된다()` — 경계: 정확히 10분 전 잠금은 그대로(`lockedBefore` 미만만).
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.** 마이그레이션은 V1 스타일(utf8mb4, `DATETIME(6)`, FK 이름 `fk_<table>_<col>`).
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 커밋** `Feat(api) : 피드 스키마와 조회 쿼리 추가`

---

### Task 3: API — 게시물 작성·수정·삭제

**Files:**
- Create: `feed/dto/{PostCreateRequest,PostMediaSpec,PostDraftResponse,PostUploadResponse,PostPublishRequest,PostUpdateRequest}.java`, `feed/service/{PostCommandService,DraftPostCleanupJob}.java`, `feed/controller/PostController.java`, `media/job/MediaJobScheduler.java`
- Modify: `common/ErrorCode.java`, `api-conventions` 표
- Test: `feed/service/PostCommandServiceTest.java`, `feed/controller/PostControllerTest.java`, `feed/service/DraftPostCleanupJobTest.java`

**Interfaces:**
- Consumes: Task 1 `MediaStorage`, Task 2 엔티티·리포지토리.
- Produces:
  - `POST /api/v1/posts` (USER 이상) `PostCreateRequest(List<@Valid PostMediaSpec> media)` — `@Size(min=1,max=10)`, `PostMediaSpec(String contentType, long byteSize, int width, int height)`. → 201 `PostDraftResponse(long postId, List<PostUploadResponse> uploads)`, `PostUploadResponse(long mediaId, String url, Map<String,String> headers, OffsetDateTime expiresAt)`.
  - `POST /api/v1/posts/{id}/publish` `PostPublishRequest(@Size(max=2200) String body)` → 200 `PostResponse`(Task 4에서 정의, 상태 `PENDING`). 작성자만. 게시물 행을 잠근 뒤 `DRAFT`가 아니면 `POST_NOT_DRAFT`, 사진 하나라도 `uploadedSize`가 비거나 선언 크기와 다르면 `POST_UPLOAD_INCOMPLETE`. 통과하면 사진마다 `PROCESS` 작업.
  - `PATCH /api/v1/posts/{id}` `PostUpdateRequest(@Size(max=2200) String body)` → 200. 작성자만, `HIDDEN_PENDING`·`HIDDEN`이면 `POST_NOT_EDITABLE`.
  - `DELETE /api/v1/posts/{id}` → 204. 작성자 또는 ADMIN. 소프트 삭제 + `MediaJobScheduler.onPostRemoved(post)`.
  - `MediaJobScheduler`: `enqueueProcess(List<PostMedia>)`, `onPostRemoved(Post)`(QUEUED `PROCESS` 취소, 사진마다 `DELETE_MEDIA` 즉시와 `PURGE_ORIGINAL` 30일 뒤), `onPostHidden(Post)`(QUEUED `PROCESS` 취소, 사진마다 `QUARANTINE`), `onPostRestored(Post)`(사진마다 `RELEASE`). 모두 `Clock` 주입.
  - `DraftPostCleanupJob`: `@Scheduled(cron = "0 15 * * * *", zone = "Asia/Seoul")`, 만든 지 24시간 넘은 `DRAFT`와 그 사진 행을 지운다(업로드 객체는 버킷 수명 주기가 지움).
- 같은 사람의 게시물이라도 `deleted_at`이 있으면 404.

- [ ] **Step 1: 실패하는 테스트 작성**
  - 서비스(Mockito): `사진_10장은_초안이_되고_11장은_검증_실패다()`는 컨트롤러 테스트로(`COMMON_VALIDATION_FAILED`, field `media`). `jpeg가_아니거나_15MB를_넘으면_검증_실패다()` — 15,728,640은 통과, 15,728,641은 실패, 0은 실패.
  - `초안이_아니면_게시는_POST_NOT_DRAFT다()`, `올라가지_않은_사진이_있으면_POST_UPLOAD_INCOMPLETE다()`, `선언과_크기가_다르면_POST_UPLOAD_INCOMPLETE다()`
  - `게시하면_사진마다_PROCESS_작업이_생기고_상태는_PENDING이다()`
  - `남의_게시물_게시·수정은_404다()`, `ADMIN은_남의_게시물을_지울_수_있다()`
  - `삭제하면_대기중_PROCESS를_취소하고_DELETE_MEDIA와_30일_뒤_PURGE_ORIGINAL을_만든다()` — `next_run_at` = 고정 시계 + 30일.
  - `미디어가_꺼져_있으면_초안_만들기는_MEDIA_UNAVAILABLE이다()`
  - `DraftPostCleanupJobTest.24시간_넘은_초안만_지운다()` — 경계: 정확히 24시간 된 초안은 남긴다.
  - Review Focus 4: `PostPublishConcurrencyTest`(`@SpringBootTest`, H2) `같은_게시물_게시가_동시에_와도_작업은_사진_수만큼이다()` — 두 스레드 `CountDownLatch`, `R2MediaStorage`는 `@MockitoBean`으로 크기 응답.
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.** 업로드 키 uuid는 `SecureRandom` 빈에서 128비트를 뽑아 hex로.
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 커밋** `Feat(api) : 게시물 작성과 수정, 삭제 추가`

---

### Task 4: API — 피드·상세 조회

**Files:**
- Create: `common/CursorResponse.java`, `feed/Cursor.java`, `feed/dto/{PostResponse,PostAuthorResponse,PostMediaResponse}.java`, `feed/service/PostQueryService.java`
- Modify: `feed/controller/PostController.java`
- Test: `feed/CursorTest.java`, `feed/service/PostQueryServiceTest.java`, `feed/controller/PostQueryControllerTest.java`

**Interfaces:**
- Produces:
  - `record CursorResponse<T>(List<T> items, String nextCursor)` (`api-conventions` 커서 페이징 형식).
  - `Cursor.encode(Instant publishedAt, long id): String` / `Cursor.decode(String): Optional<Cursor>` — base64url(`{epochMicros}:{id}`), 깨진 값은 `COMMON_VALIDATION_FAILED`(field `cursor`).
  - `PostResponse(long id, PostAuthorResponse author, String body, List<PostMediaResponse> media, int likeCount, int commentCount, OffsetDateTime publishedAt, String status, boolean mine)` — `status`는 작성자 본인에게만 값, 남에게는 null. `PostAuthorResponse(long id, String nickname)`(탈퇴자는 Task 6 이후 글이 없으므로 고려 안 함). `PostMediaResponse(long id, String type, String url, String thumbnailUrl, int width, int height)` — `READY`가 아니거나 숨김 상태면 `url`·`thumbnailUrl`이 null.
  - `GET /api/v1/posts?cursor=&size=` `@PublicApi`, size 1~30 기본 10. 비로그인: cursor가 있으면 401 `AUTH_REQUIRED`, 없으면 `findLatestVisibleIds(3)`만, `nextCursor` null.
  - `GET /api/v1/posts/{id}` `@PublicApi`. 비로그인: 그 id가 최신 3개에 없으면 401. 로그인: 차단 관계면 404. 작성자는 자기 글을 모든 상태(삭제 제외)로 볼 수 있고, 숨김이면 사진 URL은 null(앱이 "운영 정책에 따라 숨겨졌어요"를 보여줌). 남은 `VISIBLE`만.
  - `GET /api/v1/posts/mine/in-progress` (USER 이상) — 내 `PENDING`·`FAILED` 게시물 최신순 최대 10개, `List<PostResponse>`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `CursorTest.인코딩한_커서는_그대로_돌아온다()`, `깨진_커서는_검증_실패다()`
  - 컨트롤러: `비로그인_피드는_최신_3개만_주고_nextCursor가_없다()`, `비로그인이_cursor를_보내면_401이다()`, `비로그인이_4번째_글_상세를_부르면_401이다()`, `로그인하면_size만큼_주고_다음_커서를_준다()`, `size_31은_검증_실패다()`
  - 서비스: `차단_관계의_글_상세는_404다()`, `남의_PENDING_글_상세는_404다()`, `내_숨겨진_글은_사진_URL_없이_상태와_함께_보인다()`, `사진_URL은_공개_기본_주소와_키로_만든다()`
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.** 피드 한 페이지의 사진·작성자는 `IN` 조회 두 번으로 모은다(N+1 금지).
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 커밋** `Feat(api) : 피드와 게시물 상세 조회 추가`

---

### Task 5: API — 차단·신고·운영진 조치

**Files:**
- Create: `moderation/dto/{BlockCreateRequest,BlockedUserResponse,ReportCreateRequest,ReportTargetResponse,ReportTargetActionRequest}.java`, `moderation/service/{BlockService,ReportService,ModerationService,ReportSubmittedEvent}.java`, `moderation/controller/{BlockController,ReportController}.java`
- Modify: `push/service/PushEventListener.java`, `push/service/PushNotifier.java`, `push/repository/PushTokenRepository.java`(ADMIN 토큰 조회), `common/ErrorCode.java`, `api-conventions` 표
- Test: `moderation/service/{BlockServiceTest,ReportServiceTest,ModerationServiceTest}.java`, `moderation/controller/{BlockControllerTest,ReportControllerTest}.java`, `push/service/PushNotifierTest.java`(추가)

**Interfaces:**
- Consumes: Task 3 `MediaJobScheduler.onPostHidden/onPostRestored`, Task 1 `MediaStorage.presignQuarantineGet`.
- Produces:
  - `POST /api/v1/blocks` `BlockCreateRequest(long userId)` → 201(이미 차단이면 그대로 201). 자기 자신이면 `BLOCK_SELF`, 없는·탈퇴한 사용자면 404. `DELETE /api/v1/blocks/{userId}` → 204. `GET /api/v1/blocks` → `List<BlockedUserResponse(long userId, String nickname, OffsetDateTime blockedAt)>`.
  - `POST /api/v1/reports` `ReportCreateRequest(ReportTargetType targetType, long targetId, ReportReason reason, @Size(max=500) String detail)` → 201. `ReportReason { SPAM, SEXUAL, ABUSE, PORTRAIT_PRIVACY, OTHER }`, `OTHER`면 `detail` 필수(공백 불가). 이번 단계의 `targetType`은 `POST`·`USER`만 받는다(`COMMENT`는 400). 내 글·나 자신이면 `REPORT_SELF`. 중복이면 `REPORT_ALREADY_SUBMITTED`(유니크 위반을 잡아 변환).
  - 자동 숨김(POST만): 대상이 `VISIBLE`·`PENDING`일 때, `PORTRAIT_PRIVACY` 신고 1건이면, 아니면 서로 다른 신고자 3명이면 `hide(pending=true)` + `onPostHidden`.
  - `ReportSubmittedEvent(ReportTargetType, long targetId)` — 대상의 첫 `OPEN` 신고일 때만 발행. `PushNotifier.reportSubmitted` → ADMIN 전원(신고자 제외), 제목 "새 신고", 본문 "검토할 신고가 들어왔어요.", data `{type: "REPORT_SUBMITTED"}`.
  - `GET /api/v1/report-targets?page=&size=` (ADMIN) → `PageResponse<ReportTargetResponse>`. `ReportTargetResponse(String targetType, long targetId, String targetStatus, int reportCount, Map<String,Integer> reasons, List<String> details, OffsetDateTime lastReportedAt, PostResponse post /*POST일 때*/, List<String> quarantineUrls /*숨김 중이면 presigned GET 5분*/, String userNickname /*USER일 때*/)`. `OPEN` 신고가 있는 대상만, 최근 신고순.
  - `POST /api/v1/report-targets/{targetType}/{targetId}/actions` (ADMIN) `ReportTargetActionRequest(Action action)`, `Action { HIDE, RESTORE, DISMISS }`. HIDE: 글을 `HIDDEN`(이미 `HIDDEN_PENDING`이면 상태만, 격리 작업은 다시 만들지 않음), 신고 `ACTIONED`. RESTORE: 숨김 글 → `restore()` + `onPostRestored`, 신고 `DISMISSED`. DISMISS: 신고만 `DISMISSED`(USER 대상은 이것만 가능, 나머지는 400). 신고가 없는 글도 HIDE·RESTORE할 수 있다(앱의 ⋯ 메뉴에서 운영진이 직접 숨김).
  - 차단 필터는 Task 2 쿼리가 이미 한다. 운영진 신고 목록은 차단과 무관하다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `초상권_신고는_1건으로_자동_숨김되고_격리_작업이_생긴다()`
  - `다른_사유는_서로_다른_3명째에_숨김된다()` — 같은 사람이 사유를 바꿔 두 번 신고하는 경우는 409이므로, 2명까지는 그대로, 3명째에 숨김.
  - `이미_숨긴_글에_신고가_더_와도_격리_작업을_다시_만들지_않는다()`
  - `같은_대상_두번째_신고는_409다()` (리포지토리 유니크 위반 → `REPORT_ALREADY_SUBMITTED`)
  - `운영진_푸시는_대상의_첫_신고에만_발행된다()`
  - `OTHER인데_detail이_비면_검증_실패다()`, `COMMENT_대상은_400이다()`
  - `복원하면_PENDING이_되고_사진마다_RELEASE_작업이_생긴다()`, `USER_대상에_HIDE는_400이다()`
  - `자기_자신_차단은_BLOCK_SELF다()`, `차단은_두_번_해도_201이다()`
  - `ADMIN이_아니면_신고_목록은_403이다()`
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.** 신고와 자동 숨김 판정은 게시물 행을 잠근 트랜잭션 안에서 한다(동시 신고 대비).
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 커밋** `Feat(api) : 차단과 신고, 운영진 숨김 추가`

---

### Task 6: API — 탈퇴 연동과 흐름 통합 테스트

**Files:**
- Create: `feed/service/FeedWithdrawalListener.java`
- Test: `apps/api/src/test/java/com/theclimbingnight/FeedFlowIntegrationTest.java`, `WithdrawalDataIntegrationTest.java`(추가)

**Interfaces:**
- Consumes: Task 3 `MediaJobScheduler.onPostRemoved`.
- Produces: `FeedWithdrawalListener.on(UserWithdrawnEvent)` — 다른 탈퇴 리스너와 같은 방식(`@EventListener`, `@Order(Ordered.HIGHEST_PRECEDENCE + 5)`, `@Transactional`). 그 사람의 삭제되지 않은 게시물을 모두 소프트 삭제하고 `onPostRemoved`. 신고·차단 행은 남긴다(설계 7장). 차단 목록 조회에서 탈퇴한 상대는 빼고 보여준다(Task 5 `GET /blocks`에 조건 추가).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `WithdrawalDataIntegrationTest.탈퇴하면_게시물이_삭제되고_사진_삭제·원본_정리_작업이_생긴다()`
  - `FeedFlowIntegrationTest`(`@SpringBootTest`, `MediaStorage`는 `@MockitoBean`): 초안 → 게시 → (작업을 직접 DONE·사진 READY·`markVisible` 처리) → 비로그인 피드에 보임 → 다른 회원이 초상권 신고 → 피드에서 빠짐·`QUARANTINE` 작업 → 운영진 RESTORE → `PENDING`.
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 커밋** `Feat(api) : 탈퇴할 때 게시물 정리`

---

### Task 7: 워커 — 작업 처리

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/media/worker/{WorkerConfig,WorkerProperties,MediaWorkerLoop,MediaJobProcessor,JobHandler,ProcessJobHandler,QuarantineJobHandler,ReleaseJobHandler,DeleteMediaJobHandler,PurgeOriginalJobHandler,ImageTranscoder,TranscodedImage,ProcessRunner,FfmpegProcessRunner,OriginalStore,CachePurger,CloudflareCachePurger,NoopCachePurger,RetryPolicy}.java`
- Create: `apps/api/src/main/resources/application-worker.yml`
- Modify: `moderation/dto/ReportTargetResponse.java`·`moderation/service/ReportService.java`(`cleanupFailed` 필드)
- Test: `media/worker/{MediaJobProcessorTest,ProcessJobHandlerTest,QuarantineJobHandlerTest,ReleaseJobHandlerTest,ImageTranscoderTest,RetryPolicyTest,OriginalStoreTest,CloudflareCachePurgerTest}.java`, `media/worker/WorkerContextTest.java`

**Interfaces:**
- Consumes: Task 1 `MediaStorage`·`MediaUrls`, Task 2 `MediaJobRepository`·엔티티.
- Produces:
  - `application-worker.yml`: `spring.main.web-application-type: none`, `spring.flyway.enabled: false`, `app.scheduling.enabled: false`, `app.worker.enabled: true`, `app.worker.poll-interval: 2s`, `app.worker.idle-max-interval: 30s`(작업이 없으면 두 배씩 늘림), `app.worker.ffmpeg-timeout: 30s`.
  - `MediaWorkerLoop`(`@ConditionalOnProperty app.worker.enabled=true`, `SmartLifecycle`): 스레드 하나로 `requeueStale(now-10분)` → `MediaJobProcessor.runNext()` 반복. 종료 신호에 현재 작업을 마치고 멈춘다(최대 30초).
  - `MediaJobProcessor.runNext(): boolean` — `findClaimable`·`claim` 성공한 작업을 `JobHandler`(kind별)로 처리. 성공하면 `DONE`, 예외면 `RetryPolicy.nextRunAt(attempts)`로 `QUEUED` 또는 `FAILED`. `PROCESS`가 `FAILED`가 되면 사진 `FAILED`, 게시물 `markFailed()`.
  - `RetryPolicy.nextRunAt(int attemptsAfterFailure, Instant now): Optional<Instant>` — 1→+1분, 2→+5분, 3→empty(FAILED). 설계 문서의 "3회 재시도"는 "세 번째 실패에서 멈춤"으로 정한다.
  - `ProcessJobHandler`: 업로드 → 임시 파일(`Files.createTempFile`, 끝나면 삭제) → `OriginalStore.save` → `ImageTranscoder.transcode` → 게시물 상태 재확인(`PENDING` 아니면 중단, DONE) → `putPublic` 두 장 → 상태 재확인(아니면 방금 올린 두 장 `deletePublic`, DONE) → 사진 `READY`·크기 기록 → `deleteUpload` → 게시물의 사진이 모두 `READY`면 `markVisible(now)`. 상태 재확인은 별도 짧은 트랜잭션.
  - `QuarantineJobHandler`: 공개 두 장 `copyPublicToQuarantine` → `deletePublic` → `CachePurger.purge(두 URL)`. 공개본이 없으면(아직 처리 전) 아무것도 안 하고 DONE.
  - `ReleaseJobHandler`: 새 uuid로 `copyQuarantineToPublic` 두 장 → 사진 `public_key` 교체 → `deleteQuarantine`(예전 키) → 사진이 모두 READY면 `markVisible`(기존 `published_at` 유지). 격리본이 없으면(처리 전에 숨겨진 경우) NAS 원본으로 `ProcessJobHandler`와 같은 변환을 한다.
  - `DeleteMediaJobHandler`: 공개·격리·업로드 객체를 모두 지우고(없어도 성공) 캐시 비우기. `PurgeOriginalJobHandler`: NAS 원본 파일 삭제, 사진 행 `original_path` null.
  - `ImageTranscoder.transcode(Path source, Path workDir): TranscodedImage(Path display, Path thumb, int width, int height)` — `ffprobe`로 크기, `ffmpeg -threads 2 -i src -map_metadata -1 -vf scale=... -c:v libwebp -quality 80` 두 번. 원본보다 키우지 않는다.
  - `ProcessRunner.run(List<String> command, Duration timeout): ProcessResult(int exitCode, String stderrTail)` — 타임아웃이면 프로세스를 죽이고 예외. stderr는 마지막 2,000자만.
  - `OriginalStore.save(long mediaId, InputStream in, Instant now): String relativePath`, `delete(String)`, `open(String): InputStream` — 루트 밖 경로를 거부(`..`).
  - `CachePurger.purge(List<String> urls)` — `CloudflareCachePurger`: `POST https://api.cloudflare.com/client/v4/zones/{zone}/purge_cache` `{"files":[...]}`, Bearer 토큰, 타임아웃 5초, 2xx가 아니면 예외(재시도로 감). 토큰이 비면 `NoopCachePurger`(경고 로그 한 번).
  - 3회 실패한 `QUARANTINE`·`DELETE_MEDIA`는 `last_error`가 남고, 운영진 신고 목록의 해당 대상에 `targetStatus`와 별개로 `cleanupFailed: true`를 보여준다(`ReportTargetResponse`에 필드 추가).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `RetryPolicyTest`: 1→+1분, 2→+5분, 3→empty.
  - `MediaJobProcessorTest`: `예외가_나면_attempts가_늘고_다음_시각에_다시_QUEUED다()`, `세_번째_실패면_FAILED이고_사진과_게시물도_FAILED다()`, `우선순위가_높은_작업을_먼저_가져간다()`(리포지토리 테스트로 대체 가능).
  - `ProcessJobHandlerTest`(저장소·변환기 모킹): `모든_사진이_READY면_게시물이_VISIBLE이_된다()`, `하나라도_남으면_PENDING_그대로다()`, Review Focus 1: `업로드_뒤_게시물이_숨겨졌으면_방금_올린_공개본을_지운다()`, `변환_전에_숨겨졌으면_올리지_않는다()`.
  - `QuarantineJobHandlerTest.공개본을_격리로_옮기고_두_URL의_캐시를_비운다()`, `ReleaseJobHandlerTest.새_키로_되돌리고_예전_격리본을_지운다()`, `격리본이_없으면_원본으로_변환한다()`.
  - `ImageTranscoderTest`(`ProcessRunner` 모킹): 명령에 `-map_metadata -1`, `-threads 2`, `libwebp`, 1080·480 스케일 식이 들어간다. 종료 코드가 0이 아니면 stderr 꼬리를 담은 예외.
  - `OriginalStoreTest`(`@TempDir`): `연_월_폴더에_저장하고_상대_경로를_준다()`, `루트_밖_경로는_거부한다()`.
  - `CloudflareCachePurgerTest`(`MockRestServiceServer`): 요청 본문·헤더, 500이면 예외.
  - Review Focus 2: `WorkerContextTest`(`@SpringBootTest(properties = profiles=worker)`, H2, flyway는 테스트에서만 켬) — 컨텍스트에 `PushReceiptJob`·`MeetupReminderJob` 등 `@Scheduled` 메서드를 가진 빈이 스케줄되지 않음(`ScheduledTaskHolder`가 비었거나 없음), `MediaWorkerLoop` 빈은 있음, 웹 서버 없음.
- [ ] **Step 2: 실패 확인** — `./gradlew test`.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `./gradlew test`.
- [ ] **Step 5: 로컬 변환 확인** — 아무 JPEG(휴대폰 사진, EXIF 위치 포함 권장)를 스크래치패드에 두고 `ffmpeg`가 설치돼 있으면 `ImageTranscoder`와 같은 명령을 손으로 돌려 결과 WebP에 EXIF가 없는지(`ffprobe -show_format`) 본다. 없으면 Task 8의 이미지 안에서 확인한다.
- [ ] **Step 6: 커밋** `Feat(api) : 미디어 워커 작업 처리 추가`

---

### Task 8: 워커 이미지와 운영 구성

**Files:**
- Modify: `apps/api/Dockerfile`(마지막 `FROM ... AS api`, 그 위에 `FROM api AS worker`: `USER root` → `apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*` → `USER app`, `ENV SPRING_PROFILES_ACTIVE=prod,worker`)
- Modify: `deploy/compose.yml`(`worker` 서비스), `deploy/compose.build.yml`, `deploy/.env.example`, `.github/workflows/deploy.yml`(api 이미지를 `target: api`, 워커 이미지 `theclimbingnight-worker`를 `target: worker`로 빌드·푸시, `$DC pull api web worker`), `deploy/README.md`(환경변수 표, 6장 메모리 표, 8장 외부 설정), `docs/architecture.md` 5장 메모리 표
- Test: 없음(구성 검사와 로컬 기동으로 확인)

**Interfaces:**
- `worker` 서비스: `image: ghcr.io/withbbang/theclimbingnight-worker:${IMAGE_TAG:-latest}`, api와 같은 DB·R2 환경변수 + 워커 전용 셋, `volumes: - ${MEDIA_ORIGINALS_HOST_DIR}:/media/originals`(`deploy/.env`의 새 키, NAS 예: `/volume1/docker/theclimbingnight/media/originals`), `depends_on: api: condition: service_healthy`, `mem_limit: 512m`, `stop_grace_period: 40s`, 로그·재시작·TZ는 다른 서비스와 같음. `/dev/dri`는 3번(영상)에서 붙인다.
- README 8장 추가 항목: R2 버킷 두 개 만들기(`media-incoming` 비공개, `uploads/` 접두사 1일 삭제 규칙 / `media-public`), `media.<도메인>` 커스텀 도메인 연결, R2 API 토큰(두 버킷 Object Read & Write만), Cloudflare API 토큰(해당 Zone의 Cache Purge만), NAS 원본 폴더 만들기와 권한(컨테이너 `app` 사용자 uid가 쓸 수 있게).

- [ ] **Step 1: 이미지 빌드** — `docker build --platform linux/amd64 --target api -t tcn-api:local apps/api`와 `--target worker -t tcn-worker:local`. Expected: 둘 다 성공.
- [ ] **Step 2: 워커 이미지 안에서 변환 확인** — 스크래치패드의 JPEG를 마운트해 `docker run --rm --entrypoint ffmpeg ... -map_metadata -1 -c:v libwebp` → 성공, `ffprobe`로 메타데이터 없음 확인. `libwebp` 인코더가 없으면 멈추고 알린다.
- [ ] **Step 3: 구성 검사** — `deploy/.env.example`을 스크래치패드에 더미 값으로 복사해 `docker compose -f deploy/compose.yml --env-file <그 파일> config -q`. Expected: 오류 없음.
- [ ] **Step 4: 로컬 기동** — `compose.build.yml`을 겹쳐 `up -d mysql api worker`. 워커 로그에 작업 루프 시작 줄이 있고, 웹 포트가 없고, Flyway 로그가 없는지 본다. 미디어 키가 더미라 작업은 처리하지 않아도 된다. 끝나면 `down -v`, 만든 이미지 삭제.
- [ ] **Step 5: 문법 검사** — `pnpm dlx @action-validator/cli .github/workflows/deploy.yml`.
- [ ] **Step 6: 커밋** `Ops : 미디어 워커 컨테이너와 배포 추가`

---

### Task 9: 앱 — 피드 탭과 게시물 상세

**Files:**
- Install: `pnpm expo install expo-image`
- Create: `apps/mobile/types/api/post.ts`, `types/api/common.ts`(`CursorResponse<T>` 추가), `lib/post-api.ts`, `hooks/use-cursor-query.ts`, `hooks/use-feed.ts`, `hooks/use-post.ts`, `app/(tabs)/feed.tsx`, `app/posts/[id].tsx`, `components/feed/{PostCard,MediaCarousel,LoginToSeeMore,PostHiddenNotice}.tsx`
- Modify: `app/(tabs)/_layout.tsx`(벙과 굿즈 사이에 `feed`, 제목 "피드"), `components/navigation/AnimatedTabBar.tsx`(아이콘 `ImagesSquare`), `docs/architecture.md` 9장 표(이미 2단계로 있음, 상세 라우트만 추가)
- Test: `hooks/__tests__/use-cursor-query.test.ts`, `components/feed/__tests__/PostCard.test.tsx`, `app/(tabs)/__tests__/feed.test.tsx`(기존 화면 테스트 위치를 따른다)

**Interfaces:**
- Consumes: Task 4 API.
- Produces:
  - `types/api/post.ts`: `PostResponse`, `PostAuthorResponse`, `PostMediaResponse`, `PostDraftResponse`, `PostUploadResponse` (API DTO와 같은 이름·필드).
  - `lib/post-api.ts`: `fetchFeed(cursor: string | null, size: number)`, `fetchPost(id: number)`, `fetchMyInProgress()`, `createDraft(media)`, `publishPost(id, body)`, `updatePost(id, body)`, `deletePost(id)`.
  - `useCursorQuery<T>(queryKey, fetchPage: (cursor: string | null) => Promise<CursorResponse<T>>, enabled)` — `usePagedQuery`와 같은 반환 형태(id 중복 제거), `getNextPageParam`은 `nextCursor ?? undefined`.
  - 쿼리 키: `['posts']`(피드), `['posts', id]`, `['posts', 'mine', 'in-progress']`.
- 화면 규칙:
  - 카드: 작성자(닉네임 첫 글자 아바타 + 닉네임) / `MediaCarousel`(가로 `FlatList` `pagingEnabled`, 점 표시, `expo-image`로 `thumbnailUrl`을 placeholder로 두고 `url` 표시, 비율은 첫 사진 width/height로 고정하고 1:1.25~1.91:1로 자름) / 좋아요·댓글 수(이번엔 숫자만, 버튼 없음) / 본문 3줄 접기·"더 보기" / 상대 시간(`lib/kst.ts`).
  - 비로그인: 3개 뒤 `LoginToSeeMore` 카드("더 많은 사진은 로그인하고 볼 수 있어요" + 로그인 버튼), 더 불러오지 않는다. 비로그인이 상세를 열다 401을 받으면 로그인 화면으로 보낸다(기존 `AUTH_REQUIRED` 처리).
  - 빈 피드: "아직 올라온 사진이 없어요" + (로그인 시) 올리기 버튼.
  - 숨겨진 내 글 상세: `PostHiddenNotice`("운영 정책에 따라 숨겨졌어요").
  - 피드는 FlashList + `PullToRefresh`, 첫 로딩·실패는 `QueryState`. 카드 등장은 `FadeInItem`.
  - 화면 디자인은 `frontend-design`·`ui-ux-pro-max` 스킬로 잡고 `useTheme` 토큰만 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `use-cursor-query`: `it("stops when nextCursor is null")`, `it("drops duplicated ids across pages")`.
  - `PostCard`: `it("shows the image count dots only when there are two or more")`, `it("collapses the body after three lines and expands on 더 보기")`.
  - 피드 화면: `it("shows the login card after three posts when signed out")`, `it("does not request a next page when signed out")`, `it("shows the empty message when there are no posts")`.
- [ ] **Step 2: 실패 확인** — `pnpm test`.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`, `pnpm expo install --check`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 피드 탭과 게시물 상세 추가`

---

### Task 10: 앱 — 사진 올리기

**Files:**
- Install: `pnpm expo install expo-image-picker expo-image-manipulator`
- Modify: `app.config.ts`(`expo-image-picker` 플러그인, `photosPermission`: "게시물에 올릴 사진을 고르려고 사진 보관함에 접근해요.", 카메라 권한은 이번에 요청하지 않음)
- Create: `lib/upload-photos.ts`, `hooks/use-create-post.ts`, `app/posts/new.tsx`, `app/posts/[id]/edit.tsx`, `components/feed/{PhotoPickerStrip,InProgressPosts}.tsx`
- Modify: `app/(tabs)/feed.tsx`(로그인 시 오른쪽 위 + 버튼, 맨 위 `InProgressPosts`)
- Test: `lib/__tests__/upload-photos.test.ts`, `hooks/__tests__/use-create-post.test.ts`, `app/posts/__tests__/new.test.tsx`

**Interfaces:**
- Consumes: Task 9 `lib/post-api.ts`.
- Produces:
  - `preparePhoto(asset: ImagePickerAsset): Promise<PreparedPhoto>` — `expo-image-manipulator`로 긴 변 2048px 이하로 줄이고(작으면 그대로) JPEG 품질 0.85. `PreparedPhoto { uri, width, height, byteSize, contentType: 'image/jpeg' }`. 15MB를 넘으면 품질 0.7로 한 번 더, 그래도 넘으면 오류.
  - `uploadPhotos(uploads: PostUploadResponse[], photos: PreparedPhoto[], onProgress: (done: number, total: number) => void): Promise<void>` — 하나씩 순서대로 `fetch(url, { method: 'PUT', headers, body: blob })`. R2 PUT은 API가 아니므로 `apiFetch`를 쓰지 않는 유일한 곳이다(규칙 예외를 파일 주석에 적는다). 실패하면 한 번 재시도 후 throw.
  - `useCreatePost()` → `{ submit(photos, body), progress, error }`: `createDraft` → `uploadPhotos` → `publishPost` → `['posts', 'mine', 'in-progress']` invalidate. `useSubmit`으로 중복 전송 방지.
- 화면 규칙:
  - 새 글: 사진 보관함 다중 선택(최대 10, `selectionLimit: 10`, 순서 유지) → 미리보기 줄(길게 눌러 빼기) → 본문(2,200자 카운터) → "올리기". 진행 표시는 "사진 올리는 중 n/전체". 실패하면 `ErrorBanner` + 다시 시도.
  - 편집: 본문만(`FreshQuery`로 한 번 채움), 저장 뒤 `['posts', id]`·`['posts']` invalidate.
  - `InProgressPosts`: 내 `PENDING`은 "사진 정리 중…"(10초마다 다시 조회, 비면 멈추고 `['posts']` invalidate), `FAILED`는 "올리지 못했어요" + 삭제 버튼.
  - USER 미만(비로그인)은 + 버튼이 없다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `upload-photos`: `it("resizes only when the long side is over 2048")`, `it("retries a failed PUT once and then throws")`, `it("reports progress after each photo")`.
  - `use-create-post`: `it("publishes after every upload succeeds")`, `it("does not publish when an upload fails")`.
  - 새 글 화면: `it("disables 올리기 until a photo is picked")`, `it("shows the count limit message over 2200 characters")`.
- [ ] **Step 2: 실패 확인** — `pnpm test`.
- [ ] **Step 3: 구현.** `expo-image-manipulator`·`expo-image-picker` API는 설치된 버전의 `node_modules` 타입을 읽고 쓴다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`, `pnpm expo install --check`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 사진 게시물 올리기 추가`

---

### Task 11: 앱 — 신고·차단·운영진 신고 목록

**Files:**
- Create: `types/api/moderation.ts`, `lib/moderation-api.ts`, `hooks/use-moderation.ts`, `components/feed/{PostMenu,ReportSheet}.tsx`, `app/settings/blocks.tsx`, `app/admin/reports.tsx`, `components/admin/ReportTargetRow.tsx`
- Modify: `app/posts/[id].tsx`·`components/feed/PostCard.tsx`(⋯ 메뉴), `app/(tabs)/my.tsx`(차단 목록 줄), `app/admin/index.tsx`(신고 메뉴)
- Test: `components/feed/__tests__/{PostMenu,ReportSheet}.test.tsx`, `app/admin/__tests__/reports.test.tsx`

**Interfaces:**
- Consumes: Task 5 API.
- 화면 규칙:
  - ⋯ 메뉴: 내 글 → 수정·삭제(`ConfirmAction`). 남의 글 → 신고·"○○님 차단"(`ConfirmAction`, "서로의 글이 보이지 않아요"). ADMIN이면 남의 글에 "숨기기"도. 비로그인은 메뉴를 누르면 로그인 화면.
  - 신고 시트: 사유 5개(`RadioRow`) — 스팸·광고 / 음란·선정 / 욕설·혐오·괴롭힘 / 초상권·사생활 침해(내 사진이 동의 없이 올라왔어요) / 기타(입력 필수, 500자). 보내면 "신고를 접수했어요. 운영진이 확인할게요." `REPORT_ALREADY_SUBMITTED`도 같은 안내로 끝낸다.
  - 차단하면 `['posts']` invalidate, 상세에서 들어왔으면 뒤로 간다.
  - 차단 목록: 닉네임·차단일·"차단 해제".
  - 운영진 신고 목록: `AdminScreen` + `usePagedQuery` + `PagedList`. 줄 = 대상 종류·상태·신고 수·사유별 수·상세 사유 / 게시물이면 격리 사진(`quarantineUrls`) 또는 공개 사진 썸네일 / `cleanupFailed`면 경고 표시. 버튼 숨김·복원·기각(`useAdminMutation`, 처리 후 `useDropFromPages`).
  - 푸시 `REPORT_SUBMITTED`를 누르면 운영진 신고 목록으로 간다(`lib/push-route.ts`).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `PostMenu`: `it("shows edit and delete only on my post")`, `it("shows 숨기기 only to admins")`.
  - `ReportSheet`: `it("requires a detail when 기타 is chosen")`, `it("treats an already submitted report as success")`.
  - 운영진 목록: `it("removes the row after 기각")`.
  - `push-route` 테스트에 `REPORT_SUBMITTED` → `/admin/reports` 추가.
- [ ] **Step 2: 실패 확인** — `pnpm test`.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test`.
- [ ] **Step 5: 커밋** `Feat(mobile) : 신고와 차단, 운영진 신고 목록 추가`

---

### Task 12: 문서와 개인정보처리방침

**Files:**
- Modify: `docs/architecture.md`(5장 R2 버킷 구성·워커, 9장 화면 표에 신고 목록·차단 목록, 10장 2단계 모델을 설계 문서 9장으로 바꾸고 이 계획의 작업 종류 반영, 11장 미디어 처리 흐름, 12장 1.2 행), `docs/superpowers/specs/2026-11-26-phase2-feed-design.md`(격리 방식으로 바뀐 점을 8장에 반영)
- Modify: `apps/web/content/privacy.ts`(수집 항목에 게시물·사진·신고·차단 기록, 공개 범위 "최신 게시물 3개는 앱 설치자 누구나", Cloudflare R2 저장, 보관 기간은 `[확인 필요: …]`로), `apps/web/content/terms.ts`(게시물 권리·삭제·임시조치 기준 `[확인 필요: …]`), `docs/content-todo.md`(위 새 항목, 개수)
- Modify: `.claude/rules/api.md`(워커 프로필 규칙 한 줄: 워커에서 `@Scheduled`·웹·Flyway가 돌지 않음, 새 `@Scheduled` 작업은 `app.scheduling.enabled`로 묶인다는 것)
- Test: `apps/web`의 기존 콘텐츠 테스트

- [ ] **Step 1: 수정** — 위 파일. `[확인 필요]` 표시 형식은 기존 파일과 같게.
- [ ] **Step 2: 확인** — `apps/web`: `pnpm lint && pnpm test && pnpm build`. `grep -rn '\[확인 필요' apps/web/content | wc -l`과 `docs/content-todo.md` 개수가 같다.
- [ ] **Step 3: 커밋** `Docs : 피드 설계와 개인정보처리방침 반영`

---

## 이 계획이 끝나면 되는 것

- 회원이 사진 게시물을 올리면 워커가 처리해 피드에 나온다. 비로그인은 최신 3개만 본다.
- 신고하면 기준에 따라 바로 숨겨지고 공개 URL도 막힌다. 운영진은 격리된 사진을 보고 숨김·복원·기각한다. 회원끼리 차단할 수 있다.
- 운영에 내려면 사용자가 README 8장(R2 버킷 두 개, `media.<도메인>`, R2·Cloudflare 토큰, NAS 원본 폴더)을 마쳐야 한다.
- 다음: 2번 계획(좋아요·댓글·프로필). 댓글 신고는 이 계획의 신고 구조에 `COMMENT`를 열어 붙인다.
