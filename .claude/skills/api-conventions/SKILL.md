---
name: api-conventions
description: apps/api 와 apps/mobile · apps/web 이 공유하는 REST 규약 — URL 설계, 응답 래퍼, 에러 코드, 페이징, 네이밍. 엔드포인트를 만들거나 호출 코드를 쓸 때 참조.
---
# API 규약

## URL
- `/api/v1/<복수 명사>`: `GET /api/v1/posts` · `POST /api/v1/posts` · `GET /api/v1/posts/{id}`
- URL에 동사 금지. 행위는 HTTP 메서드로. 리소스로 표현이 안 되는 액션만 `POST /api/v1/meetups/{id}/join`.
- 하위 리소스: `GET /api/v1/posts/{id}/comments`, 대댓글은 `parentId` 필드로 (URL 중첩은 2단계까지).
- 쿼리 파라미터는 camelCase: `?from=2026-01-01&period=MONTH`.

## 응답 래퍼
성공:
```json
{ "success": true, "data": { "id": 1, "title": "예시", "createdAt": "2026-11-23T13:00:00+09:00" } }
```
실패:
```json
{ "success": false, "error": { "code": "COMMON_NOT_FOUND", "message": "요청한 항목을 찾을 수 없습니다." } }
```
- `data` 는 객체 또는 배열. `null` 응답은 204 로.
- HTTP 상태: 200 조회/수정 · 201 생성 · 204 삭제 · 400 검증 실패 · 401 인증 필요 · 403 권한 없음 · 404 없음 · 405 지원하지 않는 메서드 · 409 충돌 · 415 지원하지 않는 요청 형식 · 429 시도 제한 · 500 서버 오류 · 503 외부 서비스 일시 장애.

## 에러 코드
- 형식 `<DOMAIN>_<REASON>` 대문자 스네이크. `ErrorCode` enum 한 곳에서 관리 (`apps/api/.../common/ErrorCode.java`).
- `message` 는 사용자에게 그대로 보여줘도 되는 **한국어 완성 문장**. 스택트레이스·클래스명 금지.
- 검증 실패(`@Valid`)는 `COMMON_VALIDATION_FAILED` + `error.fields: [{ field, reason }]`.

| 코드 | 상태 | 뜻 |
|---|---|---|
| `COMMON_VALIDATION_FAILED` | 400 | 요청 필드 검증 실패 |
| `COMMON_NOT_FOUND` | 404 | 리소스 없음 |
| `COMMON_METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `COMMON_UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 Content-Type |
| `COMMON_INTERNAL_ERROR` | 500 | 처리되지 않은 서버 오류 |
| `AUTH_REQUIRED` | 401 | 로그인 필요 또는 토큰 만료. 클라이언트는 재로그인으로 보낸다 |
| `AUTH_FORBIDDEN` | 403 | 역할 부족 (예: 일반회원이 동호회원 전용 기능 호출) |
| `AUTH_SIGNUP_EXPIRED` | 400 | 가입 시간이 지났습니다. 처음부터 다시 로그인해 주세요. |
| `AUTH_INVALID_SOCIAL_TOKEN` | 401 | 소셜 로그인 정보를 확인하지 못했습니다. 세션 만료가 아니라 소셜 토큰 검증 실패이므로 재로그인 처리와 구분한다 |
| `AUTH_PROVIDER_UNAVAILABLE` | 503 | 로그인 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요. |
| `AUTH_ALREADY_REGISTERED` | 409 | 이미 가입된 계정입니다. 다시 로그인해 주세요. |
| `USER_NICKNAME_INVALID` | 400 | 닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다. |
| `USER_NICKNAME_TAKEN` | 409 | 이미 사용 중인 닉네임입니다. |
| `ROSTER_CSV_INVALID` | 400 | CSV 첫 줄에 '소모임닉네임' 머리글이 있어야 합니다. |
| `ROSTER_CSV_ENCODING` | 400 | CSV 파일을 UTF-8로 저장해 다시 올려 주세요. |
| `ROSTER_CSV_TOO_MANY_ROWS` | 400 | CSV는 한 번에 5,000행까지 올릴 수 있습니다. |

- 401은 두 종류다. `AUTH_REQUIRED`는 세션이 없거나 만료된 것이라 로그인 화면으로 보내고, `AUTH_INVALID_SOCIAL_TOKEN`은 소셜 토큰 검증 실패라 세션 만료가 아니다.
- 리프레시 토큰은 회전하며 한 번만 쓸 수 있다. 클라이언트는 리프레시 요청을 single-flight(동시에 하나만)로 보내야 한다. 같은 토큰의 중복 요청은 `AUTH_REQUIRED`(401)를 받는다.

도메인별 코드는 기능을 만들 때 이 표에 추가한다.

## 페이징
- 목록(게시판·공지 등): 요청 `?page=0&size=20` (0-based, size 최대 100). 응답 `data: { items: [...], page, size, totalElements, totalPages }`.
- 무한 스크롤(피드·댓글·DM): 커서 방식. 요청 `?cursor=<불투명 문자열>&size=20`, 응답 `data: { items: [...], nextCursor }` (`nextCursor`가 `null`이면 끝).

## 네이밍
- JSON 키 camelCase. 날짜·시각은 ISO-8601 오프셋 포함 문자열.
- DTO 클래스: 요청 `Xxx<행위>Request`(`PostCreateRequest`, `PostUpdateRequest`) / 응답 `XxxResponse`. 앱·웹 타입도 같은 이름으로 `types/api/xxx.ts`.

## 클라이언트 쪽 구현
- `lib/api.ts` 의 `apiFetch<T>()` 하나로 호출한다. `success: false` 면 `ApiError(code, message)` 를 throw.
- mobile: 절대 주소(`EXPO_PUBLIC_API_URL`) + `Authorization: Bearer` 토큰. `AUTH_REQUIRED` 는 한 곳에서 받아 로그인 화면으로 보낸다.
- web: 같은 주소의 `/api/*` 상대 경로. Next.js rewrites 가 API 로 전달한다.
- React Query 키: `['posts']`, `['posts', id]`. 변이 후 관련 키 invalidate.
