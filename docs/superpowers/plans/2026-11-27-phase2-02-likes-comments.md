# 2단계-02 좋아요·댓글 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 게시물에 좋아요(하트·두 번 탭)를 누르고, 댓글과 한 단계 답글을 달고 지운다. 운영진은 신고된 댓글을 게시물처럼 숨기고 복원하고 기각한다.

**Architecture:**
- 좋아요는 `feed` 패키지에 둔다(`post_likes`). 게시물 행을 `FOR UPDATE`로 잠근 뒤 행을 넣거나 지우고, `like_count`를 ±1 한다.
- 댓글은 새 `comment` 패키지에 둔다. 댓글이 바뀌면 게시물 행을 잠그고 `comment_count`를 다시 센다(`CommentCounter`).
- 읽기와 쓰기의 게시물 접근 규칙은 `PostAccess` 한 곳에서 정한다.
- 신고·운영진 조치(`moderation`)는 COMMENT 분기를 연다. 탈퇴는 새 리스너가 정리한다.
- 앱은 React Query 캐시를 낙관적으로 고치고, 상세 화면을 FlashList(머리 = 게시물 카드, 항목 = 댓글)로 바꾼다.

**Tech Stack:** Spring Boot 4.1.2 / Java 17 / JPA / Flyway(V9), H2(테스트) / MySQL 8.4(운영). Expo SDK 58, React Query 5, FlashList 2.3.2, react-native-gesture-handler ~3.3.0, Reanimated 4.7.1.

**Spec:** `docs/superpowers/specs/2026-11-27-phase2-likes-comments-design.md` (상위: `docs/superpowers/specs/2026-11-26-phase2-feed-design.md`). 규약은 `.claude/rules/{api,mobile,testing}.md`와 `api-conventions` 스킬을 따른다.

**이 계획에 없는 것:** 프로필(격자·프로필 사진), 좋아요·댓글 푸시, 댓글 수정, 댓글 좋아요, 피드 카드 댓글 미리 보기.

## 작업 방식

- 브랜치 `feat/likes-comments`(`dev`에서 땀). 첫 커밋은 설계 문서와 이 계획이다(`Docs : 좋아요·댓글 설계와 구현 계획 추가`).
- 커밋하기 전에 `.gitmessage.txt`를 읽는다. 커밋 메시지 끝에는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`을 넣는다.
- 코드 태스크 순서: 실패하는 테스트 → 실패 확인 → 구현 → 바뀐 앱의 lint·test(web은 build까지) → 커밋.
- API 테스트는 `./gradlew test`로 돌린다(context-mode가 켜져 있으면 `ctx_execute`). 결과는 `apps/api/build/test-results/test/*.xml`로 센다.
- 리뷰 강도는 `medium`을 제안한다. 근거:
  - 잠금 순서와 수 다시 세기는 틀려도 테스트에 잘 안 드러난다.
  - 신고·숨김 경로가 바뀐다.
  - 미디어·인프라 변경은 없다.
  - 실행 전에 승인받는다.

## 설계 문서와 달라지는 점 (계획에서 정함)

- **좋아요 넣기:** `INSERT IGNORE` 대신, 잠근 게시물 아래에서 `existsByPostIdAndUserId`로 확인한 뒤 `save`한다. 게시물 행 잠금이 같은 글의 좋아요를 줄 세우므로, 확인한 뒤 넣어도 경합이 없다. H2 테스트와 MySQL에서 똑같이 동작한다. 그래도 PK가 마지막 방어선이다.
- **답글 목록 URL:** `api-conventions`의 "대댓글은 `parentId` 필드로" 규칙을 따른다. 그래서 `GET /api/v1/posts/{postId}/comments?parentId={id}`로 받는다. 설계 문서의 `/comments/{id}/replies`는 쓰지 않는다.
- **"보이는 답글이 남은" 기준:** 보는 사람과 무관하게 정한다. `deleted_at IS NULL AND status = VISIBLE`인 답글이 하나라도 있으면 남은 것으로 본다. `replyCount`와 같은 기준이라, 차단 때문에 펼쳐 보면 비어 있을 수도 있다(설계 문서 6장 "숫자는 전체 수"와 같은 생각).
- **답글 대상:** 보이는 댓글이나 답글에만 답할 수 있다. 부모가 삭제 자리나 숨김 자리여도, 그 아래의 보이는 답글에는 답할 수 있다.
- **신고 목록의 댓글 항목:** 원 게시물은 기존 `post` 필드에 `PostResponse`로 넣는다. 원 게시물이 숨겨졌으면 사진 URL은 null이다(mapper 규칙 그대로). 댓글 대상에는 격리본 URL을 만들지 않는다. 운영진이 판단할 대상은 댓글 본문이기 때문이다.
- **댓글 신고 가능 상태:** 게시물 신고(`REPORTABLE` = PENDING·VISIBLE)와 같은 기준이다. 원 게시물이 그 상태이고 지워지지 않았어야 한다. 댓글은 `VISIBLE`이고 지워지지 않았어야 한다. 아니면 404.

## Global Constraints

- 댓글 본문: 앞뒤 공백을 잘라 1~500자. 넘거나 비면 `COMMON_VALIDATION_FAILED`(400).
- 페이지 크기: 댓글·답글·좋아요 목록 모두 `size` 기본 20, 1~50(`@Min(1) @Max(50)`). 피드처럼 커서 방식이다.
- 정렬:
  - 최상위 댓글과 답글: `id` 오름차순(오래된 순).
  - 좋아요 누른 사람: `created_at` 내림차순, 같으면 `user_id` 내림차순.
- 커서: 기존 `com.theclimbingnight.feed.Cursor(Instant, long)`를 쓴다. 댓글은 `(createdAt, id)`를 담고 조건은 `id > :cursorId`만 쓴다. 좋아요는 `(createdAt, userId)`를 담는다.
- 자동 숨김: 서로 다른 3명이 신고하면(`ReportService.AUTO_HIDE_REPORTERS`) 숨긴다. 댓글에는 초상권 1건 규칙이 없다. 댓글 신고에 `PORTRAIT_PRIVACY`를 쓰면 400이다.
- 잠금 순서: 게시물 행 → 댓글 행. 여러 게시물을 잠글 때는 게시물 id 오름차순.
- 권한:
  - 목록·작성·좋아요는 `@RequireRole(Role.USER)`라 비로그인은 401 `AUTH_REQUIRED`다.
  - 댓글 삭제 권한이 없으면 403 `AUTH_FORBIDDEN`이다.
  - 볼 수 없는 대상은 404 `COMMON_NOT_FOUND`다.
- 새 서비스 클래스에는 `@Transactional(readOnly = true)`를 클래스에 걸고, 쓰기 메서드에만 `@Transactional`을 건다. `ServiceTransactionConventionTest.PACKAGES`에 `com.theclimbingnight.comment`를 추가한다.
- 앱 문구:
  - 삭제 자리: "삭제된 댓글입니다"
  - 숨김 자리: "숨겨진 댓글입니다"
  - 내 숨긴 댓글: "운영 정책에 따라 숨겨졌어요"
  - 탈퇴한 답글 대상: "@알 수 없음"
  - 답글 입력 머리: "{닉네임}님에게 답글"
- 새 환경변수가 없다.

## Review Focus

1. **좋아요 연타:** 같은 사람이 하트를 빠르게 여러 번 누른다. 그래도 최종 상태가 마지막 탭과 같아야 하고, `like_count`는 실제 행 수와 같아야 한다(Task 2 동시성 테스트, Task 8 훅 테스트).
2. **보이는 답글이 남은 부모를 지운 뒤 그 답글까지 지운다:** 부모 자리가 목록에서 사라지고 `comment_count`는 0이어야 한다(Task 4 저장소 테스트).
3. **차단한 사람의 최상위 댓글에 달린 남의 답글:** 목록에서 함께 빠진다. `parentId`로 직접 요청해도 404다(Task 4·5).
4. **자동 숨김된 댓글을 기각한다:** 댓글이 다시 보이고 `comment_count`가 다시 늘어야 한다. 운영진이 확정한 숨김(`HIDDEN`)은 기각해도 그대로다(Task 6).
5. **탈퇴한 사람이 여러 게시물에 남긴 좋아요·댓글:** 모든 게시물의 두 수가 실제와 같아진다. 그 사람에게 답한 답글은 "@알 수 없음"으로 보인다(Task 7).

---

## API

### Task 1: 스키마·엔티티·저장소 뼈대

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V9__likes_comments.sql`
- Create: `apps/api/src/main/java/com/theclimbingnight/feed/entity/PostLike.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/feed/repository/PostLikeRepository.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/comment/entity/Comment.java`, `comment/entity/CommentStatus.java`
- Create: `apps/api/src/main/java/com/theclimbingnight/comment/repository/CommentRepository.java`
- Modify: `apps/api/src/main/java/com/theclimbingnight/feed/entity/Post.java`
- Modify: `apps/api/src/test/java/com/theclimbingnight/ServiceTransactionConventionTest.java` (PACKAGES)
- Test: `apps/api/src/test/java/com/theclimbingnight/feed/repository/PostLikeRepositoryTest.java`, `apps/api/src/test/java/com/theclimbingnight/comment/entity/CommentTest.java`

**Interfaces:**
- Produces:
  - `V9`:
    - `comments(id, post_id FK, author_id FK, parent_id FK nullable, reply_to_user_id FK nullable, body VARCHAR(500), status VARCHAR(16), deleted_at, created_at, updated_at)`
    - 인덱스 `idx_comments_post_parent(post_id, parent_id, id)`, `idx_comments_author(author_id)`
    - `post_likes(post_id FK, user_id FK, created_at)`, PK `(post_id, user_id)`
    - 인덱스 `idx_post_likes_post_created(post_id, created_at, user_id)`, `idx_post_likes_user(user_id)`
    - 문자셋·엔진은 V8과 같다.
  - `CommentStatus { VISIBLE, HIDDEN_PENDING, HIDDEN }`
  - `Comment extends BaseTimeEntity`:
    - getter: `id, postId, authorId, parentId(Long), replyToUserId(Long), body, status, deletedAt`
    - `static Comment create(long postId, long authorId, Long parentId, Long replyToUserId, String body)`
    - `void hide(boolean pending)`: pending이면 `HIDDEN_PENDING`, 아니면 `HIDDEN`
    - `void restore()`: `VISIBLE`
    - `void softDelete(Instant now)`
    - `boolean isShown()`: `deletedAt == null && status == VISIBLE`
    - `boolean isHidden()`
  - `PostLike`: `@IdClass(PostLike.Key)`. 필드는 `postId, userId, createdAt`. `static PostLike of(long postId, long userId, Instant now)`
  - `Post`:
    - `void addLike()`
    - `void removeLike()`: 0 아래로 내려가지 않는다.
    - `void updateLikeCount(int count)`
    - `void updateCommentCount(int count)`
  - `PostLikeRepository extends JpaRepository<PostLike, PostLike.Key>`:
    - `boolean existsByPostIdAndUserId(long postId, long userId)`
    - `@Modifying int deleteLike(long postId, long userId)`
    - `long countByPostId(long postId)`
    - `List<Long> findLikedPostIds(long userId, Collection<Long> postIds)`
    - `List<Long> findPostIdsByUserId(long userId)`
    - `List<PostLike> findLikers(long postId, long viewerId, Instant cursorCreatedAt, Long cursorUserId, Pageable limit)`: 차단 관계 양방향을 뺀다. 정렬은 Global Constraints와 같다.
  - `CommentRepository`: 이 태스크에서는 `JpaRepository<Comment, Long>`와 `@Lock(PESSIMISTIC_WRITE) Optional<Comment> findByIdForUpdate(long id)`만 만든다. 목록 쿼리는 Task 4에서 넣는다.

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentTest`
    - `숨기면_대기_여부에_따라_상태가_바뀐다`
    - `복원하면_VISIBLE이다`
    - `지운_댓글은_보이지_않는다`
  - `PostLikeRepositoryTest`(`@DataJpaTest`, `PostRepositoryTest`와 같은 설정)
    - `같은_사람의_좋아요는_한_번만_저장된다`: 두 번째 `saveAndFlush`가 `DataIntegrityViolationException`이다.
    - `좋아요_누른_사람은_최신순이고_차단_관계는_빠진다`: 3명이 누르고 그중 1명이 viewer와 차단 관계다. 결과 2명이 `created_at` 내림차순으로 나온다.
    - `좋아요_목록_커서는_그_뒤만_준다`
    - `내가_좋아요한_게시물_id만_돌려준다`
- [ ] **Step 2:** `./gradlew test --tests '*PostLikeRepositoryTest' --tests '*CommentTest'` → 컴파일 실패.
- [ ] **Step 3:** 마이그레이션, 엔티티, 저장소, `Post` 메서드를 만든다. `ServiceTransactionConventionTest.PACKAGES`에 `com.theclimbingnight.comment`를 추가한다.
- [ ] **Step 4:** 같은 명령 → PASS. `./gradlew test` 전체 → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 좋아요·댓글 테이블과 엔티티 추가`

### Task 2: 게시물 접근 규칙과 좋아요 API

**Files:**
- Create: `apps/api/src/main/java/com/theclimbingnight/feed/service/PostAccess.java`
- Create: `feed/service/PostLikeService.java`, `feed/controller/PostLikeController.java`, `feed/dto/LikeResponse.java`, `feed/dto/LikerResponse.java`
- Modify:
  - `feed/service/PostQueryService.java`: `detail`이 `PostAccess.readable`을 쓴다. 응답에 `likedByMe`를 채운다.
  - `feed/service/PostCommandService.java`, `feed/service/PostResponseMapper.java`, `feed/dto/PostResponse.java`
  - `moderation/service/ModerationService.java`: mapper 호출에 `false`를 넘긴다.
- Test:
  - `feed/service/PostLikeServiceTest.java`(Mockito)
  - `feed/service/PostLikeConcurrencyTest.java`(`@SpringBootTest`, `PostPublishConcurrencyTest` 방식)
  - `feed/controller/PostLikeControllerTest.java`(`@WebMvcTest`)
  - 기존 `PostQueryServiceTest`·`PostResponseMapper` 관련 테스트 보정

**Interfaces:**
- Consumes: Task 1
- Produces:
  - `PostAccess`(`@Component`):
    - `Post readable(long viewerId, long postId)`: `detail`의 로그인 사용자 규칙을 그대로 옮긴다. 지운 글은 404다. 작성자 본인에게는 DRAFT만 404다. 남에게는 VISIBLE이 아니거나 차단 관계면 404다.
    - `Post lockWritable(long viewerId, long postId)`: `findByIdForUpdate`로 잠근다. 지워지지 않았고 `VISIBLE`이고 차단 관계가 아니어야 한다. 아니면 404.
    - `Post lockLive(long postId)`: 잠근다. 지워지지 않았어야 한다. 아니면 404.
  - `PostResponse`에 `boolean likedByMe`를 `commentCount` 뒤에 넣는다.
  - `PostResponseMapper.toResponse(Post, String, List<PostMedia>, Long viewerId, boolean likedByMe)`
  - `record LikeResponse(int likeCount, boolean likedByMe)`
  - `record LikerResponse(long userId, String nickname)`
  - `PostLikeService`:
    - `LikeResponse like(long userId, long postId)`: `lockWritable` → 없으면 저장하고 `addLike`
    - `LikeResponse unlike(long userId, long postId)`: `lockLive` → `deleteLike == 1`이면 `removeLike`
    - `CursorResponse<LikerResponse> likers(long viewerId, long postId, String cursor, int size)`: `readable`을 먼저 확인한다.
  - `PostLikeController`(`@RequireRole(Role.USER)`):
    - `PUT /api/v1/posts/{id}/like`
    - `DELETE /api/v1/posts/{id}/like`: 둘 다 200 `LikeResponse`
    - `GET /api/v1/posts/{id}/likes?cursor=&size=20`

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `PostLikeServiceTest`
    - `처음_누르면_저장하고_수가_1_는다`
    - `이미_누른_글을_다시_눌러도_수는_그대로다`
    - `누르지_않은_글을_취소해도_수는_그대로다`
    - `보이지_않는_글에는_누를_수_없다`(PENDING·HIDDEN → 404)
    - `차단_관계인_사람_글에는_누를_수_없다`
    - `숨겨진_글에서도_좋아요는_끌_수_있다`
    - `지운_글은_끌_수_없다`(404)
  - `PostLikeConcurrencyTest`
    - `서로_다른_10명이_동시에_누르면_수가_10이다`
    - `같은_사람이_켜기와_끄기를_동시에_여러_번_보내도_수는_행_수와_같다`
    - 두 테스트 모두 `ExecutorService`와 `CountDownLatch`로 보내고, 끝나면 `like_count == countByPostId`인지 본다.
  - `PostLikeControllerTest`
    - `비로그인은_401이다`
    - `켜기는_PUT이고_LikeResponse를_준다`
    - `size가_50을_넘으면_400이다`
  - `PostQueryServiceTest`
    - `내가_누른_글은_likedByMe가_true다`
    - `비로그인은_likedByMe가_false다`
- [ ] **Step 2:** 새 테스트만 돌린다 → FAIL.
- [ ] **Step 3:** 구현한다. `PostQueryService.toResponses`는 viewer가 있으면 `findLikedPostIds`를 한 번 불러 `Set`으로 만든다. `PostCommandService`는 응답을 만들 때 `existsByPostIdAndUserId`로 채운다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 게시물 좋아요와 누른 사람 목록 추가`

### Task 3: 댓글 쓰기·지우기와 수 다시 세기

**Files:**
- Create:
  - `comment/service/CommentCounter.java`
  - `comment/service/CommentCommandService.java`
  - `comment/dto/CommentCreateRequest.java`
  - `comment/dto/CommentResponse.java`
  - `comment/dto/CommentDisplay.java`
- Modify: `comment/repository/CommentRepository.java`
- Test: `comment/service/CommentCommandServiceTest.java`(Mockito), `comment/dto/CommentRequestValidationTest.java`, `comment/repository/CommentRepositoryTest.java`(count만)

**Interfaces:**
- Consumes: Task 2의 `PostAccess.lockWritable`·`lockLive`, Task 1의 `Comment`·`Post.updateCommentCount`
- Produces:
  - `CommentRepository.countShownByPostId(long postId)`: 지워지지 않았고 VISIBLE인 댓글과 답글을 모두 센다.
  - `CommentCounter`(`@Component`): `void recount(Post lockedPost)`. 호출하는 쪽이 게시물 행을 잠근 상태여야 한다.
  - `enum CommentDisplay { VISIBLE, HIDDEN_MINE, DELETED, HIDDEN }`
    - `VISIBLE`: 보이는 댓글
    - `HIDDEN_MINE`: 숨겨진 내 댓글. 본문과 안내를 보인다.
    - `DELETED`: 삭제 자리
    - `HIDDEN`: 숨김 자리
  - `record CommentResponse(long id, Long parentId, PostAuthorResponse author, String body, String replyToNickname, OffsetDateTime createdAt, CommentDisplay display, int replyCount, boolean mine, boolean deletable)`
    - 자리(`DELETED`·`HIDDEN`)는 `author`와 `body`가 null이다.
    - `replyToNickname`은 답글에만 있다. 대상이 탈퇴했으면 null이다.
  - `record CommentCreateRequest(@NotNull @Size(max = 500) String body, Long replyToCommentId)`: `@AssertTrue isBodyPresent()`로 `body.strip()`이 비었는지 본다.
  - `CommentCommandService`:
    - `CommentResponse create(long userId, long postId, CommentCreateRequest request)`:
      1. `lockWritable`
      2. 답글이면 대상 댓글을 확인한다. 같은 게시물이어야 하고, `isShown()`이어야 하고, 대상 작성자와 차단 관계가 아니어야 한다. 아니면 404.
      3. `parentId = 대상.parentId ?? 대상.id`, `replyToUserId = 대상.authorId`
      4. 저장하고 `recount`
    - `void delete(long userId, Role role, long commentId)`:
      1. 댓글을 찾는다(지운 것은 404).
      2. `lockLive(comment.postId)` → `findByIdForUpdate`로 다시 확인한다.
      3. 작성자, 게시물 작성자, ADMIN이 아니면 403.
      4. `softDelete`하고 `recount`

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentCommandServiceTest`
    - `댓글을_달면_수를_다시_센다`
    - `본문은_앞뒤_공백을_자른다`
    - `답글에_답하면_같은_부모_아래에_붙고_대상은_답한_댓글_작성자다`
    - `다른_게시물_댓글에는_답할_수_없다`
    - `지웠거나_숨긴_댓글에는_답할_수_없다`
    - `차단_관계인_사람_댓글에는_답할_수_없다`
    - `보이지_않는_글에는_댓글을_달_수_없다`
    - `게시물_작성자는_남의_댓글을_지울_수_있다`
    - `관리자는_어떤_댓글이든_지울_수_있다`
    - `권한_없는_사람이_지우면_403이다`
    - `지운_댓글을_다시_지우면_404다`
  - `CommentRequestValidationTest`(Validator)
    - `공백뿐인_본문은_거절한다`
    - `500자는_되고_501자는_거절한다`
  - `CommentRepositoryTest`
    - `수는_지우거나_숨긴_댓글을_빼고_답글을_포함한다`
- [ ] **Step 2:** 새 테스트 → FAIL.
- [ ] **Step 3:** 구현한다. 응답의 `replyCount`는 새 댓글이라 0이다. `author` 닉네임은 `UserRepository`에서 읽는다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 댓글 작성·삭제와 댓글 수 다시 세기 추가`

### Task 4: 댓글·답글 목록 쿼리와 보이기 규칙

**Files:**
- Create: `comment/service/CommentQueryService.java`, `comment/repository/ReplyCount.java`
- Modify: `comment/repository/CommentRepository.java`
- Test: `comment/repository/CommentRepositoryTest.java`(목록), `comment/service/CommentQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 2 `PostAccess.readable`, Task 3 `CommentResponse`·`CommentDisplay`
- Produces:
  - `CommentRepository.findTopLevel(long postId, long viewerId, Long cursorId, Pageable limit)`: 최상위 댓글을 `id` 오름차순으로 준다. 포함 조건:
    1. 작성자가 viewer와 차단 관계가 아니고,
    2. 다음 중 하나여야 한다.
       - (가) `isShown`
       - (나) 작성자가 viewer이고, 지워지지 않았고, 숨김 상태
       - (다) 지워졌거나 숨김 상태이고, 보이는 답글(`deleted_at IS NULL AND status = VISIBLE`)이 하나 이상 있다.
  - `CommentRepository.findReplies(long parentId, long viewerId, Long cursorId, Pageable limit)`: 답글을 `id` 오름차순으로 준다. 포함 조건:
    - 작성자가 차단 관계가 아니고 `isShown`이거나,
    - 작성자가 viewer이고, 지워지지 않았고, 숨김 상태
  - `List<ReplyCount> countShownReplies(Collection<Long> parentIds)`
    - `record ReplyCount(Long parentId, Long count)`
  - `CommentQueryService`:
    - `CursorResponse<CommentResponse> list(long viewerId, Role role, long postId, Long parentId, String cursor, int size)`
      - `readable(viewerId, postId)`
      - `parentId`가 있으면 부모를 확인한다. 같은 게시물의 최상위 댓글이어야 하고, 부모 작성자와 차단 관계가 아니어야 한다. 아니면 404.
    - `display` 정하기:
      - `isShown`이면 `VISIBLE`
      - 내 숨긴 댓글이면 `HIDDEN_MINE`
      - 지웠으면 `DELETED`
      - 그 밖의 숨김이면 `HIDDEN`
    - `deletable`: 자리가 아니고, 내 댓글이거나 게시물 작성자이거나 ADMIN
    - `replyToNickname`: 탈퇴한 사용자(`!User.isActive()`)면 null

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentRepositoryTest`(`@DataJpaTest`)
    - `최상위_댓글은_오래된_순이고_커서_뒤만_준다`
    - `보이는_답글이_남은_지운_댓글은_자리로_남는다`
    - `답글이_모두_지워지면_지운_부모도_빠진다`
    - `숨긴_댓글은_작성자에게만_나온다`
    - `차단_관계인_사람의_최상위_댓글은_빠진다`
    - `답글_수는_보이는_답글만_센다`
  - `CommentQueryServiceTest`
    - `자리는_작성자와_본문이_없다`
    - `숨긴_내_댓글은_HIDDEN_MINE이다`
    - `탈퇴한_사람에게_단_답글은_대상_닉네임이_null이다`
    - `차단_관계인_부모의_답글을_요청하면_404다`
    - `다른_게시물의_parentId는_404다`
    - `숨겨진_내_글의_댓글은_작성자가_볼_수_있다`
    - `남의_숨겨진_글_댓글은_404다`
- [ ] **Step 2:** 새 테스트 → FAIL.
- [ ] **Step 3:** 구현한다. 차단 조건은 `PostRepository.findFeed`의 `not exists (UserBlock ...)` 형태를 그대로 쓴다. 한 페이지의 작성자·답글 대상 닉네임은 `findAllById` 한 번으로 모은다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 댓글·답글 목록과 삭제·숨김 자리 규칙 추가`

### Task 5: 댓글 컨트롤러

**Files:**
- Create: `comment/controller/CommentController.java`
- Test: `comment/controller/CommentControllerTest.java`(`@WebMvcTest`), `apps/api/src/test/java/com/theclimbingnight/CommentFlowIntegrationTest.java`(`FeedFlowIntegrationTest` 방식)

**Interfaces:**
- Consumes: Task 3·4 서비스
- Produces: 모두 `@RequireRole(Role.USER)`다.
  - `GET /api/v1/posts/{postId}/comments?parentId=&cursor=&size=20` → `CursorResponse<CommentResponse>`
  - `POST /api/v1/posts/{postId}/comments` → 201 `CommentResponse`
  - `DELETE /api/v1/comments/{id}` → 204

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentControllerTest`
    - `비로그인은_목록도_401이다`
    - `작성은_201이다`
    - `size_0은_400이다`
    - `본문이_501자면_400이다`
  - `CommentFlowIntegrationTest`
    - `댓글과_답글을_달고_부모를_지우면_자리가_남고_수는_답글만_센다`: 실제 HTTP로 작성 → 답글 → 부모 삭제 → 목록(DELETED 자리, `replyCount` 1) → 상세 `commentCount` 1
    - `차단하면_그_사람의_댓글과_그_아래_답글이_빠진다`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 댓글 API 추가`

### Task 6: 댓글 신고와 운영진 조치

**Files:**
- Modify:
  - `moderation/dto/ReportCreateRequest.java`: `isSupportedTarget`을 지우고 `isReasonAllowedForTarget`를 넣는다.
  - `moderation/service/ReportService.java`: `submitComment`
  - `moderation/service/ModerationService.java`: `act`와 `list`의 COMMENT 분기
  - `moderation/dto/ReportTargetResponse.java`: `comment` 필드
- Create: `moderation/dto/ReportCommentResponse.java`
- Test: `ReportServiceTest`, `ModerationServiceTest`, `ReportControllerTest`, `ReportCreateRequest` 검증 테스트

**Interfaces:**
- Consumes: Task 1 `CommentRepository.findByIdForUpdate`, Task 3 `CommentCounter.recount`
- Produces:
  - `ReportCreateRequest.isReasonAllowedForTarget()`: `!(targetType == COMMENT && reason == PORTRAIT_PRIVACY)`. 메시지는 "댓글은 초상권 사유로 신고할 수 없습니다."
  - `ReportService.submitComment`:
    1. 댓글을 찾는다(지운 것은 404).
    2. 게시물을 `findByIdForUpdate`로 잠근다. 지워지지 않았고 `REPORTABLE`이어야 한다.
    3. 댓글을 `findByIdForUpdate`로 잠근다. `isShown`이어야 한다.
    4. 내 댓글이면 `REPORT_SELF`, 차단 관계면 404
    5. `save`한다. 3명째면 `hide(true)`하고 `recount`
  - `ModerationService.act` COMMENT:
    - `lockedComment(id)`: 댓글을 찾고, 게시물 `lockLive` 기준(지운 글이면 404)으로 잠근 뒤, 댓글 `findByIdForUpdate`로 다시 확인한다(지운 댓글이면 404).
    - `HIDE`: 이미 `HIDDEN`이면 그대로 둔다. 아니면 `hide(false)`하고 `recount`. 신고는 ACTIONED.
    - `RESTORE`: 숨김 상태면 `restore`하고 `recount`. 신고는 DISMISSED.
    - `DISMISS`: 게시물·댓글이 살아 있고 `HIDDEN_PENDING`이면 `restore`하고 `recount`. 404로 막지 않는다. 신고는 DISMISSED.
  - `record ReportCommentResponse(long id, long postId, PostAuthorResponse author, String body)`
  - `ReportTargetResponse`에 `ReportCommentResponse comment`를 `userNickname` 앞에 넣는다.
    - COMMENT 대상이면 `post`에 원 게시물 `PostResponse`(`viewerId` null, `likedByMe` false)를 넣는다.
    - `targetStatus`는 댓글이나 원 게시물이 지워졌으면 `DELETED`, 아니면 `CommentStatus` 이름이다.
    - 지워졌으면 `comment`와 `post`는 null이다.

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `ReportServiceTest`
    - `댓글은_세_명이_신고하면_자동으로_숨기고_수를_다시_센다`
    - `댓글은_초상권_신고_한_건으로_숨기지_않는다`(검증 단계에서 400)
    - `내_댓글은_신고할_수_없다`
    - `지운_댓글이나_보이지_않는_글의_댓글은_404다`
  - `ModerationServiceTest`
    - `댓글을_숨기면_HIDDEN이고_수를_다시_센다`
    - `자동_숨김_댓글을_기각하면_다시_보이고_수가_는다`
    - `확정_숨김_댓글은_기각해도_그대로다`
    - `지운_글의_댓글은_숨김이_404이고_기각은_된다`
    - `숨겨진_글의_댓글도_숨기고_복원할_수_있다`
    - `신고_목록의_댓글_항목은_본문과_원_게시물을_준다`
  - `ReportControllerTest`: `댓글_신고는_201이다`(기존 COMMENT 거절 테스트는 바꾼다)
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다. 댓글 조치는 `media_jobs`를 만들지 않는다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 댓글 신고와 운영진 숨김·복원·기각 추가`

### Task 7: 탈퇴 정리

**Files:**
- Create: `comment/service/ReactionWithdrawalListener.java`
- Modify: `comment/repository/CommentRepository.java`, `feed/repository/PostLikeRepository.java`
- Test: `comment/service/ReactionWithdrawalListenerTest.java`(Mockito), `WithdrawalDataIntegrationTest`에 경우 추가

**Interfaces:**
- Consumes: Task 1·3
- Produces:
  - `CommentRepository.findLivePostIdsByAuthorId(long authorId)`: 중복 없음
  - `@Modifying int softDeleteByPostIdAndAuthorId(long postId, long authorId, Instant now)`
  - `PostLikeRepository.deleteLike`(Task 1)
  - `ReactionWithdrawalListener`:
    - `@EventListener @Order(Ordered.HIGHEST_PRECEDENCE + 6) @Transactional on(UserWithdrawnEvent)`
    - 좋아요한 게시물 id와 댓글 단 게시물 id를 합쳐 오름차순으로 돈다.
    - 게시물마다 `findByIdForUpdate`로 잠그고(지운 글도 잠근다) 좋아요를 지우고 댓글을 소프트 삭제한다.
    - 지워지지 않은 글이면 `updateLikeCount(countByPostId)`와 `recount`를 한다.

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `ReactionWithdrawalListenerTest`
    - `여러_게시물의_좋아요와_댓글을_지우고_두_수를_다시_센다`
    - `게시물은_id_오름차순으로_잠근다`(InOrder)
  - `WithdrawalDataIntegrationTest`
    - `탈퇴한_사람의_댓글에_남의_답글이_있으면_삭제_자리로_남는다`
    - `탈퇴한_사람에게_단_답글은_대상_닉네임이_비어_있다`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** 커밋 `Feat(api) : 탈퇴 시 좋아요·댓글 정리 추가`

## 앱

공통 규칙:
- 앱 태스크마다 `cd apps/mobile && pnpm lint && pnpm typecheck && pnpm test`가 통과해야 한다.
- 라우트를 추가하면 `.expo/types`를 다시 만든다(`.claude/rules/mobile.md`).
- 화면 디자인은 화면 작업 디자인 스킬을 쓰고 애니메이션을 살린다.
- 애니메이션은 `transform`·`opacity`만 움직이고, `useReducedMotion`이면 끝 상태로 둔다.

### Task 8: 좋아요(하트·두 번 탭)

**Files:**
- Modify: `apps/mobile/types/api/post.ts`, `apps/mobile/lib/post-api.ts`, `apps/mobile/test-utils/posts.ts`
- Create: `apps/mobile/hooks/use-like-post.ts`, `apps/mobile/components/feed/LikeButton.tsx`, `apps/mobile/components/feed/HeartBurst.tsx`
- Modify: `apps/mobile/components/feed/PostCard.tsx`, `apps/mobile/components/feed/MediaCarousel.tsx`
- Test: `apps/mobile/hooks/__tests__/use-like-post.test.ts`, `apps/mobile/components/feed/__tests__/PostCard.test.tsx`

**Interfaces:**
- Consumes: Task 2 API
- Produces:
  - `PostResponse.likedByMe: boolean`
  - `LikeResponse { likeCount: number; likedByMe: boolean }`
  - `LikerResponse { userId: number; nickname: string }`
  - `likePost(id)`, `unlikePost(id)`, `fetchLikers(id, cursor, size)`
  - `useLikePost(): { setLiked(post: PostResponse, liked: boolean): void }`
    - `['posts']` 아래 모든 캐시(피드 무한 목록 페이지, `['posts', id]`)를 바로 고친다.
    - 실패하면 되돌린다.
    - 같은 글의 요청이 진행 중이면 마지막 의도만 보낸다(앞 요청이 끝난 뒤 최종 상태와 다르면 한 번 더 보낸다).
    - 서버 응답의 `likeCount`로 맞춘다.
  - `MediaCarousel`에 `onDoubleTap?: () => void`를 추가한다.
    - `Gesture.Exclusive(doubleTap, singleTap)`로 한 번 탭(`onPress`)과 겹치지 않게 한다.
    - 두 번 탭하면 `HeartBurst`를 보인다.
  - `PostCard`:
    - 하트를 `LikeButton`으로 바꾼다. 켜짐은 채운 하트와 강조색, 누를 때 크기 튕김.
    - 내 글이 `VISIBLE`이 아니면(`status`가 있고 `VISIBLE`이 아님) 켜진 하트는 끌 수만 있고, 꺼진 하트와 두 번 탭은 막는다.
    - 비로그인이면 `/login`으로 보낸다.
    - 사진을 두 번 탭하면 `setLiked(post, true)`만 한다.
    - 말풍선과 수를 누르면 `/posts/{id}?focus=comments`로 간다.
    - "좋아요 n개" 줄은 상세에서만 보인다(Task 9).

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `use-like-post.test.ts`
    - `it("updates feed and detail caches before the request resolves")`
    - `it("rolls back when the request fails")`
    - `it("sends only the final state after rapid toggles")`
  - `PostCard.test.tsx`
    - `it("toggles the heart and shows the new count")`
    - `it("likes but never unlikes on double tap")`
    - `it("sends signed-out users to login when the heart is pressed")`
    - `it("opens the detail at comments from the comment button")`
    - `it("only allows unliking on my hidden post")`
- [ ] **Step 2:** `pnpm test -- use-like-post PostCard` → FAIL.
- [ ] **Step 3:** 구현한다. 아이콘은 `phosphor-react-native/src/icons/Heart`를 쓴다.
- [ ] **Step 4:** lint·typecheck·test → PASS.
- [ ] **Step 5:** 커밋 `Feat(mobile) : 좋아요 버튼과 두 번 탭 좋아요 추가`

### Task 9: 좋아요 누른 사람 목록

**Files:**
- Create: `apps/mobile/app/posts/[id]/likes.tsx`, `apps/mobile/hooks/use-likers.ts`
- Modify: `apps/mobile/app/posts/[id]/index.tsx`("좋아요 n개" 줄)
- Test: `apps/mobile/__tests__/post-likes.test.tsx`

**Interfaces:**
- Consumes: Task 8 `fetchLikers`
- Produces:
  - `useLikers(postId)`: `useCursorQuery(['posts', postId, 'likes'], …, true, (l) => l.userId)`
  - 화면은 `Avatar`와 닉네임 목록이다. 비었으면 "아직 좋아요가 없어요".
  - 상세에서 `likeCount > 0`일 때 "좋아요 n개"를 누르면 이 화면으로 간다.

- [ ] **Step 1:** 실패하는 테스트 `post-likes.test.tsx`를 쓴다.
  - `it("lists likers newest first")`
  - `it("loads the next page at the end")`
  - `it("shows an empty message")`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** lint·typecheck·test → PASS.
- [ ] **Step 5:** 커밋 `Feat(mobile) : 좋아요 누른 사람 목록 화면 추가`

### Task 10: 상세 화면 댓글 목록과 답글 펼치기

**Files:**
- Create:
  - `apps/mobile/types/api/comment.ts`
  - `apps/mobile/lib/comment-api.ts`
  - `apps/mobile/hooks/use-comments.ts`
  - `apps/mobile/components/comment/CommentItem.tsx`
  - `apps/mobile/components/comment/ReplyList.tsx`
- Modify: `apps/mobile/app/posts/[id]/index.tsx`(ScrollView → FlashList)
- Test: `apps/mobile/__tests__/post-detail.test.tsx`(경우 추가), `apps/mobile/components/comment/__tests__/CommentItem.test.tsx`

**Interfaces:**
- Consumes: Task 5 API
- Produces:
  - `CommentResponse`·`CommentDisplay`: API DTO와 같은 이름
  - `fetchComments(postId, { parentId, cursor, size })`, `createComment(postId, { body, replyToCommentId })`, `deleteComment(id)`
  - `useComments(postId, enabled)`: 키 `['posts', postId, 'comments']`
  - `useReplies(postId, parentId, enabled)`: 키 `['posts', postId, 'comments', parentId]`
  - 상세 화면:
    - FlashList를 쓴다. `ListHeaderComponent`는 숨김 안내, `PostCard`, "좋아요 n개" 순서다. 항목은 최상위 `CommentItem`이다.
    - `onEndReached`에서 다음 페이지를 받는다.
    - `focus=comments`로 들어오면 첫 댓글 위치로 스크롤한다.
    - 비로그인이면 댓글 자리에 로그인 안내를 보이고 목록을 요청하지 않는다.
  - `CommentItem`:
    - `display`마다 문구가 다르다(Global Constraints).
    - "답글 n개 보기"를 누르면 `ReplyList`를 펼친다. 펼치면서 답글이 아래로 페이드인한다.
    - 답글 앞에는 `@{replyToNickname ?? '알 수 없음'}`을 붙인다.

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentItem.test.tsx`
    - `it("shows the deleted placeholder without author")`
    - `it("shows the policy notice on my hidden comment")`
    - `it("prefixes replies with @unknown when the target left")`
  - `post-detail.test.tsx`
    - `it("lists comments oldest first under the post")`
    - `it("expands replies on demand")`
    - `it("asks signed-out users to log in instead of loading comments")`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** lint·typecheck·test → PASS.
- [ ] **Step 5:** 커밋 `Feat(mobile) : 상세 화면 댓글 목록과 답글 펼치기 추가`

### Task 11: 댓글 입력·답글 달기·삭제·신고

**Files:**
- Create: `apps/mobile/components/comment/CommentInput.tsx`, `apps/mobile/components/comment/CommentMenu.tsx`
- Modify:
  - `apps/mobile/hooks/use-comments.ts`(`useCreateComment`, `useDeleteComment`)
  - `apps/mobile/app/posts/[id]/index.tsx`
  - `apps/mobile/components/feed/ReportSheet.tsx`(COMMENT면 초상권을 뺀다)
- Test: `apps/mobile/components/comment/__tests__/CommentInput.test.tsx`, `CommentMenu.test.tsx`, `apps/mobile/components/feed/__tests__/ReportSheet.test.tsx`, `apps/mobile/__tests__/post-detail.test.tsx`

**Interfaces:**
- Consumes: Task 10
- Produces:
  - `CommentInput({ postId, replyTo: CommentResponse | null, onCancelReply })`:
    - 화면 아래에 붙는다. 키보드가 올라오면 함께 올라간다(`Screen`의 `KeyboardAvoidingView` 방식, FlashList `automaticallyAdjustKeyboardInsets`).
    - 1~500자일 때만 "게시"가 켜진다.
    - 게시하는 중에는 한 번만 보낸다(`useSubmit`).
    - 게시물이 `VISIBLE`이 아니면 렌더하지 않는다.
  - `useCreateComment(postId)`:
    - 성공하면 최상위 댓글은 목록 끝에 붙인다. 답글은 그 부모의 답글 목록 끝에 붙이고 펼친다.
    - `['posts']`의 `commentCount`를 서버 값으로 맞추려고 `['posts', postId]`를 무효화한다.
  - `useDeleteComment(postId)`: 성공하면 `['posts', postId]`와 `['posts', postId, 'comments']`를 무효화한다.
  - `CommentMenu`(⋯):
    - `deletable`이면 "삭제"(`ConfirmAction`)
    - 내 댓글이 아니면 "신고"(`ReportSheet targetType="COMMENT"`)와 "차단"(`useBlockUser`)
  - `ReportSheet`: `targetType === 'COMMENT'`면 `REASONS`에서 `PORTRAIT_PRIVACY`를 뺀다.

- [ ] **Step 1: 실패하는 테스트 작성.**
  - `CommentInput.test.tsx`
    - `it("disables posting for blank or 501-character text")`
    - `it("shows the reply header and clears it on cancel")`
  - `CommentMenu.test.tsx`
    - `it("offers delete only when deletable")`
    - `it("offers report and block on others' comments")`
  - `ReportSheet.test.tsx`: `it("hides the portrait reason for comments")`
  - `post-detail.test.tsx`
    - `it("appends a new reply under its parent")`
    - `it("hides the input on a hidden post")`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** lint·typecheck·test → PASS.
- [ ] **Step 5:** 커밋 `Feat(mobile) : 댓글 입력·답글·삭제·신고 추가`

### Task 12: 운영진 신고 목록의 댓글 항목

**Files:**
- Modify: `apps/mobile/types/api/moderation.ts`(`ReportCommentResponse`, `comment` 필드), `apps/mobile/components/admin/ReportTargetRow.tsx`
- Test: `apps/mobile/__tests__/admin-reports.test.tsx`

**Interfaces:**
- Consumes: Task 6 응답
- Produces:
  - `actionsOf`의 COMMENT 분기:
    - `DELETED`면 기각만
    - `VISIBLE`이면 숨기기·기각
    - `HIDDEN_PENDING`이면 숨기기·복원·기각
    - `HIDDEN`이면 복원·기각
    - 문구는 게시물 분기에서 "글"을 "댓글"로 바꾼다. 사진 격리 문구는 쓰지 않는다.
  - `targetName`: COMMENT면 `${comment.author.nickname}의 댓글`, 없으면 `${targetId}번 댓글`
  - 줄에 댓글 본문(3줄)과 원 게시물 첫 사진의 썸네일(있을 때)을 보인다.

- [ ] **Step 1:** 실패하는 테스트를 쓴다.
  - `it("shows comment body and hide/dismiss for a visible comment")`
  - `it("offers restore for an auto-hidden comment")`
  - `it("offers only dismiss for a deleted comment")`
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** lint·typecheck·test → PASS.
- [ ] **Step 5:** 커밋 `Feat(mobile) : 운영진 신고 목록에 댓글 항목 추가`

## 문서

### Task 13: 설계·개인정보 문서 반영

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-11-26-phase2-feed-design.md`
    - 11장: 2번을 "좋아요·댓글·대댓글(댓글 신고·숨김 포함)"으로 바꾸고, 프로필을 별도 번호로 뺀다.
    - 5장: 확정 내용을 새 설계 문서로 안내한다.
  - `docs/architecture.md`
    - 10장 2단계 요약: `comments, post_likes`는 추가됐고 `profile_images`는 프로필 계획에서 추가한다고 적는다.
    - 화면 표(247행 근처): 좋아요 목록(`/posts/{id}/likes`)을 추가한다.
  - `apps/web/content/privacy.ts`
    - "피드와 신고" 수집 항목에 "남긴 댓글과 좋아요, 남긴 시각"을 넣는다.
    - 공개 범위에 "댓글과 좋아요 누른 사람은 로그인한 회원에게 보입니다"를 넣는다.
    - 탈퇴 표에 "댓글·좋아요: 바로 지움(답글이 달린 댓글은 '삭제된 댓글입니다'로 자리만 남김)"을 넣는다.
  - `CLAUDE.md` 명령 표: 바뀐 것이 없으면 그대로 둔다.
- Test: `cd apps/web && pnpm lint && pnpm test && pnpm build`(privacy 문구 스냅숏이나 테스트가 있으면 보정)

- [ ] **Step 1:** 문서를 고친다.
- [ ] **Step 2:** web lint·test·build → PASS.
- [ ] **Step 3:** 커밋 `Docs : 좋아요·댓글 반영과 개인정보 처리방침 보완`
