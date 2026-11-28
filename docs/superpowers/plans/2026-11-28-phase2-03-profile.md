# 2단계-03 프로필 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 다른 회원의 프로필(사진·닉네임·자기소개·게시물 수·3열 격자)을 보고, 내 프로필 사진과 자기소개를 바꾼다. 운영진은 신고된 사용자의 프로필 사진을 내리고 자기소개를 지운다.

**Architecture:**
- 프로필 사진은 게시물 사진과 같은 흐름(presigned PUT → 확정 → 워커 변환 → 공개 버킷)을 탄다. `media_jobs.target_type`으로 게시물 사진과 프로필 사진을 나누고, 워커 처리기를 `(kind, target_type)`으로 고른다.
- 새 `profile` 패키지에 프로필 조회·자기소개·프로필 사진 업로드를 둔다. `users`에는 `bio`와 지금 쓰는 사진 `profile_image_id`만 둔다.
- 작성자를 담는 응답(게시물·댓글·좋아요·차단 목록)은 `UserAvatars`로 사진 URL을 한 번에 붙인다.
- 앱은 `/users/[id]` 프로필 화면과 `/settings/profile` 편집 화면을 새로 만들고, `Avatar`가 사진을 보이게 한다.

**Tech Stack:** Spring Boot 4.1.2 / Java 17 / JPA / Flyway(V10), H2(테스트) / MySQL 8.4(운영), ffmpeg(워커). Expo SDK 58, React Query 5, FlashList 2.3.2, expo-image, expo-image-picker, expo-image-manipulator(모두 설치돼 있음), Reanimated 4.7.1.

**Spec:** `docs/superpowers/specs/2026-11-28-phase2-profile-design.md` (상위: `docs/superpowers/specs/2026-11-26-phase2-feed-design.md`). 규약은 `.claude/rules/{api,mobile,testing}.md`와 `api-conventions` 스킬을 따른다.

**이 계획에 없는 것:** 동호회 활동 정보, 팔로우, 프로필 사진 필터·편집, 프로필 사진 자동 임시조치, 예전에 내린 격리본을 보는 화면.

## 작업 방식

- 브랜치 `feat/profile`(`dev`에서 땀). 첫 커밋은 설계 문서와 이 계획이다(`Docs : 프로필 설계와 구현 계획 추가`).
- 커밋하기 전에 `.gitmessage.txt`를 읽는다. 커밋 메시지 끝에는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`을 넣는다.
- 코드 태스크 순서: 실패하는 테스트 → 실패 확인 → 구현 → 바뀐 앱의 lint·test → 커밋.
- API 테스트는 `./gradlew test`로 돌린다(context-mode가 켜져 있으면 `ctx_execute`). 결과는 `apps/api/build/test-results/test/*.xml`로 센다.
- 리뷰 강도는 `medium`을 제안한다(워커·미디어 작업 경로와 신고 조치가 바뀐다). 실행 전에 승인받는다.

## 설계 문서와 달라지는 점 (계획에서 정함)

- **공개 키 모양:** 게시물 사진(`p/{hex}` + `/d.webp`·`/t.webp`)과 같게 `a/{hex}` + `/l.webp`(320px)·`/s.webp`(96px)로 한다. 설계 문서의 `profile/{UUID}_l.webp`는 쓰지 않는다. 업로드 키도 게시물과 같은 `uploads/{hex}.jpg`다(수명 주기 규칙이 `uploads/` 전체에 걸림).
- **내 정보에 사진·자기소개를 넣지 않는다:** `UserResponse`는 토큰·벙 주최자·인증 신청에도 쓰여서 그대로 둔다. 마이 탭과 편집 화면은 `GET /users/{내 id}/profile`로 받는다. 설계 문서 8장의 "`UserResponse`에 `bio`·`profileImage`"는 이것으로 바꾼다.
- **자기소개 수정 경로:** `PATCH /api/v1/users/me/profile`(`{ bio }`) 그대로. 닉네임은 기존 `PATCH /users/me`.
- **운영진 "둘 다":** 사진과 자기소개가 다 있으면 카드에 "둘 다 내리기" 버튼을 하나 더 두고, 앱이 `REMOVE_PROFILE_IMAGE` → `CLEAR_BIO`를 차례로 보낸다. 두 번째 요청은 신고가 이미 닫혀 있어도 조치하고 200을 준다(`close()`는 열린 신고가 없으면 아무것도 안 한다).
- **NAS 원본 경로:** `profile/yyyy/MM/{profileImageId}.jpg`(원본 루트 기준 상대 경로). 게시물 원본과 파일 이름이 겹치지 않게 `profile/` 아래에 둔다.

## Global Constraints

- 자기소개: 앞뒤 공백을 자른 뒤 0~150자(코드포인트), 줄바꿈(`\n`) 최대 3개. `\r\n`·`\r`은 `\n`으로 바꾼다. 비면 `null`. 어기면 `USER_BIO_INVALID`(400, "자기소개는 150자, 4줄까지 쓸 수 있어요.").
- 프로필 사진 업로드: `image/jpeg`, 1바이트~5MB(5,242,880), presigned PUT 15분. 앱은 1:1로 자른 뒤 긴 변 1024px JPEG 0.85로 저장한다.
- 처리 중 제한: 한 사용자의 `PROCESSING` 프로필 사진은 최대 2개. 넘으면 `PROFILE_IMAGE_BUSY`(409, "앞서 올린 사진을 적용하는 중이에요. 잠시 뒤 다시 해 주세요.").
- 확정할 때 업로드 객체가 없거나 크기가 다르면 `PROFILE_IMAGE_UPLOAD_INCOMPLETE`(409, "사진이 아직 올라가지 않았어요. 다시 올려 주세요.").
- 변환: 가운데 정사각 자르기 → WebP 320px(`/l.webp`)·96px(`/s.webp`), 원본이 더 작으면 키우지 않는다. EXIF·프레임 메타데이터 제거는 `ImageTranscoder`의 기존 규칙(`-map_metadata -1`, `STRIP_FRAME_METADATA`, `-f jpeg_pipe`, `-protocol_whitelist file`) 그대로.
- 격자: 한 페이지 기본 18, `@Min(1) @Max(30)`. 커서는 기존 `feed.Cursor(publishedAt, id)`. 정렬 `published_at desc, id desc`.
- 권한: 프로필·격자는 `@RequireRole(Role.USER)`(비로그인 401). 차단 관계(어느 쪽이든)·탈퇴·없는 사용자는 404 `COMMON_NOT_FOUND`. 운영진 조치는 `@RequireRole(Role.ADMIN)`(기존 컨트롤러).
- 보존: 본인이 바꾸거나 되돌린 사진은 `DELETE_MEDIA` + 30일 뒤 `PURGE_ORIGINAL`. 운영진이 내린 사진은 `QUARANTINE`만(원본·격리본 보존). 탈퇴하면 그 사람의 모든 프로필 사진에 `DELETE_MEDIA` + 30일 뒤 `PURGE_ORIGINAL`.
- 새 서비스 클래스는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`. `ServiceTransactionConventionTest.PACKAGES`에 `com.theclimbingnight.profile`을 추가한다.
- 잠금 순서: 사용자 행(`findByIdForUpdate`) → 프로필 사진 행. 탈퇴(`UserService.withdraw`)가 사용자 행을 먼저 잠그는 것과 같다.
- 앱 문구:
  - 빈 격자(남): "아직 올린 사진이 없어요" / (나): "첫 사진을 올려 보세요"
  - 숨긴 칸: "숨겨짐"
  - 없는 프로필: "없는 사용자예요"
  - 적용 중: "적용하는 중" / 30초 경과: "잠시 뒤 반영돼요" / 실패: "사진을 적용하지 못했어요. 다시 올려 주세요"
  - 사진 메뉴: "사진 바꾸기", "기본 이미지로"
  - 운영진 버튼: "프로필 사진 내리기", "자기소개 지우기", "둘 다 내리기"
- 새 환경변수 없음.

## Review Focus

1. **사진을 연달아 두 번 바꿨는데 첫 번째 처리가 늦게 끝난다:** 두 번째 사진이 남아야 하고 첫 번째는 `REMOVED`, 공개본이 지워져야 한다(Task 5 테스트 `olderImageFinishingLateDoesNotReplaceNewer`).
2. **처리 중에 탈퇴하거나 기본 이미지로 되돌린다:** 처리가 끝나도 `profile_image_id`가 다시 채워지지 않는다(Task 5 `resetWhileProcessingLeavesNoImage`, `withdrawnWhileProcessingLeavesNoImage`).
3. **게시물 사진과 프로필 사진의 id가 같다:** 게시물 숨김이 같은 번호의 프로필 사진 작업을 취소하지 않고, 프로필 처리 실패가 같은 번호의 게시물 사진을 FAILED로 만들지 않는다(Task 1 `cancelQueuedIgnoresOtherTargetType`, Task 5 `profileFailureDoesNotTouchPostMedia`).
4. **나를 차단한 사람의 프로필 주소로 직접 들어간다:** 프로필·격자 모두 404, 그 사람 게시물 수도 새지 않는다(Task 2 `blockedEitherWayIs404`).
5. **자기소개에 줄바꿈만 잔뜩 넣거나 이모지 150개를 넣는다:** 줄바꿈 4개는 400, 이모지 150개는 통과(코드포인트 기준)(Task 2 `BioPolicyTest`).

---

## API

### Task 1: 스키마·엔티티·작업 대상 종류

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V10__profile.sql`
- Create: `apps/api/src/main/java/com/theclimbingnight/profile/entity/{ProfileImage,ProfileImageStatus}.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/profile/repository/ProfileImageRepository.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/media/job/MediaTargetType.java`
- Modify: `user/entity/User.java`, `media/job/{MediaJob,MediaJobRepository,MediaJobScheduler}.java`, `media/worker/MediaJobProcessor.java`(failMedia 조건만)
- Test: `profile/repository/ProfileImageRepositoryTest.java`, `media/job/MediaJobRepositoryTest.java`(있으면 추가, 없으면 새로)

**Interfaces:**
- Produces:
  - `enum MediaTargetType { POST_MEDIA, PROFILE_IMAGE }`
  - `MediaJob.queue(MediaJobKind kind, long targetId, Instant now)`는 `POST_MEDIA`로 둔다. 새 `MediaJob.queue(MediaJobKind kind, MediaTargetType type, long targetId, Instant now)`.
  - `MediaJobRepository.cancelQueued(Collection<Long> targetIds, MediaTargetType type, MediaJobKind kind)` (기존 2인자 버전은 지우고 호출처를 `POST_MEDIA`로 바꾼다)
  - `enum ProfileImageStatus { UPLOADING, PROCESSING, READY, FAILED, REMOVED }`
  - `ProfileImage`: `static ProfileImage start(long userId, String uploadKey, String publicKey, long byteSize)`, `largeKey()`(`publicKey + "/l.webp"`), `smallKey()`(`publicKey + "/s.webp"`), `markProcessing()`, `markReady()`, `markFailed()`, `markRemoved()`, `recordOriginal(String)`, `clearOriginal()`, 게터. 상수 `LARGE_SUFFIX = "/l.webp"`, `SMALL_SUFFIX = "/s.webp"`.
  - `ProfileImageRepository`: `findByIdForUpdate(long)`, `countByUserIdAndStatus(long, ProfileImageStatus)`, `findAllByUserIdAndStatus(long, ProfileImageStatus)`, `findAllByUserId(long)`, `findIdsByStatusCreatedBefore(ProfileImageStatus, Instant)`, `existsByUserIdAndStatusAndIdGreaterThan(long userId, ProfileImageStatus status, long id)`.
  - `User`: 필드 `bio`(150), `profileImageId`(Long). `changeBio(String)`, `useProfileImage(Long id)`. `withdraw`에서 `bio`·`profileImageId`도 `null`로.

- [ ] **Step 1: V10 마이그레이션**
  - `ALTER TABLE users ADD COLUMN bio VARCHAR(150) NULL, ADD COLUMN profile_image_id BIGINT NULL`
  - `profile_images`(id, user_id NOT NULL FK users, status VARCHAR(16), upload_key VARCHAR(128), public_key VARCHAR(128), original_path VARCHAR(255) NULL, byte_size BIGINT, created_at, updated_at), 인덱스 `(user_id, status)`, `(status, created_at)`.
  - `users.profile_image_id` FK → `profile_images(id)`. 두 표가 서로 가리키므로 `profile_images`를 만든 뒤 `ALTER TABLE users ADD CONSTRAINT`.
  - `ALTER TABLE media_jobs ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'POST_MEDIA'`. 인덱스 `idx_media_jobs_target`을 `(target_type, target_id, kind)`로 바꾼다.
  - 격자 인덱스 `CREATE INDEX idx_posts_author_grid ON posts (author_id, status, published_at, id)`.
  - 파일 첫 줄 주석에 "users ↔ profile_images 순환 FK: 탈퇴는 행을 지우지 않으므로 문제없음"을 적는다.

- [ ] **Step 2: 실패하는 테스트**
  - `ProfileImageRepositoryTest.existsNewerConfirmed`: 같은 사용자의 PROCESSING id 10·11이 있을 때 `existsByUserIdAndStatusAndIdGreaterThan(u, PROCESSING, 10)`는 true, `(…, 11)`은 false.
  - `MediaJobRepositoryTest.cancelQueuedIgnoresOtherTargetType`: `(PROCESS, POST_MEDIA, 7)`과 `(PROCESS, PROFILE_IMAGE, 7)`을 넣고 `cancelQueued([7], POST_MEDIA, PROCESS)` → 1건만 CANCELED, 프로필 작업은 QUEUED.
  - `MediaJobRepositoryTest.failedCleanupOnlyCountsPostMedia`: 게시물 사진 id 7의 게시물과, 같은 id 7인 프로필 사진의 FAILED `QUARANTINE`만 있으면 `findPostIdsWithFailedCleanup([그 게시물])`이 비어 있다.

- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*ProfileImageRepositoryTest' --tests '*MediaJobRepositoryTest'` → 컴파일 실패.

- [ ] **Step 4: 구현**
  - `MediaJob`에 `@Enumerated targetType`(length 16, not null).
  - `cancelQueued`·`findPostIdsWithFailedCleanup`(본 쿼리와 `later` 서브쿼리 둘 다)에 `targetType = POST_MEDIA` 조건.
  - `MediaJobScheduler.cancelProcess`는 `POST_MEDIA`로 부른다.
  - `MediaJobProcessor.fail`: `failMedia`는 `job.getTargetType() == POST_MEDIA`일 때만 부른다. 프로필 사진 실패 처리는 Task 5에서 넣는다.

- [ ] **Step 5: 통과 확인** — `./gradlew test` 전체 PASS(기존 워커 테스트 포함).

- [ ] **Step 6: 커밋** — `Feat(api) : 프로필 스키마와 미디어 작업 대상 종류 추가`

### Task 2: 프로필 조회·격자·자기소개

**Files:**
- Create: `profile/controller/ProfileController.java`, `profile/service/{ProfileQueryService,BioService,BioPolicy}.java`, `profile/dto/{ProfileResponse,ProfileImageUrls,GridPostResponse,BioUpdateRequest}.java`
- Create: `user/service/UserAvatars.java`
- Modify: `feed/repository/PostRepository.java`, `common/ErrorCode.java`, `ServiceTransactionConventionTest`
- Test: `profile/service/{BioPolicyTest,ProfileQueryServiceTest}.java`, `profile/controller/ProfileControllerTest.java`

**Interfaces:**
- Consumes: Task 1 `User.bio/profileImageId`, `ProfileImage.largeKey()/smallKey()`.
- Produces:
  - `record ProfileImageUrls(String largeUrl, String smallUrl)`
  - `record ProfileResponse(long id, String nickname, String bio, ProfileImageUrls profileImage, long postCount, boolean mine)` — `profileImage`는 nullable
  - `record GridPostResponse(long id, String thumbnailUrl, int mediaCount, String status)` — `thumbnailUrl`은 숨긴 글이면 null, `status`는 본인에게만
  - `UserAvatars` (`@Component`, `user/service`): `Map<Long, ProfileImageUrls> of(Collection<Long> userIds)` — READY인 지금 사진만, 없는 사용자는 키 없음. `String smallUrl(Map<Long, ProfileImageUrls>, long userId)` 편의 정적 메서드(없으면 null).
  - `ProfileQueryService.profile(long viewerId, long userId): ProfileResponse`, `posts(long viewerId, long userId, String cursor, int size): CursorResponse<GridPostResponse>`
  - `BioService.update(long userId, String rawBio): ProfileResponse`
  - `BioPolicy.normalize(String raw): String` (nullable 반환)
  - `PostRepository`: `findProfileGrid(long authorId, Collection<PostStatus> statuses, Instant cursorPublishedAt, Long cursorId, Pageable)`, `countByAuthorIdAndStatusAndDeletedAtIsNull(long, PostStatus)`
  - `ErrorCode.USER_BIO_INVALID`

- [ ] **Step 1: 실패하는 테스트**
  - `BioPolicyTest`: `"  "` → null / `"a\r\nb"` → `"a\nb"` / 150자 통과, 151자 `USER_BIO_INVALID` / 이모지 150개 통과 / `"1\n2\n3\n4"` 통과, `"1\n2\n3\n4\n5"` 거부.
  - `ProfileQueryServiceTest`:
    - `othersSeeOnlyVisible`: 남의 VISIBLE 2·HIDDEN 1·PENDING 1 → 격자 2개, `status` 모두 null, `postCount` 2.
    - `ownerSeesHiddenWithoutThumbnail`: 본인 요청이면 HIDDEN·HIDDEN_PENDING 포함, 숨긴 칸 `thumbnailUrl == null`, `status == "HIDDEN"`. PENDING·FAILED·DRAFT·삭제 글은 없음. `postCount`는 VISIBLE 수.
    - `blockedEitherWayIs404`: 내가 차단 / 나를 차단 각각 `profile`·`posts` 모두 `COMMON_NOT_FOUND`.
    - `withdrawnIs404`.
    - `gridPagesByCursor`: 20개 → size 18이면 18개 + 커서, 다음 2개 + 커서 null. 첫 미디어 `thumbKey()`의 URL, `mediaCount`는 미디어 수.
    - `profileImageOnlyWhenReady`: `profile_image_id`가 가리키는 READY 사진이면 두 URL, 없으면 `profileImage == null`.
  - `ProfileControllerTest`: 비로그인 `GET /api/v1/users/1/profile` → 401 `AUTH_REQUIRED`. `PATCH /api/v1/users/me/profile` `{ "bio": "x" }` → 200, `data.bio == "x"`. `size=31` → 400.

- [ ] **Step 2: 실패 확인** — 해당 테스트 실행, 컴파일 실패.

- [ ] **Step 3: 구현**
  - 컨트롤러: `GET /api/v1/users/{id}/profile`, `GET /api/v1/users/{id}/posts?cursor&size=18`, `PATCH /api/v1/users/me/profile`. `/users/me/...`와 겹치지 않게 경로 변수는 `{id:\\d+}`로 쓴다.
  - 접근 검사: 사용자가 없거나 `!isActive()`거나 `UserBlockRepository.existsBetween(viewer, userId)`면 404. 본인이면 차단 검사를 건너뛴다.
  - 격자 상태: 남이면 `[VISIBLE]`, 본인이면 `[VISIBLE, HIDDEN_PENDING, HIDDEN]`. 썸네일은 미디어를 `findAllByPostIdInOrderBySortOrder`로 한 번에 읽어 첫 장의 `thumbKey()`, 숨긴 글은 null.
  - `UserAvatars.of`: `users.profileImageId`와 `profile_images`를 조인해 `status = READY`인 것만 읽는 JPQL 하나.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 프로필 조회와 자기소개 수정 추가`

### Task 3: 작성자 응답에 프로필 사진 붙이기

**Files:**
- Modify: `feed/dto/{PostAuthorResponse,LikerResponse}.java`, `moderation/dto/BlockedUserResponse.java`, `feed/service/{PostResponseMapper,PostQueryService,PostCommandService,PostLikeService}.java`, `comment/service/{CommentQueryService,CommentCommandService}.java`, `moderation/service/{BlockService,ModerationService}.java`
- Test: 각 서비스의 기존 테스트에 단언 추가

**Interfaces:**
- Consumes: Task 2 `UserAvatars.of`, `UserAvatars.smallUrl`.
- Produces:
  - `record PostAuthorResponse(long id, String nickname, String profileImageUrl)`
  - `record LikerResponse(long userId, String nickname, String profileImageUrl)`
  - `record BlockedUserResponse(long userId, String nickname, String profileImageUrl, OffsetDateTime blockedAt)`
  - `PostResponseMapper.toResponse(Post post, String authorNickname, String authorImageUrl, List<PostMedia> media, Long viewerId, boolean likedByMe)`

- [ ] **Step 1: 실패하는 테스트**
  - `PostQueryServiceTest.authorHasProfileImageUrl`: 작성자에게 READY 사진이 있으면 피드·상세·비로그인 맛보기 3개 모두 `author.profileImageUrl`이 `{base}/a/{hex}/s.webp`, 없으면 null.
  - `CommentQueryServiceTest.authorHasProfileImageUrl`, `PostLikeServiceTest.likerHasProfileImageUrl`, `BlockServiceTest.blockedUserHasProfileImageUrl`(탈퇴한 사용자는 null) — 같은 단언.
  - 페이지당 `UserAvatars.of`가 한 번만 불리는지는 따로 세지 않는다(코드 리뷰로 본다).

- [ ] **Step 2: 실패 확인** — 컴파일 실패.

- [ ] **Step 3: 구현** — 각 서비스가 이미 모으는 작성자 id 묶음으로 `UserAvatars.of`를 한 번 부르고 결과를 넘긴다. 운영진 신고 목록의 게시물·댓글 작성자도 같은 방식.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 작성자 응답에 프로필 사진 주소 추가`

### Task 4: 프로필 사진 업로드 API

**Files:**
- Create: `profile/controller/ProfileImageController.java`, `profile/service/{ProfileImageService,ProfileImageCleanupJob}.java`, `profile/dto/{ProfileImageUploadResponse,ProfileImageStartRequest,ProfileImageStatusResponse}.java`
- Modify: `media/job/MediaJobScheduler.java`, `common/ErrorCode.java`
- Test: `profile/service/{ProfileImageServiceTest,ProfileImageCleanupJobTest}.java`, `profile/controller/ProfileImageControllerTest.java`, `media/job/MediaJobSchedulerTest`(있으면 추가)

**Interfaces:**
- Consumes: Task 1 엔티티·저장소, `MediaStorage.presignUpload/uploadedSize`, `PresignedUpload`.
- Produces:
  - `ProfileImageStartRequest(@Min(1) @Max(5_242_880) long byteSize)`
  - `ProfileImageUploadResponse(long id, String url, Map<String,String> headers, OffsetDateTime expiresAt)` — 게시물 `PostUploadResponse`와 같은 필드 이름
  - `ProfileImageStatusResponse(long id, String status)`
  - `ProfileImageService.start(long userId, long byteSize)`, `confirm(long userId, long imageId)`, `status(long userId, long imageId)`, `reset(long userId)`
  - `MediaJobScheduler`: `enqueueProfileProcess(ProfileImage)`, `onProfileImageDropped(ProfileImage)`(DELETE_MEDIA + 30일 뒤 PURGE_ORIGINAL, 큐의 PROCESS 취소), `onProfileImageTakenDown(ProfileImage)`(QUARANTINE만, 큐의 PROCESS 취소)
  - `ErrorCode.PROFILE_IMAGE_BUSY`, `PROFILE_IMAGE_UPLOAD_INCOMPLETE`

- [ ] **Step 1: 실패하는 테스트** (`ProfileImageServiceTest`, 가짜 `MediaStorage`)
  - `startDropsOldUploading`: UPLOADING 행이 있는 사용자가 다시 시작하면 예전 행이 없어지고 새 행 하나, presign은 `image/jpeg`·요청 크기·15분.
  - `startRejectsWhenTwoProcessing`: PROCESSING 2개면 `PROFILE_IMAGE_BUSY`.
  - `confirmChecksUpload`: `uploadedSize`가 비거나 크기가 다르면 `PROFILE_IMAGE_UPLOAD_INCOMPLETE`. 맞으면 PROCESSING + `(PROCESS, PROFILE_IMAGE, id)` 작업 1건.
  - `confirmOthersImageIs404`, `confirmTwiceIsNoop`(이미 PROCESSING이면 작업을 더 만들지 않고 성공).
  - `statusOnlyOwner`: 남의 id면 404.
  - `resetDropsCurrent`: 지금 사진이 있으면 `profile_image_id == null`, 그 사진 REMOVED, `DELETE_MEDIA` 1건 + `PURGE_ORIGINAL`(now+30일) 1건. 사진이 없으면 아무 일 없음.
  - `resetAlsoDropsProcessing`: 처리 중인 사진도 REMOVED가 되고 큐의 PROCESS가 취소된다. 그래야 늦게 끝난 처리가 되돌린 사진을 다시 채우지 않는다.
  - `ProfileImageCleanupJobTest`: 24시간 지난 UPLOADING만 지우고, 그 사이 PROCESSING이 된 행은 두고, 23시간짜리도 둔다.
  - `ProfileImageControllerTest`: `POST /api/v1/users/me/profile-images` 201, `POST …/{id}/confirm` 202, `GET …/{id}` 200, `DELETE /api/v1/users/me/profile-image` 204, 비로그인 401, `byteSize` 0·5,242,881은 400.

- [ ] **Step 2: 실패 확인** — 컴파일 실패.

- [ ] **Step 3: 구현**
  - 모든 쓰기는 사용자 행을 먼저 `findByIdForUpdate`로 잠근다(Global Constraints 잠금 순서).
  - 키 생성은 `PostCommandService`처럼 `SecureRandom` 16바이트 hex: 업로드 `uploads/{hex}.jpg`, 공개 `a/{hex}`.
  - 스토리지가 꺼져 있으면(`DisabledMediaStorage`) 기존처럼 `MEDIA_UNAVAILABLE`이 난다. 따로 처리하지 않는다.
  - 정리 작업: `@Scheduled(cron = "0 20 * * * *", zone = "Asia/Seoul")`, `DraftPostCleanupJob`처럼 행을 잠근 뒤 다시 UPLOADING인지 보고 지운다. `app.scheduling.enabled` 규칙(`.claude/rules/api.md`)을 따른다.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 프로필 사진 올리기와 되돌리기 API 추가`

### Task 5: 워커 — 프로필 사진 처리기

**Files:**
- Create: `media/worker/{ProfileProcessJobHandler,ProfileQuarantineJobHandler,ProfileDeleteMediaJobHandler,ProfilePurgeOriginalJobHandler}.java`
- Modify: `media/worker/{JobHandler,MediaJobProcessor,ImageTranscoder,OriginalStore,WorkerConfig}.java`
- Test: `media/worker/{ProfileProcessJobHandlerTest,ProfileQuarantineJobHandlerTest,ProfileDeleteMediaJobHandlerTest,ImageTranscoderTest,OriginalStoreTest,MediaJobProcessorTest,WorkerContextTest}.java`

**Interfaces:**
- Consumes: Task 1, Task 4 `MediaJobScheduler.onProfileImageDropped`.
- Produces:
  - `JobHandler.targetType()` — `default MediaTargetType targetType() { return MediaTargetType.POST_MEDIA; }`. `MediaJobProcessor`는 `(kind, targetType)` 쌍으로 처리기를 찾는다.
  - `ImageTranscoder.transcodeSquare(Path source, Path workDir): SquareImage` → `record SquareImage(Path large, Path small)`
  - `OriginalStore.saveProfile(long profileImageId, InputStream in, Instant now): String` → `profile/yyyy/MM/{id}.jpg`

- [ ] **Step 1: 실패하는 테스트** (`WorkerFixtures`의 가짜 스토리지·가짜 `ProcessRunner` 재사용)
  - `ImageTranscoderTest.squareCropsCenterAndNeverUpscales`: 명령에 `crop='min(iw,ih)':'min(iw,ih)'`와 `scale='min(320,iw)':-1`·`scale='min(96,iw)':-1`, `STRIP_FRAME_METADATA`, `-f jpeg_pipe`가 들어 있다.
  - `OriginalStoreTest.saveProfileUnderProfileDir`.
  - `ProfileProcessJobHandlerTest`:
    - `readyReplacesCurrent`: 지금 사진 A, 새 사진 B 처리 → B READY, `profile_image_id == B`, A REMOVED, A에 `DELETE_MEDIA`·`PURGE_ORIGINAL` 작업, 공개 버킷에 `a/{hex}/l.webp`·`/s.webp`, 업로드 삭제.
    - `olderImageFinishingLateDoesNotReplaceNewer`: B(10)·C(11) 둘 다 PROCESSING일 때 B 처리 → B REMOVED, 방금 올린 B 공개본 삭제, `profile_image_id` 그대로.
    - `resetWhileProcessingLeavesNoImage`: 변환·업로드가 끝난 뒤 커밋 직전에 행이 REMOVED가 돼 있으면(가짜 스토리지 `putPublic` 훅에서 `reset` 호출) → `profile_image_id == null`, 방금 올린 두 공개본 삭제, 새 작업 없음.
    - `withdrawnWhileProcessingLeavesNoImage`: 같은 방식으로 커밋 직전에 사용자가 탈퇴 → 공개본 삭제, READY가 되지 않음.
    - `alreadyReadyOnlyDeletesUpload`: 지난 시도가 READY까지 기록했으면 업로드만 지운다(게시물 처리기와 같음).
  - `MediaJobProcessorTest.profileFailureDoesNotTouchPostMedia`: 같은 id 7의 게시물 사진과 프로필 사진이 있고 프로필 PROCESS가 최종 실패 → 프로필 사진 FAILED, 게시물 사진은 그대로.
  - `ProfileQuarantineJobHandlerTest`: REMOVED 사진의 두 공개본을 `quarantine/`으로 복사 후 삭제하고 두 URL 캐시 비움. 격리본이 이미 있으면 복사를 건너뜀.
  - `ProfileDeleteMediaJobHandlerTest`: 공개·격리·업로드 모두 삭제, 공개된 적이 있으면(원본 경로가 있으면) 캐시 비움.
  - `ProfilePurgeOriginalJobHandlerTest`: 원본 파일을 지우고 `original_path`를 비운다. 파일이 없어도 성공.
  - `WorkerContextTest`: 처리기 9개(게시물 5 + 프로필 4, 프로필은 RELEASE 없음)가 `(kind, targetType)` 중복 없이 등록된다.

- [ ] **Step 2: 실패 확인** — 컴파일 실패.

- [ ] **Step 3: 구현**
  - `ProfileProcessJobHandler`: 행 읽기 → READY면 업로드 삭제만 → PROCESSING 아니면 끝 → 업로드 다운로드 → `saveProfile` → `recordOriginal` → `transcodeSquare` → 두 장 `putPublic`(`image/webp`) → 한 트랜잭션에서 사용자 행 잠금 → 사진 행 잠금 → 세 갈래:
    - 행이 PROCESSING이 아님(되돌리기·탈퇴가 이미 REMOVED로 바꾸고 정리 작업을 만듦): 트랜잭션 밖에서 방금 올린 두 공개본을 지우고 끝(`ProcessJobHandler.deletePublicQuietly`와 같은 방식).
    - 행은 PROCESSING인데 사용자가 활성이 아니거나 더 새 사진이 있음: `markRemoved` + `onProfileImageDropped`(공개본·원본 정리를 작업에 맡김).
    - 그 밖: `markReady()`, 예전 지금 사진이 있으면 `markRemoved` + `onProfileImageDropped`, `useProfileImage(id)`. 커밋 뒤 업로드 삭제.
  - 더 새 사진 판정: `existsByUserIdAndStatusAndIdGreaterThan(userId, PROCESSING, id) || existsByUserIdAndStatusAndIdGreaterThan(userId, READY, id)`.
  - `MediaJobProcessor.fail`: `PROFILE_IMAGE`의 `PROCESS` 최종 실패면 행이 PROCESSING일 때만 `markFailed()`.
  - 프로필 처리기에는 `RELEASE`가 없다. 운영진 복원이 없기 때문이다.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 워커에 프로필 사진 처리 추가`

### Task 6: 운영진 조치와 신고 카드

**Files:**
- Modify: `moderation/dto/{ReportTargetActionRequest,ReportTargetResponse}.java`, `moderation/service/ModerationService.java`
- Test: `moderation/service/ModerationServiceTest.java`, `moderation/controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: Task 4 `MediaJobScheduler.onProfileImageTakenDown`, Task 2 `UserAvatars`.
- Produces:
  - `Action { HIDE, RESTORE, DISMISS, REMOVE_PROFILE_IMAGE, CLEAR_BIO }`
  - `ReportTargetResponse`에 `String userProfileImageUrl`(96px, nullable), `String userBio`(nullable) 추가. USER 대상에만 값.

- [ ] **Step 1: 실패하는 테스트**
  - `removeProfileImageQuarantines`: 지금 사진 A → `profile_image_id == null`, A REMOVED, `(QUARANTINE, PROFILE_IMAGE, A)` 1건, `PURGE_ORIGINAL` 없음, 신고 ACTIONED, `handled_by == adminId`.
  - `clearBio`: `bio == null`, 신고 ACTIONED.
  - `secondActionAfterCloseStillActs`: `REMOVE_PROFILE_IMAGE` 뒤 `CLEAR_BIO` → 예외 없이 `bio == null`.
  - `profileActionsOnlyForUserTarget`: POST·COMMENT 대상에 두 조치 → `COMMON_VALIDATION_FAILED`. USER 대상에 `HIDE`·`RESTORE`도 계속 400.
  - `removeWhenNoImageIsNoop`: 사진이 없어도 신고는 닫힌다.
  - `userTargetShowsImageAndBio`: 목록의 USER 카드에 `userProfileImageUrl`·`userBio`.

- [ ] **Step 2: 실패 확인**.

- [ ] **Step 3: 구현** — 조치는 사용자 행을 `findByIdForUpdate`로 잠근 뒤 한다. 탈퇴한 사용자면 조치 없이 신고만 닫는다.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 운영진 프로필 사진 내리기와 자기소개 지우기 추가`

### Task 7: 탈퇴 연동

**Files:**
- Create: `profile/service/ProfileWithdrawalListener.java`
- Test: `profile/service/ProfileWithdrawalListenerTest.java`

**Interfaces:**
- Consumes: `UserWithdrawnEvent`, Task 4 `MediaJobScheduler.onProfileImageDropped`.

- [ ] **Step 1: 실패하는 테스트** — `withdrawDropsAllProfileImages`: READY 1·PROCESSING 1·REMOVED(운영진이 내린 것) 1·UPLOADING 1인 사용자가 탈퇴 → UPLOADING 행은 삭제, 나머지 셋 모두 REMOVED이고 각각 `DELETE_MEDIA` + `PURGE_ORIGINAL`(30일), 큐의 PROCESS 취소. `users.bio`·`profile_image_id`는 null(`User.withdraw`가 지움).

- [ ] **Step 2: 실패 확인**.

- [ ] **Step 3: 구현** — `@EventListener @Order(Ordered.HIGHEST_PRECEDENCE + 6) @Transactional`(피드 리스너 다음). 사용자 행은 `UserService.withdraw`가 이미 잠갔다.

- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

- [ ] **Step 5: 커밋** — `Feat(api) : 탈퇴 시 프로필 사진 정리 추가`

## 앱

화면 태스크(Task 9·10)는 시작 전에 `frontend-design:frontend-design`, `ui-ux-pro-max:design`, `ui-ux-pro-max:brand`, `taste-skill:taste-skill`, `taste-skill:redesign-skill`을 불러 디자인 계획부터 세운다(사용자 요청). 기존 화면(피드 카드·마이 탭)의 토큰·홀드 모양·글꼴을 이어 쓰는 "redesign - preserve" 기준이다. 애니메이션을 살린다(격자 칸 순차 등장 `FadeInItem`, 사진 교체 크로스페이드, 누를 때 `usePressScale`).

### Task 8: 타입·API·훅·아바타

**Files:**
- Create: `apps/mobile/types/api/profile.ts`, `lib/profile-api.ts`, `lib/upload-profile-image.ts`, `hooks/{use-user-profile,use-profile-image,use-update-bio}.ts`, `components/ui/AuthorLink.tsx`
- Modify: `types/api/{post,moderation}.ts`, `components/ui/Avatar.tsx`, `components/feed/PostCard.tsx`, `components/comment/CommentItem.tsx`, `app/posts/[id]/likes.tsx`, `app/settings/blocks.tsx`, `lib/upload-photos.ts`(`put`·`readFile`을 내보내 재사용)
- Test: `hooks/__tests__/{use-profile-image,use-user-profile}.test.ts(x)`, `components/ui/__tests__/Avatar.test.tsx`, `lib/__tests__/upload-profile-image.test.ts`

**Interfaces:**
- Consumes: Task 2·3·4 응답 모양.
- Produces:
  - 타입: `ProfileResponse`, `ProfileImageUrls`, `GridPostResponse`, `ProfileImageUploadResponse`, `ProfileImageStatus`. `PostAuthorResponse`·`LikerResponse`·`BlockedUserResponse`에 `profileImageUrl: string | null`.
  - `profileApi`: `profile(id)`, `posts(id, cursor?)`, `updateBio(bio)`, `startImage(byteSize)`, `confirmImage(id)`, `imageStatus(id)`, `resetImage()`
  - `prepareProfilePhoto(asset): Promise<PreparedPhoto>` — 1024px, 5MB 넘으면 0.7로 한 번 더, 그래도 넘으면 `PhotoError`
  - `useUserProfile(id)` 쿼리 키 `['users', id, 'profile']`, `useUserPosts(id)` 키 `['users', id, 'posts']`(`useCursorQuery`)
  - `useProfileImage()` → `{ pick(): Promise<void>, reset(): Promise<void>, phase: 'idle' | 'uploading' | 'applying' | 'slow' | 'failed', preview: string | null }`
  - `useUpdateBio()` (`useSubmit` 패턴)
  - `Avatar({ nickname, imageUrl?, size? = 40 })`
  - `AuthorLink({ userId, nickname, imageUrl, withdrawn?, children? })` — 비로그인이면 `/login`, 탈퇴면 누를 수 없음, 아니면 `/users/{id}`

- [ ] **Step 1: 실패하는 테스트**
  - `Avatar.test`: `imageUrl`이 있으면 `expo-image`가 그 주소로 렌더, 없으면 첫 글자. 이미지 `onError`면 첫 글자로 돌아간다.
  - `use-profile-image.test`(가짜 타이머, `profileApi` 모킹):
    - 확정 뒤 `imageStatus`가 PROCESSING → READY면 `['users', 내 id, 'profile']`을 무효화하고 `phase`가 `idle`.
    - FAILED면 `phase === 'failed'`.
    - 30초 동안 PROCESSING이면 `phase === 'slow'`, 더 묻지 않는다(호출 수 16회 이하).
    - REMOVED면 조용히 무효화하고 `idle`.
    - `PROFILE_IMAGE_BUSY` 오류면 그 메시지를 알린다(`notifyError`).
  - `upload-profile-image.test`: `ImageManipulator` 모킹, 1024px로 줄이고 5MB 넘으면 0.7로 다시 저장.

- [ ] **Step 2: 실패 확인** — `pnpm test` FAIL.

- [ ] **Step 3: 구현**
  - 사진 고르기: `launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 1 })`.
  - 폴링 간격 2초, 최대 30초. 화면을 떠나면 멈춘다.
  - `PostCard`·`CommentItem`·좋아요 목록: 아바타와 닉네임을 `AuthorLink`로 감싼다. 차단 목록은 `Avatar`에 사진만 넘기고 누를 수 없다.

- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test` PASS.

- [ ] **Step 5: 커밋** — `Feat(mobile) : 프로필 사진 아바타와 프로필 API 훅 추가`

### Task 9: 프로필·편집 화면

**Files:**
- Create: `apps/mobile/app/users/[id].tsx`, `app/settings/profile.tsx`, `components/profile/{ProfileHeader,ProfileGrid,GridCell,ProfileMenu}.tsx`
- Modify: `app/(tabs)/my.tsx`, `app/_layout.tsx`(스택에 두 화면 등록, 기존 방식대로), `components/feed/InProgressPosts.tsx`(필요하면 props만)
- Test: `components/profile/__tests__/{ProfileGrid,ProfileHeader}.test.tsx`, `app/__tests__/users-profile.test.tsx`(기존 화면 테스트 위치 관례를 따른다)

**Interfaces:**
- Consumes: Task 8 훅·컴포넌트, 기존 `ReportSheet`(`targetType="USER"`), `useBlockUser`, `InProgressPosts`.

- [ ] **Step 1: 실패하는 테스트**
  - `ProfileGrid`: 3열, 칸 정사각, `mediaCount > 1`이면 겹친 아이콘, `thumbnailUrl == null`이면 "숨겨짐", 누르면 `/posts/{id}`.
  - `ProfileHeader`: `bio`가 null이면 자기소개 줄이 없다. `mine`이면 "프로필 편집", 아니면 ⋯ 메뉴.
  - 프로필 화면: 404면 "없는 사용자예요", 빈 격자 문구 둘(남/나), 내 프로필이면 격자 위에 `InProgressPosts`.
  - 편집 화면: 자기소개 151자 입력 시 저장 버튼이 막히고 남은 글자 수가 보인다. 사진을 누르면 "사진 바꾸기 / 기본 이미지로"(사진이 없으면 "기본 이미지로"는 없음). `phase`별 문구.

- [ ] **Step 2: 실패 확인**.

- [ ] **Step 3: 구현**
  - 프로필 화면은 FlashList 하나(머리 = `ProfileHeader`, 항목 = 3개씩 묶은 줄). 아래로 당겨 새로고침.
  - ⋯ 메뉴: "신고하기"(`ReportSheet` USER), "차단하기"(확인 뒤 차단, 성공하면 뒤로 가고 피드 무효화).
  - 마이 탭: 위쪽 아이콘 자리를 내 `Avatar`(사진)로 바꾸고, 그 영역을 누르면 `/users/{내 id}`. 메뉴의 "닉네임 변경"은 그대로 둔다.
  - 편집 화면: 사진 → 닉네임 줄(`/settings/nickname`으로) → 자기소개 `TextField`(여러 줄, 150자 카운터) → 저장.

- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test` PASS. 그리고 CLAUDE.local.md 규칙대로 확인용 시뮬레이터를 새로 띄우고 개발 서버 8090으로 화면을 눈으로 본다(끝나면 끄고 시뮬레이터 삭제).

- [ ] **Step 5: 커밋** — `Feat(mobile) : 프로필 화면과 프로필 편집 추가`

### Task 10: 운영진 신고 카드

**Files:**
- Modify: `apps/mobile/components/admin/ReportTargetRow.tsx`, `types/api/moderation.ts`, `hooks/use-moderation.ts`(필요하면)
- Test: `components/admin/__tests__/ReportTargetRow.test.tsx`

**Interfaces:**
- Consumes: Task 6 `REMOVE_PROFILE_IMAGE`·`CLEAR_BIO`, `userProfileImageUrl`, `userBio`.

- [ ] **Step 1: 실패하는 테스트** — USER 카드: 사진이 있으면 96px 아바타와 "프로필 사진 내리기", 자기소개가 있으면 본문과 "자기소개 지우기", 둘 다 있으면 "둘 다 내리기"(누르면 두 요청이 차례로 나감), 기각은 그대로.

- [ ] **Step 2: 실패 확인**.

- [ ] **Step 3: 구현** — 기존 `ConfirmAction` 패턴. "둘 다 내리기"는 첫 요청이 실패하면 두 번째를 보내지 않는다.

- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm typecheck && pnpm test` PASS.

- [ ] **Step 5: 커밋** — `Feat(mobile) : 신고 카드에 프로필 조치 추가`

## 문서

### Task 11: 설계·규약 문서 반영

**Files:**
- Modify: `docs/architecture.md`(10장 2단계 요약 `profile_images`, 143줄 프로필 사진 문구), `docs/superpowers/specs/2026-11-26-phase2-feed-design.md`(9장 `profile_images` 컬럼, 8장 변환 표의 키 모양, 11장 3번에 이 설계 링크), `docs/superpowers/specs/2026-11-28-phase2-profile-design.md`(위 "설계 문서와 달라지는 점" 반영), `docs/content-todo.md`(개인정보처리방침: 자기소개·프로필 사진 수집·공개 범위·탈퇴 시 삭제, 운영진이 내린 사진 보존 / 임시조치 항목에 프로필 사진 포함 여부), `.claude/skills/api-conventions/SKILL.md`(에러 코드 표에 `USER_BIO_INVALID`·`PROFILE_IMAGE_BUSY`·`PROFILE_IMAGE_UPLOAD_INCOMPLETE`), `.claude/rules/api.md`(미디어 작업은 `target_type`까지 조건에 넣는다는 한 줄)

- [ ] **Step 1: 위 문서를 고친다.**
- [ ] **Step 2: 커밋** — `Docs : 프로필 설계 반영`
