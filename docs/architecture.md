# theClimbingNight 아키텍처

벽헤는밤 클라이밍 동호회 앱의 설계 문서. 이 문서가 화면 구성, 시스템 구성, 데이터 모델의 정본이다.
작성 2026-11-23. "(제안)"으로 표시한 항목은 대화에서 확정되지 않고 설계자가 정한 기본값이라 바꿀 수 있다.

## 1. 왜 만드는가

동호회는 지금까지 소모임 앱과 네이버카페로 운영했다.

- 소모임: 최대 300명이라 신규 인원을 계속 받기 어렵다. 관리자 페이지가 없다. 쓰기 불편하다.
- 네이버카페: 쓰기 불편하고 UI가 불편하다. 굿즈 판매 같은 상업 활동이 제한된다.

이 앱은 **가입 이후의 동호회 활동 전체를 옮겨 오는 동호회 전용 앱**이다. 동호회원은 현재 400~500명이다.

소모임과 앱은 계속 함께 운영한다. 신규 회원 모집은 소모임에서 하고, 앱에서 동호회 가입을 시도하면 소모임으로 보낸다. 소모임에 들어온 회원을 주기적으로 앱으로 일괄 이동시키는 구조다. 벙 신청과 참석 기록은 앱에서만 받는다고 가정한다 (소모임에 올린 벙의 참석을 앱 통계에 넣으려면 운영진이 직접 입력해야 한다).

불편했던 지점(가입 절차, 벙 찾기와 신청 단계, 처음 온 사람이 동호회를 알아보기 어려움)이 모두 해결 대상이다.

## 2. 목표 / 비목표 / 반목표

**목표**
- 동호회원이 앱을 열고 두 번 탭 안에 다가오는 벙을 찾고 신청한다.
- 운영진이 벙 개설, 참석 보정, 동호회원 승인을 앱에서 직접 한다.
- 인원 상한 없이 회원이 늘어도 버틴다.
- 굿즈 판매 경로를 동호회가 직접 가진다 (처음엔 네이버 스마트스토어 링크, 나중엔 PG 결제).
- 소셜 로그인 한 번으로 가입한다.

**비목표 (이번에 만들지 않음)**
- 신규 회원 모집. 소모임이 계속 맡는다.
- 관리자 웹 페이지. 나중에 만든다. 그 전까지 운영진 기능은 앱 안의 운영진 전용 화면으로 처리한다.
- 블랙리스트 자동 제재. 운영진이 숫자를 보고 직접 판단한다.
- 1단계의 피드, 이미지 편집, DM (2·3단계, 7장).

**반목표 (이렇게 되면 기술적으로 돌아가도 실패)**
- 소모임보다 벙 신청이 번거로워진다.
- 운영진 업무가 늘어난다 (예: 매 벙마다 참석자 전원을 손으로 체크해야 함).
- NAS의 다른 서비스(k-drinker 등)가 이 앱 때문에 느려지거나 죽는다.
- 회원 개인정보나 블랙리스트 기록이 다른 회원에게 노출된다.

## 3. 제약

| 항목 | 값 | 영향 |
|---|---|---|
| 서버 | Synology DS1520+ (Celeron J4125 4코어, iGPU, RAM 8GB 상한) | 다른 프로젝트와 공유. 이 앱 전체 1.0~1.4GB 이내 |
| 회선 | 업로드 92 / 다운로드 88Mbps (100M급) | 영상 재생 트래픽은 NAS에서 내보내지 않는다 |
| 저장 | RAID5 14TB (2% 사용) | 원본 보관 여유 충분 |
| 외부 노출 | Cloudflare Tunnel 무료 플랜 | 요청 본문 100MB 제한. 큰 업로드는 R2로 직접 |
| 비용 | 최소. 연 약 20만 원 (Apple 개발자 $99, Google $25, 도메인) | 유료 SaaS 지양 |
| 인력 | 1인 개발 | 구성 요소 수를 줄인다 |
| 스토어 심사 | UGC 신고·차단 (Apple 1.2), 앱 내 탈퇴 (5.1.1), 소셜 로그인 시 Apple 로그인 (4.8), 웹뷰 래퍼 금지 (4.2) | 11장 체크리스트 |
| 고정 스택 | Spring Boot, nginx, Next.js / 앱은 Expo(React Native) | |

## 4. 접근 방식

| | A. Spring 중심 (**선택**) | B. Spring + Firebase |
|---|---|---|
| 로그인 | 카카오·Apple 토큰을 Spring이 검증, 자체 JWT 발급 | Firebase Auth, Spring은 Firebase 토큰 검증 |
| DM | Spring WebSocket + MySQL | Firestore |
| 데이터 위치 | MySQL 한 곳 | 회원·DM 일부가 Google 쪽으로 나뉨 |

A를 고른 이유: 국내 사용자에게 필요한 카카오 로그인은 Firebase가 기본 지원하지 않아 B도 커스텀 연동이 필요하다. 그러면 B의 속도 이점이 거의 없다. 명부·블랙리스트·참석률처럼 서로 엮인 데이터가 많아 한 DB가 유리하다.

검토 후 제외: Supabase 같은 BaaS 중심 구성(고정 스택과 충돌), 웹뷰 위주 앱(이미지 편집 성능과 심사 4.2 리스크), Flutter(React·TS 자산 재사용 불가).

미디어는 R2 + NAS 하이브리드로 확정: 원본은 NAS, 보여주는 압축본·썸네일은 R2.

## 5. 시스템 구성

```
[iOS/Android 앱 (Expo)]
   │  HTTPS (API, JWT)                       │ 미디어 PUT (presigned URL) / GET
   ▼                                         ▼
[Cloudflare Tunnel]                    [Cloudflare R2]
   │                                     ├─ incoming/  (업로드 원본, 처리 후 삭제)
   ▼                                     └─ public/    (압축본·썸네일, 공개 도메인)
[NAS: Docker Compose]                        ▲
   ├─ nginx ──┬─ /api/*  → api (Spring)      │
   │          └─ /*      → web (Next.js)      │
   ├─ api      Spring Boot (REST, 2단계부터 WebSocket)
   ├─ worker   미디어 처리 (ffmpeg + VAAPI, 2단계) ── 원본 다운로드·NAS 보관·변환·R2 업로드
   ├─ mysql    MySQL 8.4
   └─ redis    (2단계 DM부터. 1단계엔 두지 않음)
        │
        └─ /volume1/... 원본 미디어, DB 백업

[Expo Push Service] ← api가 알림 발송 (FCM·APNs 경유, 무료)
```

### 컨테이너와 메모리 예산

| 컨테이너 | 단계 | mem_limit (제안) | 비고 |
|---|---|---|---|
| nginx | 1 | 32MB | 프로젝트 전용 |
| api | 1 | 768MB | `-Xmx512m -XX:+ExitOnOutOfMemoryError` |
| web | 1 | 256MB | Next.js standalone |
| mysql | 1 | 512MB | 프로젝트 전용 인스턴스 (제안). 기존 MySQL 통합은 별도 작업 |
| worker | 2 | 512MB | 작업 큐에서 한 번에 하나씩 처리 |
| redis | 3 | 64MB | DM 세션·pub/sub |

1단계 합계 상한 약 1.5GB. 현재 NAS 여유 약 4.4GB.

### 도메인 (제안)

- `api.<도메인>` → nginx → api / web. 웹뷰 페이지도 같은 도메인에서 서빙한다.
- `media.<도메인>` → R2 공개 버킷 (Cloudflare 커스텀 도메인).
- 도메인은 아직 없다. 구매가 필요하다.

### 배포

k-drinker와 같은 패턴: `master` 푸시 → GitHub Actions가 이미지 빌드(GHCR) → NAS에 `deploy/` 복사 → `compose pull && up`. 앱은 EAS Build로 스토어 빌드, JS만 바뀌면 EAS Update.

### 백업

매일 `mysqldump`를 압축해 NAS에 보관하고, 같은 파일을 R2 비공개 경로에 한 부 더 올린다 (제안: 30일 보관). 원본 미디어는 RAID5 위에 있고 별도 외부 백업은 하지 않는다 (제안, 비용 이유).

## 6. 회원과 권한

### 역할

| 역할 | 누가 | 할 수 있는 것 |
|---|---|---|
| 비로그인 | 누구나 | 동호회 소개, 공지, 굿즈 링크 보기 |
| USER (일반회원) | 앱 가입자 | 위 + 문의, 동호회원 인증 신청, (2단계) 피드·댓글·좋아요, (3단계) DM |
| MEMBER (동호회원) | 운영진 승인을 받은 사람 | 위 + 벙 일정 조회·신청·취소, 내 참석률 |
| ADMIN (운영진) | DB에서 직접 지정 (관리자 웹이 생기면 거기서) | 위 + 벙 개설·수정·취소, 참석 보정, 블랙리스트 조회·취소, 동호회원 승인·해제, 공지 작성, 문의 답변 |

권한 검사는 모두 API에서 한다. 화면에서 숨기는 것은 보조 수단이다.

### 가입과 로그인

- 소셜 로그인: **카카오, 네이버, 구글**. iOS에는 **Apple 로그인**을 추가한다 (Apple 4.8: 다른 소셜 로그인을 제공하면 Apple 로그인도 제공해야 함).
  | | 카카오 | 네이버 | 구글 | Apple |
  |---|---|---|---|---|
  | iOS | O | O | O | O |
  | Android | O | O | O | X (제안: 웹 리다이렉트 흐름·도메인이 필요해 뺌) |
- iPhone에서 Apple로만 가입한 사람은 Android로 바꾸면 계정을 이어 쓸 수 없다. 가입 화면에서 카카오·네이버·구글을 위에 두고 Apple을 마지막에 둔다 (제안).
- 앱이 각 SDK로 받은 토큰(카카오·네이버 access token, 구글·Apple ID token)을 API로 보내면 API가 제공자에게 검증한 뒤 자체 토큰을 발급한다.
- 카카오·네이버 SDK는 네이티브 모듈이라 Expo Go로는 실행할 수 없다. 개발은 처음부터 EAS development build로 한다. 각 제공자 개발자 콘솔에 앱 등록(번들 ID, 키 해시, 리다이렉트)이 필요하다.
- 토큰 (제안): access token JWT 30분, refresh token 30일 회전 방식(사용할 때마다 새로 발급, 이전 것 폐기), DB에는 해시로 저장. 앱은 `expo-secure-store`에 보관.
- 첫 로그인 시 닉네임을 입력받아 USER로 가입한다. 프로필 사진은 1단계에 없다 (닉네임 첫 글자 아바타). 업로드 경로가 2단계에 생기면 함께 넣는다 (제안). 같은 사람이 여러 소셜 계정으로 로그인하면 별개 계정으로 본다 (제안. 계정 연결은 3단계). 제공자가 이메일을 주더라도 이메일로 자동 병합하지 않는다 (다른 사람 계정에 붙는 사고 방지).
- 앱 탈퇴: 계정을 비활성화하고 개인정보(닉네임, 프로필 사진, 제공자 ID)를 지운다. 명부의 참석 기록은 통계 보존을 위해 남기되 앱 계정과의 연결을 끊는다.

### 동호회원 명부와 인증

참석 기록은 **앱 계정이 아니라 동호회원 명부(roster)** 에 붙는다. 구글 시트의 지난 기록에는 앱에 가입하지 않은 사람도 있기 때문이다.

```
[명부: 소모임 닉네임 기준] ← 시트 이관 데이터, 벙 신청·참석 기록이 여기에 붙음
        ▲ 승인 시 연결 (1:1)
[앱 계정] ── 인증 신청(소모임 닉네임 입력) → 운영진 승인
```

**출시 전 명부 채우기:** 운영진이 기존 동호회원 400~500명의 소모임 닉네임을 CSV로 정리하면 명부에 일괄 등록한다. 이후 소모임에서 넘어오는 회원도 같은 방법으로 주기적으로 추가한다. 1단계에서는 관리자 웹이 없으므로, ADMIN 토큰으로 호출하는 CSV 등록 API와 이를 부르는 스크립트(`deploy/scripts/`)로 처리한다 (제안). 같은 CSV를 다시 올려도 중복 생성되지 않게, 이미 있는 닉네임은 건너뛰고 결과(추가·건너뜀·오류 행)를 돌려준다.

흐름:
1. USER가 "동호회원 인증"에서 소모임 닉네임을 입력해 신청한다. 대기 중 신청은 한 사람당 하나.
2. 운영진이 "승인 대기" 화면에서 확인한다.
   - 승인: 아직 계정에 연결되지 않은 같은 닉네임의 명부 항목이 **정확히 하나**면 그걸 후보로 보여주고, 없으면 새로 만든다. 둘 이상이면 운영진이 고른다. 자동으로 연결하지 않고 운영진이 확인한 뒤 연결한다 (닉네임 중복 때문에 남의 기록에 붙는 것을 막기 위해).
   - **일괄 승인:** 후보가 정확히 하나인 신청만 골라 한 번에 승인하는 버튼을 둔다. 출시 직후 수백 건이 몰려도 운영진은 애매한 건(후보 없음, 둘 이상)만 하나씩 보면 된다. 운영진이 다른 명부 항목을 직접 골라 연결할 수도 있다 (닉네임이 바뀐 경우). 계정은 MEMBER가 된다.
   - 거절: 사유를 남긴다. 사용자는 사유를 보고 다시 신청할 수 있다.
3. 동호회원 자격 해제: 운영진이 MEMBER를 USER로 내린다. 명부 연결과 기록은 남는다 (재승인 시 이어짐).

앱의 "동호회 가입하기" 버튼은 소모임 모임 링크를 외부 브라우저로 연다. 링크는 서버 설정값으로 둬서 앱 업데이트 없이 바꾼다.

## 7. 단계별 범위

| 단계 | 기능 |
|---|---|
| **1단계 (첫 출시)** | 카카오·네이버·구글·Apple 로그인, 탈퇴 / 명부 CSV 일괄 등록, 동호회원 인증·승인·일괄 승인 / 벙 일정·신청·대기·취소 / 참석 보정·블랙리스트 / 내 참석률 / 공지 / 문의 / 굿즈(스마트스토어 링크) / 동호회 소개 / 푸시 알림 |
| 2단계 | 피드(사진·영상), 이미지 편집, 댓글·대댓글·좋아요, 신고·차단, 미디어 워커 |
| 3단계 | DM, 관리자 웹, 구글 시트 지난 기록 이관 도구, PG 결제 |

구글 시트 이관은 데이터를 받는 시점에 따라 앞당길 수 있다. 데이터 모델은 1단계부터 이관을 받을 수 있게 만든다 (명부, `source` 컬럼, 이관 데이터에 없을 수 있는 컬럼은 nullable). 시트의 실제 열 구성은 받은 뒤 확인하고, 모델과 다르면 새 마이그레이션으로 맞춘다.

2단계부터 다른 사용자가 만든 콘텐츠가 생기므로 신고·차단은 피드와 같은 단계에 반드시 함께 나간다 (Apple 1.2).

## 8. 벙

### 규칙

- 운영진만 벙을 연다. 항목: 제목, 설명, 시작·종료 시각, 장소(암장 이름 텍스트), 정원, 늦은 취소 기준(기본 시작 24시간 전, 벙마다 변경 가능), **신청자 명단 공개 여부**(토글).
- 명단 공개가 켜져 있으면 동호회원은 벙 상세에서 확정자·대기자 닉네임과 순서를 본다. 꺼져 있으면 인원(확정 8/12, 대기 3)과 본인 상태·순번만 본다. 기본값은 켜짐 (제안: 소모임과 같은 경험). 운영진은 토글과 관계없이 항상 명단을 본다. 개설 후에도 바꿀 수 있다.
- 동호회원이 신청하면 정원 안이면 **확정**, 넘치면 **대기**(신청 순서대로 순번).
- 신청 마감: 시작 시각까지 (제안). 시작 후에는 신청·취소 불가.
- 확정자가 취소하면 대기 1번이 자동으로 확정되고 푸시를 받는다 (제안).
- 운영진이 정원을 늘리면 대기자가 순서대로 확정된다. 정원을 줄여도 이미 확정된 사람은 내리지 않는다 (제안).
- **늦은 취소는 너그럽게 다룬다.** 불가피한 사유가 있을 수 있어서 자동으로 블랙리스트를 매기지 않는다.
  - 기준 시각 이후 확정자가 취소하면 취소 사유(선택 입력)와 함께 **"늦은 취소"로 표시만** 한다.
  - 운영진이 벙 신청자 목록에서 늦은 취소 건을 보고, 필요하다고 판단할 때만 블랙리스트 +1을 매긴다.
  - 대기자의 취소, 기준 시각 이후 대기에서 자동 확정된 사람의 취소는 늦은 취소 표시도 하지 않는다 (`confirmed_at`이 기준 시각 이후면 제외. 본인이 고르지 않은 확정이므로).
- 운영진은 지난 벙을 **복제해서 개설**할 수 있다. 제목·설명·장소·정원·늦은 취소 기준을 복사하고 날짜만 새로 고른다. 정기벙 입력 부담을 줄이기 위한 것으로, 반복 규칙 자동 생성은 만들지 않는다 (제안).
- 운영진은 특정 신청자를 내보낼 수 있다 (블랙리스트 없음).
- 운영진이 벙을 취소하면 신청자 전원에게 푸시. 취소된 벙은 참석률 계산에서 빠진다.

### 참석 기록과 보정

- 벙이 끝나면 (종료 시각 기준) 그 시점의 확정자 전원을 **참석**으로 기록한다 (제안). 운영진이 대부분은 손대지 않고 안 온 사람만 고치게 하려는 것.
- 주최 운영진(또는 다른 운영진)이 나중에 보정한다: 참석 / 불참(사전 연락 있음) / 노쇼. 노쇼로 바꾸면 블랙리스트 +1, 되돌리면 그 기록이 취소된다.
- 보정 기한은 두지 않는다 (제안). 언제 고쳐도 통계가 다시 계산된다.

### 블랙리스트

- 사유는 늦은 취소, 노쇼 두 가지. 늦은 취소는 운영진이 직접 매길 때만, 노쇼는 운영진이 참석 보정에서 노쇼로 표시할 때 생긴다. 시스템이 혼자 매기는 경우는 없다. 각 기록은 벙과 연결되고, 운영진이 사유를 적어 취소할 수 있다.
- 자동 제재 없음. 운영진이 숫자를 보고 판단한다.
- 운영진에게 보이는 곳: 동호회원 프로필, 벙 신청자 목록. **누적 횟수와 최근 3개월 횟수**를 함께 보여준다 (제안).
- 본인은 자기 기록(횟수, 사유, 벙)만 볼 수 있다. 다른 회원에게는 보이지 않는다.

### 참석률

- 기간: 일 / 주 / 월 / 분기 / 연 (주는 월요일 시작, 한국 시간 기준).
- **참석률 = 기간 내 내가 "참석"으로 기록된 벙 수 ÷ 기간 내 열린 벙 수(취소 제외)** (제안). 참석 횟수도 함께 보여준다.
- 지난 기록을 이관하면 이관된 벙도 분모·분자에 포함된다.
- 집계는 요청 때 SQL로 계산한다. 회원 수·벙 수가 작아 별도 집계 테이블이 필요 없다.

### 동시성

정원 판정은 벙 행을 잠그고(`SELECT ... FOR UPDATE`) 확정자 수를 센 뒤 저장한다. 같은 사람의 중복 신청은 `(meetup_id, roster_id)` 유니크 제약으로 막는다.

## 9. 화면 구성 (네이티브 / 웹뷰)

웹뷰는 **로그인이 필요 없는 읽기 전용 페이지**에만 쓴다. 웹뷰 안에서 앱 토큰을 넘기는 복잡도를 피하려는 것이다.

| 화면 | 구현 | 대상 | 단계 |
|---|---|---|---|
| 로그인, 닉네임 입력 | 네이티브 | 비로그인 | 1 |
| 홈 (다가오는 벙, 최근 공지, 동호회 가입하기 버튼) | 네이티브 | 전체 (벙은 MEMBER만) | 1 |
| 벙 일정 (목록 + 월 달력) | 네이티브 | MEMBER | 1 |
| 벙 상세 (신청·취소, 확정·대기 인원, 공개 시 명단) | 네이티브 | MEMBER | 1 |
| 내 참석률 (기간 선택, 횟수·비율) | 네이티브 | MEMBER | 1 |
| 내 블랙리스트 기록 | 네이티브 | MEMBER | 1 |
| 동호회원 인증 신청·결과 | 네이티브 | USER | 1 |
| 문의 작성·내역·답변 보기 | 네이티브 | USER 이상 | 1 |
| 마이페이지 (프로필, 알림 설정, 로그아웃, 탈퇴) | 네이티브 | USER 이상 | 1 |
| [운영진] 벙 개설·수정·취소 | 네이티브 | ADMIN | 1 |
| [운영진] 참석 보정 | 네이티브 | ADMIN | 1 |
| [운영진] 동호회원 승인 대기·일괄 승인, 회원 목록·자격 해제, 블랙리스트 | 네이티브 | ADMIN | 1 |
| [운영진] 명부 CSV 일괄 등록 | 스크립트 + API (관리자 웹 전까지) | ADMIN | 1 |
| [운영진] 공지 작성, 문의 답변 | 네이티브 | ADMIN | 1 |
| 공지 목록·상세 | 웹뷰 | 전체 | 1 |
| 동호회 소개 | 웹뷰 | 전체 | 1 |
| 굿즈 (소개 + 스마트스토어로 이동) | 웹뷰 | 전체 | 1 |
| 이용약관, 개인정보처리방침 | 웹뷰 | 전체 | 1 |
| 피드, 게시물 작성·편집, 댓글, 신고·차단 | 네이티브 | USER 이상 (보기는 전체) | 2 |
| DM | 네이티브 | USER 이상 | 3 |
| 관리자 웹 | 웹 (`apps/web/app/admin`) | ADMIN | 3 |

탭 구성 (제안): 홈 / 벙 / (2단계) 피드 / 굿즈 / 마이. 벙 탭은 MEMBER가 아니면 "동호회원 인증" 안내를 보여준다. 운영진 화면은 마이 탭 안의 "운영진 메뉴"로 들어간다.

웹뷰 페이지는 `apps/web`의 `/app/*` 경로 아래에 두고, 같은 페이지를 브라우저로도 열 수 있게 한다 (공지·소개 링크 공유용). 외부 링크(스마트스토어, 소모임)는 웹뷰 안에서 열지 않고 시스템 브라우저로 넘긴다.

## 10. 데이터 모델 초안 (MySQL)

공통: PK는 `BIGINT AUTO_INCREMENT`, 시각은 `DATETIME(6)` UTC 저장, 생성·수정 시각 컬럼은 생략 표기.

### 1단계

```
users
  id, nickname, profile_image_key (nullable), role ENUM(USER, MEMBER, ADMIN),
  status ENUM(ACTIVE, WITHDRAWN), withdrawn_at

auth_identities                      -- 소셜 로그인 연결
  id, user_id → users, provider ENUM(KAKAO, NAVER, GOOGLE, APPLE), provider_user_id
  UNIQUE(provider, provider_user_id)

refresh_tokens
  id, user_id → users, token_hash UNIQUE, expires_at, revoked_at

roster_members                       -- 동호회원 명부
  id, somoim_nickname (유니크 아님: 동명이인·닉네임 변경 대비), user_id → users (nullable, UNIQUE), note,
  source ENUM(APP, IMPORT)

membership_requests                  -- 동호회원 인증 신청
  id, user_id → users, somoim_nickname, status ENUM(PENDING, APPROVED, REJECTED),
  reject_reason, roster_member_id → roster_members (승인 시), reviewed_by → users, reviewed_at

meetups                              -- 벙 (이관 데이터를 위해 ends_at·capacity·place_name은 nullable, 앱에서 만들 땐 필수)
  id, title, description, place_name, starts_at, ends_at, capacity,
  late_cancel_hours DEFAULT 24, participants_visible BOOLEAN DEFAULT TRUE,
  host_user_id → users (nullable: 이관 데이터),
  status ENUM(OPEN, CANCELED), canceled_at, source ENUM(APP, IMPORT)

meetup_applications                  -- 신청 (앱에서 생긴 것만)
  id, meetup_id → meetups, roster_member_id → roster_members,
  status ENUM(CONFIRMED, WAITLISTED, CANCELED, REMOVED),
  applied_at, confirmed_at, canceled_at, late_cancel BOOLEAN, cancel_reason (nullable)
  UNIQUE(meetup_id, roster_member_id)
  -- 대기 순번 = WAITLISTED 중 applied_at 순서

attendances                          -- 참석 기록 (앱·이관 공통, 참석률의 근거)
  id, meetup_id → meetups, roster_member_id → roster_members,
  result ENUM(ATTENDED, EXCUSED, NO_SHOW), recorded_by → users (nullable), source ENUM(AUTO, ADMIN, IMPORT)
  UNIQUE(meetup_id, roster_member_id)

penalties                            -- 블랙리스트 기록
  id, roster_member_id → roster_members, meetup_id → meetups,
  type ENUM(LATE_CANCEL, NO_SHOW), created_by → users (항상 운영진),
  revoked_at, revoked_by → users, revoke_reason

notices
  id, title, body (마크다운), pinned BOOLEAN, author_id → users, published_at

inquiries
  id, user_id → users, title, body, status ENUM(OPEN, ANSWERED),
  answer, answered_by → users, answered_at

push_tokens
  id, user_id → users, expo_push_token UNIQUE, platform ENUM(IOS, ANDROID), last_seen_at

app_settings                         -- 앱 업데이트 없이 바꾸는 값
  key PRIMARY KEY, value
  -- somoim_join_url, smartstore_url 등
```

### 2단계 (방향만)

```
posts            id, author_id, body, visibility, status, created_at
post_media       id, post_id, type(IMAGE, VIDEO), status(UPLOADING, PROCESSING, READY, FAILED),
                 incoming_key, original_path(NAS), display_key, thumbnail_key, width, height, duration_ms, sort_order
comments         id, post_id, author_id, parent_id(nullable, 대댓글 1단계까지), body, deleted_at
post_likes       post_id, user_id  PK(post_id, user_id)
reports          id, reporter_id, target_type(POST, COMMENT, USER), target_id, reason, status, handled_by
user_blocks      blocker_id, blocked_id  PK(blocker_id, blocked_id)
media_jobs       id, post_media_id, status, attempts, last_error   -- 워커 작업 큐 (DB 폴링)
```

3단계 DM(`dm_rooms`, `dm_messages`, `dm_reads`)은 그때 설계한다.

## 11. 기능별 세부

### 푸시 알림 (1단계)

`expo-notifications`로 Expo push token을 받아 API에 등록하고, API는 Expo Push API로 보낸다 (제안: FCM·APNs를 직접 다루는 것보다 단순하고 무료).

| 이벤트 | 받는 사람 |
|---|---|
| 동호회원 인증 승인·거절 | 신청자 |
| 대기 → 확정 승격 | 해당 회원 |
| 벙 취소 | 신청자 전원 |
| 새 벙 개설 | MEMBER 전원 (알림 설정에서 끌 수 있음) |
| 새 공지 | 전원 (끌 수 있음) |
| 문의 답변 | 문의자 |
| 벙 전날 리마인드 | 확정자 (제안) |

### 미디어 처리 (2단계)

1. 앱이 사진은 리사이즈(긴 변 2048px), 영상은 압축(720p, 최대 60초) 후 API에 업로드 요청.
2. API가 `post_media`(UPLOADING)를 만들고 R2 `incoming/` presigned PUT URL을 준다.
3. 앱이 R2에 직접 올리고 API에 완료를 알린다 → `media_jobs` 생성, PROCESSING.
4. worker가 작업을 하나씩 가져와 R2에서 원본을 받아 NAS에 보관 → 표시용(이미지: WebP 여러 크기, 영상: H.264 MP4 + 썸네일, VAAPI 하드웨어 인코딩) 생성 → R2 `public/`에 업로드 → `incoming/` 원본 삭제 → READY.
5. 실패하면 3회 재시도 후 FAILED, 앱에 실패 표시.

### 굿즈 (1단계)

웹뷰의 굿즈 페이지에서 상품 소개를 보여주고, 구매는 스마트스토어 링크로 넘긴다. 실물 상품이라 나중에 PG 결제를 붙여도 앱스토어 인앱결제 대상이 아니다. PG를 쓰려면 사업자등록과 통신판매업 신고가 필요하다 (3단계 전 준비).

### 문의

1:1 문의. 다른 사용자에게 보이지 않는다. 운영진이 답변하면 푸시.

## 12. 스토어 심사 체크리스트

| 요건 | 대응 | 단계 |
|---|---|---|
| 앱 내 계정 삭제 (5.1.1(v)) | 마이페이지 탈퇴 | 1 |
| 소셜 로그인 시 Apple 로그인 (4.8) | iOS에 카카오·네이버·구글과 함께 Apple 로그인 | 1 |
| 최소 기능, 웹뷰 래퍼 금지 (4.2) | 핵심 화면은 네이티브 | 1 |
| 개인정보처리방침 URL | 웹뷰 페이지, 스토어 등록 | 1 |
| UGC 신고·차단·운영자 조치 (1.2) | 신고, 차단, 운영자 숨김 처리 | 2 (피드와 함께) |
| 심사용 테스트 계정 | 심사 전 MEMBER 테스트 계정 준비 (카카오 로그인만으로는 심사자가 못 들어올 수 있음) | 1 |

## 13. 결정 기록

| 결정 | 내용 |
|---|---|
| 앱 | Expo (React Native, TypeScript), Expo Router |
| 서버 | Spring Boot + nginx + Next.js, NAS Docker Compose |
| 로그인 | 카카오·네이버·구글 + iOS Apple, Spring 자체 JWT (접근 방식 A) |
| 소모임 병행 | 소모임은 계속 운영(모집 창구), 회원을 주기적으로 앱으로 일괄 이동 |
| 명부 | 출시 전 기존 400~500명 CSV 일괄 등록, 인증 신청은 후보 1명이면 일괄 승인 |
| 명단 공개 | 벙마다 개설자가 토글 |
| 미디어 | 원본 NAS, 표시용 R2 |
| DB | MySQL 8.4 |
| 동호회원 인증 | 소모임 닉네임으로 신청, 운영진 승인 |
| 관리자 | 1단계는 앱 안 운영진 화면, 관리자 웹은 3단계 |
| 신규 회원 모집 | 소모임 유지, 앱은 소모임으로 연결 |
| 블랙리스트 | 늦은 취소·노쇼 카운트, 운영진이 직접 매김(늦은 취소는 표시만 자동), 자동 제재 없음 |
| 참석 기록 | 실제 참석 기준, 주최 운영진이 보정 |
| 지난 기록 | 구글 시트로 보관 중, 나중에 이관 (명부 기반 모델로 대비) |

## 14. 만드는 사람에게 맡기는 것

- 라이브러리 세부 선택: 상태관리, 목록 가상화(FlashList 등), 달력 컴포넌트, 2단계 이미지 편집기.
- API 엔드포인트 세부 설계 (`api-conventions` 스킬 규약 안에서).
- Flyway 마이그레이션 구성, 인덱스.
- nginx 설정, Cloudflare Tunnel 구성 방식 (기존 프로젝트와 같은 터널을 쓸지 등).
- 디자인 (색, 타이포, 아이콘). 동호회 로고·브랜드 자료는 사용자에게 받는다.
- "(제안)" 표시 항목은 구현 중 더 나은 근거가 있으면 바꾸되, 바꾸면 이 문서를 고친다.
