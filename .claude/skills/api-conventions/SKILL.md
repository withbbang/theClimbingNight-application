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
- HTTP 상태: 200 조회/수정 · 201 생성 · 204 삭제 · 400 검증 실패 · 401 인증 필요 · 403 권한 없음 · 404 없음 · 409 충돌 · 429 시도 제한 · 500 서버 오류.

## 에러 코드
- 형식 `<DOMAIN>_<REASON>` 대문자 스네이크. `ErrorCode` enum 한 곳에서 관리 (`apps/api/.../common/ErrorCode.java`).
- `message` 는 사용자에게 그대로 보여줘도 되는 **한국어 완성 문장**. 스택트레이스·클래스명 금지.
- 검증 실패(`@Valid`)는 `COMMON_VALIDATION_FAILED` + `error.fields: [{ field, reason }]`.

| 코드 | 상태 | 뜻 |
|---|---|---|
| `COMMON_VALIDATION_FAILED` | 400 | 요청 필드 검증 실패 |
| `COMMON_NOT_FOUND` | 404 | 리소스 없음 |
| `COMMON_INTERNAL_ERROR` | 500 | 처리되지 않은 서버 오류 |
| `AUTH_REQUIRED` | 401 | 로그인 필요 또는 토큰 만료 |
| `AUTH_FORBIDDEN` | 403 | 역할 부족 (예: 일반회원이 동호회원 전용 기능 호출) |

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
