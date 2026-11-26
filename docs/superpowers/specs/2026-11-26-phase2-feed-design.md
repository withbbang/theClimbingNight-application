# 2단계 피드 설계

작성 2026-11-26. 정본 설계는 `docs/architecture.md`이고, 이 문서는 2단계(피드·미디어·신고·차단)를 계획으로 옮길 수 있게 구체화한 것이다. 확정되면 `architecture.md`의 2단계 부분(7·9·10·11·12장)을 이 문서에 맞춰 고친다.

표기: **(결정)** 사용자가 정한 것, **(제안)** 설계자가 정한 것으로 사용자가 바꿀 수 있다.

## 1. 목적

동호회 사람들이 일상·등반 사진과 영상을 올리고 좋아요·댓글로 반응하는, 인스타그램과 같은 용도의 SNS 피드 **(결정)**. 소모임·네이버카페에서 흩어져 있던 사진 공유를 앱 안으로 가져오는 것이 목적이다.

성공: 회원이 따로 안내받지 않아도 사진을 올리고, 비로그인 사용자가 맛보기로 피드를 보고 가입한다.

실패로 볼 것(기술적으로 동작해도): 초상권·불쾌 콘텐츠 신고가 들어왔는데 운영진이 빨리 숨길 수 없음 / 영상 변환이 NAS 메모리를 압박해 API가 죽음 / 비로그인 사용자가 API로 피드 전체를 긁어감.

## 2. 범위

### 하는 것

- 게시물: 사진·영상 최대 10장을 넘겨 보는 형태 + 본문 **(결정)**
- 피드: 팔로우 없이 동호회 전체 글 최신순 **(결정, A안)**
- 좋아요(두 번 탭 포함), 댓글·대댓글(한 단계) **(결정)**
- 프로필: 3열 격자, 프로필 사진 **(결정)**. 1단계에서 미룬 프로필 사진 업로드를 여기서 넣는다.
- 신고·차단·운영진 숨김 (Apple 1.2)
- 이미지 편집: 자르기(1:1, 4:5, 원본 비율), 필터 **(결정)**
- 미디어 워커: 원본 NAS 보관, 표시용 변환본 R2 업로드

### 안 하는 것

- 스토리, 릴스 탭, 해시태그, 탐색 탭, 팔로우 **(결정)**. 데이터 모델은 팔로우를 나중에 붙여도 피드 쿼리만 바꾸면 되게 둔다.
- 사람 태그, 위치 태그, 게시물 저장(북마크), 공유 링크 (제안: 범위 밖)
- 피드 관련 푸시 알림 (제안: 2단계 뒤 별도 작업. 좋아요·댓글 알림은 범위가 커서 뺀다)

## 3. 권한과 노출

| 동작 | 비로그인 | USER 이상 | ADMIN |
|---|---|---|---|
| 피드 최신 게시물 **3개** 보기 | O | O | O |
| 4번째 이후 게시물, 다음 페이지 | X (로그인 안내) | O | O |
| 게시물 상세 | 최신 3개에 들 때만 | O | O |
| 좋아요 수·댓글 수 | O | O | O |
| 댓글 내용 | X | O | O |
| 프로필 보기 | X | O | O |
| 작성·좋아요·댓글·신고·차단 | X | O | O |
| 남의 게시물·댓글 숨김, 신고 처리 | X | X | O |

- 비로그인 3개 **(결정)**. 게시물 개수 기준이고, 한 게시물 안의 미디어는 모두 볼 수 있다 **(제안)**.
- 제한은 API에서 한다. 토큰 없는 피드 요청은 최신 3개만 주고 다음 커서를 주지 않는다. 토큰 없이 커서를 보내면 401. 토큰 없는 상세 요청은 그 게시물이 지금 최신 3개(보이는 것 기준)에 들 때만 주고, 아니면 401 **(제안)**.
- 작성 자격은 USER 이상(동호회원 인증 전이라도 가능) — `architecture.md` 6장 그대로.
- "최신 3개"는 `VISIBLE`이고 미디어가 모두 `READY`인 게시물 기준.

## 4. 게시물 규칙 (제안)

- 미디어 1~10개 필수(글만 있는 게시물 없음). 본문 0~2,200자.
- 게시 후 본문만 고칠 수 있다. 미디어 추가·삭제·순서 변경은 안 된다(인스타그램과 같음). 지우고 다시 올린다.
- 삭제: 작성자 또는 ADMIN. 삭제는 소프트 삭제(`deleted_at`)하고 R2 변환본은 워커가 지운다. NAS 원본은 30일 뒤 정리 작업이 지운다(실수 복구 여지).
- 게시 흐름: 미디어를 모두 올린 뒤 게시물을 확정한다. 확정 뒤 모든 미디어가 `READY`가 되면 피드에 나온다. 그 전에는 작성자 본인에게만 "처리 중"으로 보인다. 하나라도 `FAILED`면 작성자에게 "다시 올려 주세요"를 보이고 피드에 내지 않는다.
- 피드 정렬은 확정(게시) 시각이 아니라 **모두 READY가 된 시각**(`published_at`) 내림차순. 영상 변환이 늦어도 순서가 뒤섞이지 않는다.
- 페이징은 커서(`published_at`, `id`) — `api-conventions` 스킬 규약을 따른다.

## 5. 댓글·좋아요 (제안)

- 댓글 1~500자. 대댓글은 한 단계: 대댓글에 답하면 같은 부모 아래 붙고 앞에 `@닉네임`을 넣는다.
- 댓글 수정 없음, 삭제만(작성자·게시물 작성자·ADMIN). 대댓글이 있는 댓글을 지우면 "삭제된 댓글입니다"로 남긴다.
- 좋아요는 `(post_id, user_id)` PK로 한 번만. 좋아요 수는 `posts.like_count` 비정규화 컬럼, 댓글 수도 `comment_count`.
- 좋아요 누른 사람 목록 보기 (제안: 포함, 단순 목록).

## 6. 신고·차단·운영진 조치

### 신고 (제안)

- 대상: 게시물, 댓글, 사용자.
- 사유: 스팸·광고 / 음란·선정 / 욕설·혐오·괴롭힘 / **초상권·사생활 침해(내 사진이 동의 없이 올라옴)** / 기타(자유 입력 필수).
- 같은 사람이 같은 대상을 두 번 신고할 수 없다.
- 신고가 들어오면 ADMIN에게 푸시한다(1단계 푸시 구조 재사용).
- **임시조치**: 초상권·사생활 침해 신고는 1건이면, 그 밖의 사유는 서로 다른 3명이 신고하면 자동으로 숨긴다(`HIDDEN_PENDING`). 운영진이 검토해 복원하거나 확정 숨김한다. 정보통신망법 44조의2 대응이며 기준은 법률 검토 대상(`docs/content-todo.md`에 추가).
- 신고자는 결과를 앱에서 볼 수 있게 하지 않는다 (제안: 1단계 범위 밖).

### 차단 (제안)

- 양방향으로 안 보이게 한다: 내가 차단한 사람과 나를 차단한 사람의 게시물·댓글이 내 피드·상세·댓글 목록에서 빠지고, 서로 프로필을 열 수 없다.
- 차단하면 그 사람에게 알리지 않는다. 마이 → 차단 목록에서 해제한다.
- 운영진도 다른 회원을 차단할 수 있다. 단 운영진 화면(신고 목록)에서는 차단과 무관하게 모두 보인다.

### 운영진 조치

- 게시물·댓글 숨김(`HIDDEN`)·복원. 숨겨진 글은 작성자에게 "운영 정책에 따라 숨겨졌어요"로 보인다.
- 신고 목록(대상별 묶음, 사유, 건수) → 숨김 / 기각.
- 작성 제한(사용자 단위 피드 작성 금지)은 2단계에 넣지 않는다 (제안: 필요하면 자격 해제·블랙리스트와 별개로 추가).

## 7. 탈퇴·자격 변경과의 관계 (제안)

- 탈퇴(`UserWithdrawnEvent`): 그 사람의 게시물·댓글을 소프트 삭제하고, 변환본 삭제 작업을 만든다. 좋아요는 지우고 수를 줄인다. 다른 사람 게시물에 단 대댓글이 있는 댓글은 "삭제된 댓글입니다"로 남긴다.
- 자격 해제(MEMBER→USER)는 피드에 영향 없음(USER도 피드를 쓴다).
- 신고·차단 기록은 탈퇴 뒤에도 남긴다(운영 기록). 보관 기간은 방침 항목으로 추가.

## 8. 미디어 처리

### 저장 위치 (제안, `architecture.md` 5장 수정)

`architecture.md`는 한 버킷 안에 `incoming/`·`public/`을 두었지만, R2 커스텀 도메인은 버킷 전체를 공개하므로 **버킷을 둘로 나눈다**.

| 버킷 | 공개 | 내용 | 수명 주기 |
|---|---|---|---|
| `media-incoming` | 비공개 | `uploads/`: 앱이 presigned PUT으로 올린 원본. `quarantine/`: 숨긴 사진의 공개본 | `uploads/`만 1일 뒤 삭제 (처리 못 한 찌꺼기 정리). `quarantine/`은 없음 |
| `media-public` | `media.<도메인>` | WebP·MP4·썸네일 | 없음 |
| 기존 백업 버킷 | 비공개 | DB 백업 | 30일 (변경 없음) |

- R2 API 토큰은 백업용과 분리해 미디어 두 버킷만 쓸 수 있게 만든다.
- NAS 원본 경로: `/volume1/<프로젝트>/media/originals/{yyyy}/{mm}/{post_media_id}.{ext}`. 컨테이너에는 워커만 마운트한다.
- DB에는 R2 키만 저장하고, URL은 응답할 때 `MEDIA_PUBLIC_BASE_URL` + 키로 만든다. 도메인을 바꿔도 데이터 이전이 없다 (제안).
- 운영에는 `media.<도메인>` 커스텀 도메인이 필요하다(`r2.dev` 주소는 요청 제한이 있는 개발용). 도메인 구매·연결은 1번 작업 출시의 선행 조건이며 `deploy/README.md` 8장의 도메인 항목과 같이 처리한다. 개발 중에는 `r2.dev` 주소를 쓴다.
- 키에는 추측 가능한 ID 대신 무작위 값(UUID)을 쓴다.
- **숨기면 URL로도 바로 막는다 (결정)**: 자동 임시조치(`HIDDEN_PENDING`), 운영진 숨김(`HIDDEN`)은 `QUARANTINE` 작업을, 삭제는 `DELETE_MEDIA` 작업을 즉시 만든다. 워커는 `media-public`의 공개본을 비공개 버킷 `media-incoming`의 `quarantine/{공개 키}`로 옮기고(복사 후 삭제) Cloudflare API로 그 URL들의 캐시를 비운다(URL 단위 purge, 무료 플랜 가능). 처음 설계는 공개본을 지우고 복원 때 NAS 원본에서 다시 만드는 것이었으나, 그러면 운영진이 신고된 사진을 볼 방법이 없어 격리로 바꿨다. 운영진은 신고 목록에서 5분짜리 presigned GET으로 격리된 사진을 보고 숨김·복원·기각을 판단한다. 아직 격리 전이면 공개 주소를 주고, 둘 다 없는 사진은 뺀다(사진마다 HEAD 1~2번).
- **복원**: 운영진이 복원하거나 자동 숨김 글의 신고를 기각하면 `RELEASE` 작업이 격리본을 **새 키**로 공개 버킷에 복사하고 예전 공개본을 지운다. 새 키를 쓰므로 예전 URL은 되살아나지 않는다. 격리본이 없으면(처리 전에 숨겨진 사진) NAS 원본에서, 그것도 없으면 `uploads/`의 업로드 객체에서 다시 변환한다. 셋 다 없으면 사진과 게시물이 `FAILED`가 된다.
- 기각(`DISMISS`)은 자동 숨김(`HIDDEN_PENDING`) 글을 복원한다(숨길 이유가 없다는 뜻). 운영진이 확정 숨김한 `HIDDEN` 글은 신고만 닫고 숨김은 유지한다. 이미 숨겨졌거나 `FAILED`인 글을 신고하면 API는 404를 주고, 앱은 접수된 것처럼 안내한다.
- **작업 종류와 우선순위**: `QUARANTINE`·`DELETE_MEDIA`(0) > `RELEASE`(1) > `PROCESS`(2) > `PURGE_ORIGINAL`(3). 숨김·삭제는 아직 `QUEUED`인 `PROCESS`를 취소한다. `PURGE_ORIGINAL`은 삭제 30일 뒤 NAS 원본을 지운다.
- 워커는 처리 중에도 게시물 상태를 다시 읽는다. 업로드 전후에 `PENDING`이 아니면 방금 올린 공개 사본을 지운다.
- 캐시 비우기가 실패하면 작업이 재시도되고, 3번째 실패에서 `FAILED`가 된다. 운영진 신고 목록에는 "캐시 비우기 실패"(`cleanupFailed`)로 표시한다.
- **알려진 한계**: 사진이 처리되기 전에 숨겨진 글을 업로드 객체 수명(1일)이 지난 뒤 복원하면 NAS 원본도 격리본도 없어 `FAILED`가 된다. 작성자가 다시 올려야 한다.

### 흐름

1. 앱: 사진은 편집 결과를 JPEG로 긴 변 2048px, 영상은 720p·최대 60초로 압축 **(architecture.md 결정)**.
2. 앱 → API `게시물 초안 만들기`(미디어 개수·종류·크기) → `posts`(DRAFT) + `post_media`(UPLOADING)마다 presigned PUT URL(유효 15분, Content-Type·최대 크기 고정: 사진 15MB, 영상 150MB).
3. 앱이 R2 `media-incoming`에 직접 올린다. 다 올리면 API `게시물 확정`(본문 포함) → API가 R2 HEAD로 각 객체 존재·크기를 확인 → `media_jobs` 생성, 미디어 PROCESSING, 게시물 PENDING.
4. worker가 작업을 하나씩 가져와(조건부 `UPDATE ... WHERE id=? AND status='QUEUED'`로 선점, 동시성 1): 원본 다운로드 → NAS 저장 → 변환 → `media-public` 업로드 → incoming 원본 삭제 → READY. 게시물의 미디어가 모두 READY면 게시물 VISIBLE, `published_at` 기록.
5. 실패하면 1분 → 5분 뒤에 다시 하고, 3번째 실패에서 FAILED. 워커가 처리 중 죽어 `RUNNING`으로 10분 넘게 있는 작업은 다시 `QUEUED`로 돌린다(시도 횟수는 늘어난다).
6. DRAFT로 24시간 지난 게시물은 정리 작업이 지운다.

### 변환 결과 (제안)

| 종류 | 결과 |
|---|---|
| 사진 | WebP 긴 변 1080px(상세), 480px(피드 격자·썸네일). EXIF(위치 포함)는 지운다 |
| 영상 | H.264 MP4 720p(VAAPI, 실패 시 소프트웨어 인코딩), AAC, `faststart`. 첫 프레임 WebP 썸네일 480px |
| 프로필 사진 | WebP 정사각 320px·96px. 같은 경로(초안 없이 `profile_images` 단일 업로드) |

- 원본 EXIF 회전값은 변환 시 반영한 뒤 제거한다.
- NAS 원본은 받은 그대로(EXIF 포함) 둔다. 외부에 나가지 않는다.

### 워커 구성 **(결정, A안)**

- `apps/api` 같은 코드베이스, `worker` 프로필로 별도 컨테이너. API 컨테이너에서는 워커 루프가 돌지 않고, 워커 컨테이너에서는 웹 서버를 띄우지 않는다(`spring.main.web-application-type=none`).
- 이미지: API 이미지와 같은 소스에서 ffmpeg를 넣은 별도 타깃(Dockerfile 멀티 스테이지에 `worker` 타깃). `mem_limit` 512MB, `-Xmx256m`.
- VAAPI: NAS `/dev/dri/renderD128`을 워커 컨테이너에 넘긴다. J4125(Gemini Lake)는 H.264 인코딩 지원. 장치가 없으면 소프트웨어 인코딩으로 떨어지고 로그에 남긴다.
- ffmpeg는 작업마다 별도 프로세스, 타임아웃(영상 5분, 사진 30초), 스레드 제한(`-threads 2`).
- 사진도 처음부터 워커를 거친다(1번 작업에서 경로를 검증하기 위해).

## 9. 데이터 모델 (제안, `architecture.md` 10장 2단계를 대체)

```
posts           id, author_id, body, status(DRAFT, PENDING, VISIBLE, FAILED, HIDDEN_PENDING, HIDDEN),
                like_count, comment_count, published_at, deleted_at
post_media      id, post_id, type(IMAGE, VIDEO), status(UPLOADING, PROCESSING, READY, FAILED),
                sort_order, incoming_key, original_path, display_key, thumbnail_key,
                width, height, duration_ms, byte_size
media_jobs      id, kind(PROCESS, QUARANTINE, RELEASE, DELETE_MEDIA, PURGE_ORIGINAL), target_type(POST_MEDIA, PROFILE_IMAGE), target_id,
                status(QUEUED, RUNNING, DONE, FAILED), attempts, next_run_at, locked_at, last_error
profile_images  id, user_id, status, incoming_key, original_path, large_key, small_key   -- users.profile_image_id로 현재 것 지정
comments        id, post_id, author_id, parent_id(nullable), body, status(VISIBLE, HIDDEN_PENDING, HIDDEN), deleted_at
post_likes      post_id, user_id, created_at   PK(post_id, user_id)
reports         id, reporter_id, target_type(POST, COMMENT, USER), target_id, reason, detail,
                status(OPEN, ACTIONED, DISMISSED), handled_by, handled_at   UNIQUE(reporter_id, target_type, target_id)
user_blocks     blocker_id, blocked_id, created_at   PK(blocker_id, blocked_id)
```

- 피드 인덱스: `posts(status, published_at, id)`.
- 차단 필터는 쿼리에서 `NOT EXISTS`로 양방향 처리. 400~500명 규모라 충분하다.

## 10. 앱 화면 (제안)

탭: 홈 / 벙 / **피드** / 굿즈 / 마이 (`architecture.md` 9장 제안 그대로).

- 피드 탭: 카드 목록(FlashList). 카드 = 작성자(아바타·닉네임) / 미디어 넘기기(점 표시) / 좋아요·댓글 버튼과 수 / 본문(3줄 접기) / 시간. 비로그인은 3개 뒤 로그인 안내 카드. 오른쪽 위 + 버튼으로 작성.
- 작성: 사진 선택(여러 장, 순서) → 편집(자르기·필터, 4번 작업) → 본문 → 올리기(진행률). 올리는 중 앱을 나가도 이어서 하지는 않는다(실패로 보고 다시 올림).
- 상세: 카드 + 댓글 목록 + 입력창. 내 글이면 ⋯ 메뉴에 수정·삭제, 남의 글이면 신고·차단.
- 프로필: 아바타, 닉네임, 게시물 수, 3열 격자. 마이 탭의 내 프로필에서 프로필 사진 변경.
- 마이 → 차단 목록. 운영진 메뉴 → 신고 목록.
- 화면 디자인은 화면 작업 디자인 스킬을 쓰고 애니메이션을 살린다(두 번 탭 하트, 좋아요 버튼 등).

## 11. 작업 순서 **(결정)**

각각 계획 문서 하나, 피처 브랜치 하나(`dev`에서).

1. **사진 피드 + 신고·차단 + 워커(사진만)** — 게시물 작성·목록·상세·삭제·본문 수정, 비로그인 3개 제한, 신고·차단·운영진 숨김·신고 목록, 워커 컨테이너와 R2 두 버킷, 탈퇴 연동. 이 단계가 끝나면 출시할 수 있다. 신고 대상 COMMENT는 2번에서 붙인다.
2. **좋아요·댓글·대댓글, 프로필(격자·프로필 사진)** — 댓글 신고·숨김 포함.
3. **영상** — 앱 압축, VAAPI 변환, 재생(`expo-video`), 썸네일.
4. **이미지 편집** — 자르기·필터. 그 전까지 작성 화면은 원본 비율 그대로 올린다.

## 12. 인프라·문서 변경

- 환경변수(API·워커): `R2_ACCOUNT_ID`(기존 키 재사용), `R2_MEDIA_ACCESS_KEY_ID`, `R2_MEDIA_SECRET_ACCESS_KEY`, `R2_MEDIA_INCOMING_BUCKET`, `R2_MEDIA_PUBLIC_BUCKET`, `MEDIA_PUBLIC_BASE_URL`, `MEDIA_ORIGINALS_DIR`(워커), `CLOUDFLARE_ZONE_ID`·`CLOUDFLARE_CACHE_PURGE_TOKEN`(워커, Zone → Cache Purge 권한만), `MEDIA_ORIGINALS_HOST_DIR`(compose 전용, NAS 원본 폴더). CLAUDE.md 규칙대로 `application.yml`·두 `.env.example`·`compose.yml`·`deploy/README.md` 표를 함께 고친다.
- `deploy/compose.yml`에 `worker` 서비스(`mem_limit: 512m`, `/dev/dri`, 원본 볼륨). `deploy.yml`이 워커 이미지도 빌드·배포.
- `deploy/README.md` 8장에 R2 미디어 버킷·커스텀 도메인·CORS(앱은 CORS가 필요 없지만 웹 관리자 대비 GET만 허용)·수명 주기 설정 추가.
- `docs/content-todo.md`: 개인정보처리방침에 "피드 최신 게시물 3개는 앱 설치자 누구나 볼 수 있음", 게시물·신고 기록 보관 기간, 임시조치 기준(법률), 이용약관에 게시물 권리·삭제 기준(법률) 추가.
- 메모리 예산: 1단계 상한 약 1.6GB + 워커 512MB ≈ 2.1GB. NAS 여유 약 4.4GB 안.

## 13. 고려한 방식

**피드 구성**: 전체 피드(채택) / 팔로우 기반 / 전체 + 팔로우 나중. 400~500명 규모에서 신규 회원 첫날부터 피드가 차 있고 구현이 단순해 전체 피드를 골랐다 (사용자).

**비로그인 노출**: 전체 공개 / 로그인 필수 / 최신 N개 맛보기(채택, N=3). 초상권·신고 불가 위험을 줄이면서 가입 전 맛보기를 남기려고 사용자가 골랐다.

**숨김 시 공개 사진 처리**: 자동 숨김 즉시 비공개 버킷으로 격리 + 캐시 비우기(채택) / 앱에서만 숨기고 확정 때 삭제. 초상권 신고 즉시 URL로도 막히게 하려고 골랐다. 처음에는 R2 삭제 후 복원 시 재변환이었으나, 운영진이 신고된 사진을 볼 수 있어야 해서 격리로 바꿨다. 대가는 Cloudflare 토큰 하나와 비공개 버킷의 격리본 보관 (사용자).

**워커**: API 코드 공유 별도 컨테이너(채택) / API 안에서 변환 / Go·Python 별도 워커. API 안 변환은 ffmpeg가 API와 메모리를 나눠 OOM 위험, 별도 언어는 상태 전이 로직이 두 곳으로 갈라져 1인 개발 유지보수 부담이 커서 제외했다 (사용자).

## 14. 만드는 사람에게 맡기는 것

- S3 호환 클라이언트 선택(AWS SDK v2 S3 + presigner 제안), ffmpeg 인자 세부, WebP 품질값.
- 앱의 이미지 선택·압축 라이브러리(설치된 Expo SDK 58 기준으로 확인), 편집기 라이브러리(4번 작업).
- API 엔드포인트 세부(`api-conventions` 규약 안에서), Flyway 마이그레이션 번호(V8부터), 인덱스 세부.
- 워커 폴링 간격(제안 2초, 작업 없으면 늘림), 정리 작업 스케줄.
- 화면 세부 디자인.
