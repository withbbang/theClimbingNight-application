# 2단계 프로필 설계

작성 2026-11-28. 피드 설계(`2026-11-26-phase2-feed-design.md`) 11장의 3번 작업. 피드 설계 3·8·9·10장의 프로필 관련 내용을 구체화하고, 다른 곳은 그 문서를 따른다.

표기: **(결정)** 사용자가 정한 것, **(제안)** 설계자가 정한 것으로 사용자가 바꿀 수 있다.

## 1. 목적

인스타그램처럼 그 사람이 올린 사진을 모아 보는 곳 **(결정)**. 피드에서 본 사람이 누군지, 무엇을 올려 왔는지 보게 한다. 1단계에서 미룬 프로필 사진을 넣어 피드·댓글의 첫 글자 아바타를 실제 사진으로 바꾼다.

성공: 피드에서 작성자를 눌러 프로필로 가고, 회원이 따로 안내받지 않아도 프로필 사진과 자기소개를 바꾼다.

실패로 볼 것(기술적으로 동작해도): 불쾌하거나 남의 얼굴을 쓴 프로필 사진을 운영진이 바로 내릴 수 없음 / 차단한 사람의 프로필이 보임 / 프로필 사진에 위치 정보(EXIF)가 남아 공개됨 / 프로필 사진 교체가 게시물 사진 처리를 오래 밀어냄.

## 2. 범위

### 하는 것

- 프로필 화면: 프로필 사진, 닉네임, 자기소개, 게시물 수, 3열 격자 **(결정)**
- 프로필 사진 올리기·바꾸기·기본 이미지로 되돌리기 **(결정: 워커 경유 A안)**
- 자기소개 **(결정: 넣음)**
- 모든 아바타 자리(피드 카드, 상세, 댓글, 좋아요 목록, 차단 목록, 마이 탭)에 프로필 사진 표시
- 아바타·닉네임을 누르면 그 사람 프로필로 이동
- 운영진: 사용자 신고에서 프로필 사진 내리기, 자기소개 지우기
- 탈퇴 연동

### 안 하는 것

- 동호회 활동 정보(동호회원 배지, 참석률, 벙 기록) **(결정: 사진 위주)**
- 팔로우·팔로워 수, 프로필 공유 링크, 하이라이트, 탭(사진/태그) 구분 (제안: 피드 설계의 범위 밖 그대로)
- 프로필 사진 필터·편집. 1:1 자르기만 기기 사진 선택기로 한다. 편집은 5번 작업 (제안)
- 프로필 사진 자동 임시조치(신고 수로 자동 숨김) (제안: 7장)

## 3. 권한과 노출 (제안, 피드 설계 3장 따름)

| 동작 | 비로그인 | USER 이상 | ADMIN |
|---|---|---|---|
| 프로필 보기·격자 | X (401, 앱은 로그인 안내) | O | O |
| 아바타 사진 보기(피드 맛보기 3개 안) | O | O | O |
| 내 프로필 사진·자기소개 바꾸기 | X | O | O |
| 남의 프로필 사진 내리기·자기소개 지우기 | X | X | O (신고 목록에서) |

- 서로 차단 관계(어느 쪽이든)이면 프로필과 격자는 404. 그 사람의 아바타는 이미 피드·댓글에서 빠지므로 따로 처리할 것이 없다.
- 탈퇴한 사용자의 프로필은 404. 앱은 "없는 사용자예요"를 보여 준다. 탈퇴한 사용자의 아바타는 누를 수 없다.
- 운영진도 차단 관계면 프로필은 404 (운영진 화면인 신고 목록에서는 차단과 무관하게 보인다, 피드 설계 6장 그대로).

## 4. 프로필 화면 (제안)

```
┌──────────────────────────────┐
│ ←  클라이밍왕                ⋯ │   남의 프로필: ⋯ → 신고·차단
│                              │   내 프로필: ⋯ 대신 "프로필 편집"
│   (사진)    게시물 24         │
│   클라이밍왕                  │
│   주말 볼더링, 가끔 리드        │   자기소개(없으면 줄 없음)
│                              │
│ ┌────┬────┬────┐             │
│ │    │  ⧉ │    │  3열 정사각 격자 │
│ ├────┼────┼────┤  ⧉ = 사진 여러 장 │
│ │    │    │    │  탭 → 게시물 상세 │
│ └────┴────┴────┘             │
└──────────────────────────────┘
```

- 경로: `/users/[id]`. 내 id면 같은 화면에 편집 진입점이 붙는다. 마이 탭 위쪽(사진·닉네임 영역)을 누르면 내 프로필로 간다. 마이 탭 메뉴 구성은 그대로.
- 들어가는 곳: 피드 카드·상세의 작성자 아바타·닉네임, 댓글 아바타·닉네임, 좋아요 목록 행, 차단 목록은 제외(차단 중이라 404). 비로그인이 누르면 로그인 화면.
- 격자 칸: 첫 미디어의 썸네일(480px WebP)을 정사각으로 잘라 보인다. 미디어가 2개 이상이면 오른쪽 위에 겹친 아이콘.
- **남의 프로필 격자**: `VISIBLE` 게시물만, `published_at`·`id` 내림차순 커서 페이징(한 번에 18개 = 6줄).
- **내 프로필 격자**: 위와 같은 목록에 내가 볼 수 있는 숨긴 글(`HIDDEN_PENDING`·`HIDDEN`)도 섞어 넣는다. 숨긴 글은 공개본이 격리돼 있고, 지금 `PostResponseMapper`도 작성자에게 사진 URL을 주지 않는다. 그래서 숨긴 칸은 사진 없이 회색 칸에 "숨겨짐"만 보이고, 누르면 기존 상세의 "운영 정책에 따라 숨겨졌어요"로 간다. (검토 뒤 추가, 설계자 결정) 처리 중·실패한 글은 기존 `GET /posts/mine/in-progress`로 격자 위에 띠로 따로 보인다(피드 탭과 같은 컴포넌트 재사용).
- 게시물 수: `VISIBLE` 게시물 수. 내 프로필도 같은 기준(남이 보는 숫자와 같게).
- 비어 있으면: 남의 프로필 "아직 올린 사진이 없어요", 내 프로필 "첫 사진을 올려 보세요" + 작성 버튼.
- 화면 디자인은 화면 작업 디자인 스킬을 쓰고 애니메이션을 살린다(격자 칸 순차 등장, 프로필 사진 교체 시 크로스페이드 등).

### 프로필 편집 (제안)

- 내 프로필 → "프로필 편집": 사진(누르면 "사진 바꾸기 / 기본 이미지로"), 닉네임(기존 `/settings/nickname`으로 이동), 자기소개 입력, 저장.
- 사진 바꾸기: `expo-image-picker`의 자르기(`allowsEditing`, `aspect [1,1]`)로 고르고, 앱이 긴 변 1024px JPEG로 줄여 올린다(`expo-image-manipulator`, 게시물 작성과 같은 방식).
- 올린 뒤 처리(보통 수 초) 동안 화면에는 고른 사진을 미리 보여 주고 "적용하는 중" 표시. 앱은 `GET /users/me/profile-images/{id}`로 그 사진의 `status`를 2초 간격으로 최대 30초 본다.
  - `READY`: 내 정보를 다시 받아 새 사진으로 바꾼다.
  - `FAILED`: "사진을 적용하지 못했어요. 다시 올려 주세요". 워커 재시도 때문에 FAILED는 몇 분 뒤에야 찍히므로, 30초 안에는 주로 확정 단계 오류(객체 없음·크기 초과)가 이 문구로 나온다.
  - `REMOVED`(더 새 사진이 이겼거나 되돌림): 조용히 내 정보를 다시 받는다.
  - 30초가 지나도 `PROCESSING`이면 "잠시 뒤 반영돼요"로 바꾸고 기다림을 멈춘다. 다음에 들어올 때 반영된다. (검토 뒤 추가, 설계자 결정)

## 5. 자기소개 (제안)

- 0~150자(인스타그램과 같음), 줄바꿈 최대 3개(4줄). 앞뒤 공백은 지우고, 비우면 `null`.
- `users.bio VARCHAR(150) NULL`. 글자 수는 닉네임 정책과 같이 코드포인트 기준.
- 링크 자동 인식 없음. 그냥 글자로 보인다.
- 운영진이 지우면 `null`이 된다. 본인에게 따로 알리지 않는다(다시 쓸 수 있다).

## 6. 프로필 사진 처리 **(결정: A안, 워커 경유)**

### 데이터

```
profile_images  id, user_id, status(UPLOADING, PROCESSING, READY, FAILED, REMOVED),
                upload_key, original_path, public_key, byte_size, created_at, updated_at
users           + profile_image_id (nullable, FK profile_images.id), + bio
media_jobs      + target_type(POST_MEDIA, PROFILE_IMAGE) NOT NULL DEFAULT 'POST_MEDIA'
```

- 공개본 키: `a/{hex}` + `/l.webp`(320px) / `/s.webp`(96px). 게시물 사진(`p/{hex}` + `/d.webp`·`/t.webp`)과 같은 모양의 무작위 키, URL은 응답 때 `MEDIA_PUBLIC_BASE_URL` + 키.
- 업로드 키: `uploads/{hex}.jpg` (`media-incoming`, 게시물과 같다. 기존 1일 수명 주기 규칙이 `uploads/` 전체에 걸린다).
- NAS 원본: 원본 루트 기준 `profile/yyyy/MM/{profile_image_id}.jpg`. 게시물 원본과 이름이 겹치지 않게 `profile/` 아래에 둔다.
- `REMOVED`: 바꾸기·되돌리기·운영진 내리기·탈퇴로 더 쓰지 않는 사진.
- 피드 설계 9장의 `large_key`·`small_key`는 `public_key` 하나 + 접미사(`/l.webp`·`/s.webp`)로 바꾼다. `PostMedia`의 공개 키 + 접미사 방식과 같다.

### 흐름

1. 앱 → `POST /api/v1/users/me/profile-images` → `profile_images`(UPLOADING) + presigned PUT(15분, `image/jpeg`, 1바이트~5MB). 앱은 1:1로 자른 뒤 긴 변 1024px JPEG 0.85로 올린다.
2. 앱이 `media-incoming`에 올린다 → `POST /api/v1/users/me/profile-images/{id}/confirm` → API가 HEAD로 존재·크기 확인 → PROCESSING, `media_jobs`(PROCESS, PROFILE_IMAGE) 생성.
3. 워커: 업로드 다운로드 → NAS 원본 저장 → 가운데를 정사각으로 잘라 WebP 320px·96px(EXIF·프레임 메타데이터 제거, 기존 `ImageTranscoder` 규칙 재사용) → `media-public` 업로드 → 업로드 객체 삭제 → 한 트랜잭션에서 사용자 행을 잠그고:
   - 이 사진이 그 사용자의 **가장 최근에 확정한 사진**이면 READY로 바꾸고 `users.profile_image_id`를 이것으로 바꾼다. 예전 사진은 REMOVED + `DELETE_MEDIA` 작업.
   - 더 최근 사진이 이미 확정됐거나 그 사이 사용자가 되돌리기·탈퇴했으면 이 사진은 REMOVED로 두고 방금 올린 공개본을 지운다. 늦게 끝난 예전 업로드가 새 사진을 덮지 않게 한다.
4. 실패·재시도는 게시물과 같다(1분 → 5분, 3번째에 FAILED). FAILED면 `profile_image_id`는 예전 그대로.
5. 되돌리기: `DELETE /api/v1/users/me/profile-image` → `profile_image_id = null`, 예전 사진 REMOVED + `DELETE_MEDIA`.
6. NAS 원본은 REMOVED가 된 뒤 30일에 `PURGE_ORIGINAL`이 지운다(게시물과 같음).
7. UPLOADING으로 24시간 지난 행은 기존 초안 정리 작업처럼 지운다.

- 우선순위: 기존 표 그대로(`QUARANTINE`·`DELETE_MEDIA` > `RELEASE` > `PROCESS` > `PURGE_ORIGINAL`). 프로필 사진은 한 장·작은 크기라 게시물 처리를 오래 밀어내지 않는다.
- 남용 방지 (검토 뒤 수정, 설계자 결정):
  - 새 업로드를 시작하면 그 사용자의 예전 `UPLOADING` 행을 바로 지운다. 올리다 그만둔 사진 때문에 막히는 일이 없다. 업로드 객체가 남았으면 1일 수명 주기가 지운다.
  - `PROCESSING`은 최대 2개까지만 둔다. 넘으면 `PROFILE_IMAGE_BUSY`(409)로 거절한다. 재시도 대기 중인 사진도 센다.
- 지금 `target_id`로만 작업을 찾는 곳(`MediaJobScheduler.cancelQueued`, `MediaJobProcessor.failMedia`, `findPostIdsWithFailedCleanup` 등)은 모두 `target_type`도 조건에 넣는다. 게시물 사진 id와 프로필 사진 id가 겹치기 때문이다.
- 만든 뒤 달라진 점: `MediaJobRepository.cancelQueued`는 더 이상 영속성 컨텍스트를 비우지 않는다. 호출한 쪽이 이미 읽어 둔 `MediaJob` 엔티티는 취소가 반영되지 않은 채일 수 있다. 워커가 프로필 처리를 확정할 때 잠그는 순서는 사용자 행 → 프로필 사진 행이다(탈퇴가 사용자 행을 먼저 잠그는 것과 같다).
- 작업 처리기는 `(kind, target_type)`으로 나눈다. 프로필 사진은 `PROCESS`·`QUARANTINE`·`DELETE_MEDIA`·`PURGE_ORIGINAL`만 쓰고 `RELEASE`는 없다(7장).

## 7. 운영진 조치 (제안)

- 사용자 신고(`USER`) 대상에 조치 두 개를 연다: `REMOVE_PROFILE_IMAGE`, `CLEAR_BIO`. 지금은 `USER`에 `DISMISS`만 된다.
  - `REMOVE_PROFILE_IMAGE`: `profile_image_id = null`, 사진 REMOVED + `QUARANTINE`(공개본을 격리하고 캐시를 비움). 신고는 ACTIONED로 닫는다.
  - `CLEAR_BIO`: `bio = null`. 신고는 ACTIONED로 닫는다.
  - 사진과 자기소개가 다 있으면 카드에 "둘 다 내리기" 버튼을 하나 더 두고, 앱이 `REMOVE_PROFILE_IMAGE` → `CLEAR_BIO`를 차례로 보낸다. 두 번째 요청은 신고가 이미 닫혀 있어도 조치하고 204를 준다(`close()`는 열린 신고가 없으면 아무것도 안 한다).
- 복원은 없다. 내린 사진은 본인이 다시 올린다.
- 보존 (검토 뒤 추가, 설계자 결정): 숨긴 게시물과 같은 기준을 쓴다. 운영진이 내린 사진은 `QUARANTINE`만 걸고 격리본과 NAS 원본을 모두 남기며 `PURGE_ORIGINAL`을 예약하지 않는다. 탈퇴할 때 그 사람의 모든 프로필 사진에 `DELETE_MEDIA` + 30일 뒤 `PURGE_ORIGINAL`을 걸어 함께 지운다. 본인이 바꾸거나 되돌린 사진만 30일 뒤 원본을 지운다. 보존 기간 문구는 `content-todo.md`의 신고 기록 보관 항목에 넣는다.
- 신고 목록의 사용자 대상 카드에는 지금 프로필 사진(공개 URL, 96px)과 자기소개만 보여 준다. 조치해 신고가 닫히면 카드가 목록에서 빠지므로, 예전에 내린 사진은 보여 주지 않는다. 격리본은 분쟁에 대비한 기록이고, 그 사진을 보는 화면은 이번 범위 밖이다. (설계자 결정)
- 자동 임시조치는 하지 않는다: 사용자 신고는 사유가 사진인지 자기소개인지 닉네임인지 알 수 없고, 자동으로 다 내리면 장난 신고로 프로필을 지울 수 있다. 신고가 들어오면 기존대로 ADMIN에게 푸시가 가고 운영진이 판단한다. 초상권 사유에 대한 법적 기준은 `docs/content-todo.md`의 임시조치 항목에 "프로필 사진 포함 여부"를 덧붙인다.

## 8. API (제안, `api-conventions` 스킬 규약)

| 메서드·경로 | 권한 | 내용 |
|---|---|---|
| `GET /api/v1/users/{id}/profile` | USER | `{ id, nickname, bio, profileImage: { largeUrl, smallUrl } \| null, postCount, mine }`. 차단·탈퇴·없음은 404 |
| `GET /api/v1/users/{id}/posts?cursor&size` | USER | 격자 항목 `{ id, thumbnailUrl, mediaCount, status(본인만) }` 커서 페이지. 기본 18, 최대 30 |
| `PATCH /api/v1/users/me/profile` | USER | `{ bio }`. 닉네임은 기존 `PATCH /users/me` |
| `POST /api/v1/users/me/profile-images` | USER | `{ id, uploadUrl, headers, expiresAt }` |
| `POST /api/v1/users/me/profile-images/{id}/confirm` | USER | 처리 시작. 202 |
| `GET /api/v1/users/me/profile-images/{id}` | USER | `{ id, status }`. 본인 것만, 아니면 404 |
| `DELETE /api/v1/users/me/profile-image` | USER | 기본 이미지로 |
| `POST /api/v1/report-targets/USER/{id}/actions` | ADMIN | `action`에 `REMOVE_PROFILE_IMAGE`, `CLEAR_BIO` 추가 |

- 작성자·사용자를 담는 응답에 작은 사진 URL을 넣는다: `PostAuthorResponse`·`LikerResponse`·`BlockedUserResponse`에 `profileImageUrl`(96px, nullable), `UserResponse`(`/users/me`)는 그대로 둔다(토큰·벙 주최자·인증 신청에도 쓰여서). 마이 탭과 편집 화면은 `GET /users/{내 id}/profile`로 받는다. 응답 만들 때 사용자 id 묶음으로 한 번에 읽어 N+1을 피한다.
- 비로그인 피드 맛보기 3개의 작성자 아바타도 사진 URL을 준다(공개 버킷이라 어차피 공개, 프로필 자체는 401).

## 9. 탈퇴·자격 변경 (제안)

- 탈퇴(`UserWithdrawnEvent`): `bio = null`, `profile_image_id = null`, 그 사람의 모든 프로필 사진 REMOVED + `DELETE_MEDIA` + 30일 뒤 `PURGE_ORIGINAL`(운영진이 내린 사진 포함), 진행 중인 `PROCESS`는 취소. 아키텍처 문서의 "개인정보(닉네임, 프로필 사진 …)를 지운다"와 맞춘다.
- 자격 해제(MEMBER→USER)는 영향 없음.

## 10. 앱 공통 변경 (제안)

- `Avatar`에 `imageUrl`(nullable)·크기 prop을 추가. 사진이 있으면 `expo-image`로 홀드 모양 안에 채우고, 없거나 불러오기 실패면 지금처럼 첫 글자. 피드·댓글은 96px 판, 프로필 화면은 320px 판.
- 누를 수 있는 작성자 영역은 공용 컴포넌트 하나로 만들어 피드 카드·댓글·좋아요 목록에서 쓴다.
- 화면 갱신: 내 사진·자기소개가 바뀌면 세션의 내 정보와 프로필 쿼리를 다시 받는다. 피드에 이미 받아 둔 내 게시물 아바타는 다음 새로고침에 바뀐다(즉시 바꾸지 않음).

## 11. 인프라·문서 변경

- 새 환경변수 없음(기존 미디어 버킷·토큰·경로 재사용).
- 새 오류 코드: `USER_BIO_INVALID`(400), `PROFILE_IMAGE_BUSY`(409, 처리 중 2개 초과), `PROFILE_IMAGE_UPLOAD_INCOMPLETE`(409, 확정 때 객체 없음·크기 다름). `api-conventions` 스킬 표에 있다.
- 마이그레이션 V10: `users.bio`, `users.profile_image_id`, `profile_images`, `media_jobs.target_type`(기존 행은 `POST_MEDIA`), 프로필 격자용 인덱스 `posts(author_id, status, published_at, id)`.
- `architecture.md` 10장 2단계 요약의 `profile_images` 줄, 피드 설계 9장 `profile_images` 컬럼, 11장 3번에 이 문서 링크.
- `docs/content-todo.md`: 개인정보처리방침에 자기소개·프로필 사진 항목(수집·공개 범위·탈퇴 시 삭제), 임시조치 항목에 프로필 사진 포함 여부.

## 12. 접근 방식 검토

**프로필 사진 처리**

- **A. 워커 경유 (채택)**: 게시물 사진과 같은 presigned 업로드 → 워커 변환 → 공개 버킷. 서버가 형식·EXIF를 책임지고 원본이 NAS에 남으며, 운영진 내리기가 기존 격리 경로를 그대로 쓴다. 대가: `media_jobs.target_type` 추가, 처리기 분기, 몇 초의 반영 지연(앱 미리보기로 가림).
- **B. 앱이 줄여 공개 버킷에 직접**: 즉시 반영, 워커 변경 없음. 서버 검증·EXIF 보장이 없고, NAS 원본이 없고, 공개 버킷 쓰기 URL을 앱에 주며, 격리 경로를 따로 만들어야 해서 뺐다.
- 고려 후 버림: API가 받아 직접 변환 — 원본이 API를 거치는 업로드는 피하는 설계 원칙(CLAUDE.md)과 API 메모리 예산에 맞지 않는다.

**프로필 내용**: 사진 위주(채택, 사용자) / 동호회 활동 포함 — 보여 줄 항목과 권한 검사가 늘어 뺐다. 자기소개는 넣는다(사용자).

## 13. 빌더에게 맡기는 것

- 격자·프로필 화면의 시각 디자인과 애니메이션 세부(디자인 스킬 사용).
- 처리기 분기 구조(`JobHandler`를 대상별로 나눌지, 처리기 안에서 분기할지).
- 정사각 자르기 ffmpeg 필터 식, 96px 판 품질 값.
- 프로필 사진 응답을 묶어 읽는 조회 방식.
