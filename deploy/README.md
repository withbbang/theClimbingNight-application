# 집 서버 배포 (Synology NAS)

벽헤는밤 API·웹을 Synology DS1520+에 Docker Compose로 올린다. 외부 요청은 Cloudflare Tunnel로만 들어온다.

```
[앱·브라우저] ─HTTPS─▶ Cloudflare ─Tunnel─▶ cloudflared ─▶ nginx ─┬─ /api/* ─▶ api (Spring Boot) ─▶ mysql
                                                                  └─ /*     ─▶ web (Next.js)
```

| 파일 | 역할 |
|---|---|
| `compose.yml` | 운영 구성. api·web 이미지는 GHCR에서 받는다 |
| `compose.build.yml` | 로컬 확인용. 소스로 빌드한다 (7장) |
| `nginx/default.conf` | `/api/*`·`/*` 분기, 접속 기록 형식 |
| `.env.example` | `deploy/.env` 키 목록 (5장) |
| `backup.sh` | DB 덤프, NAS 30개 보관 + R2 사본 (4장) |
| `scripts/import-roster.sh` | 명부 CSV 일괄 등록 |

`master`에 푸시하면 `.github/workflows/deploy.yml`이 이미지를 만들어 GHCR에 올리고, SSH로 NAS에 `deploy/`를 복사한 뒤 `compose pull && up` 한다.

## 1. 처음 배포

### 1-1. NAS 준비 (k-drinker·lottery-picker와 같은 배포 계정이면 이미 되어 있다)

1. DSM → 제어판 → 터미널 및 SNMP → SSH 활성화 (포트 변경 권장).
2. 배포 계정 `~/.ssh/authorized_keys`에 배포용 공개키 등록.
3. Synology는 `docker.sock`이 root 전용이라 sudo가 필요하다.
   ```sh
   sudo sh -c 'echo "<배포계정> ALL=(root) NOPASSWD: /usr/local/bin/docker" > /etc/sudoers.d/<배포계정> && chmod 440 /etc/sudoers.d/<배포계정>'
   ssh <배포계정>@NAS 'sudo -n /usr/local/bin/docker ps'   # 확인
   ```
4. GHCR 로그인 (워크플로가 만든 패키지는 처음에 비공개다). 비밀번호 프롬프트에 `read:packages` 권한 PAT를 넣는다. `-p`로 넘기면 셸 기록에 남는다.
   ```sh
   sudo docker login ghcr.io -u withbbang
   ```

### 1-2. 배포 폴더와 `deploy/.env`

```sh
# NAS
sudo mkdir -p /volume1/docker/theclimbingnight/deploy
sudo chown <배포계정> /volume1/docker/theclimbingnight -R
```

Mac에서 `deploy/.env.example`을 NAS의 `/volume1/docker/theclimbingnight/deploy/.env`로 복사하고 값을 채운다 (5장 표). 권한은 `chmod 600`.

- `JWT_SECRET`: `openssl rand -base64 48`
- `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`: `openssl rand -base64 24` (서로 다르게)
- `CLOUDFLARE_TUNNEL_TOKEN`, `PUBLIC_HOST`: 2장

### 1-3. GitHub 설정

저장소 Settings → Secrets and variables → Actions.

| 종류 | 이름 | 값 |
|---|---|---|
| Repository secret | `NAS_HOST` | NAS 주소 |
| Repository secret | `NAS_SSH_PORT` | SSH 포트 |
| Repository secret | `NAS_SSH_USER` | 배포 계정 |
| Repository secret | `NAS_SSH_KEY` | 배포용 개인키 전체 |
| Repository variable (선택) | `NAS_DEPLOY_DIR` | 비우면 `/volume1/docker/theclimbingnight` |

Environment secret은 이 워크플로가 읽지 않는다. Repository secret에 넣는다.

### 1-4. 첫 실행

`master`에 푸시하거나 Actions → deploy → Run workflow. 첫 실행은 HDD에서 MySQL 초기화(최대 수 분) → api(Flyway 마이그레이션) → web 순서로 뜬다. 워크플로는 api가 healthy가 될 때까지 5분 기다린다.

### 1-5. 기동 확인

```sh
# NAS
cd /volume1/docker/theclimbingnight
sudo docker compose -f deploy/compose.yml --env-file deploy/.env ps
sudo docker compose -f deploy/compose.yml --env-file deploy/.env logs api | grep '격리 수준'   # "MySQL 격리 수준 확인: READ-COMMITTED"
curl -fsS https://<PUBLIC_HOST>/api/v1/health                                           # {"success":true,"data":{"status":"UP"}}
```

격리 수준이 다르면 api가 기동하지 않고 그 값을 로그에 남긴다 (`DatabaseSettingsCheck`).

## 2. Cloudflare Tunnel

1. 도메인을 사서 Cloudflare에 추가한다 (네임서버를 Cloudflare로).
2. Cloudflare 대시보드 → Zero Trust → Networks → Tunnels → Create a tunnel → Cloudflared.
3. 설치 화면의 `--token` 뒤 값을 `CLOUDFLARE_TUNNEL_TOKEN`에 넣는다. 설치 명령은 실행하지 않는다 (compose의 `cloudflared`가 대신한다).
4. Public Hostname 추가: 서브도메인(예: `api`) + 도메인, Service `HTTP` · `nginx:80`. 이 호스트 이름을 `PUBLIC_HOST`에 넣는다.
5. SSL/TLS → Edge Certificates → Always Use HTTPS 켜기.

무료 플랜은 요청 본문이 100MB로 제한된다. API는 1MB 넘는 본문을 받지 않으므로 지금은 영향이 없다 (2단계 미디어는 R2로 직접 올린다).

## 3. 다시 배포와 되돌리기

`master` 푸시면 끝이다. 문서(`*.md`, `docs/`)만 바뀐 푸시는 배포하지 않는다.

되돌릴 때는 NAS에서 `deploy/.env`의 `IMAGE_TAG`를 이전 커밋 SHA로 바꾸고:

```sh
sudo docker compose -f deploy/compose.yml --env-file deploy/.env up -d api web
```

DB 마이그레이션(Flyway)은 되돌리지 않는다. 스키마를 바꾼 배포를 되돌릴 때는 4장 백업에서 복구할지 먼저 판단한다.

## 4. 백업

`backup.sh`는 DB를 `backups/theclimbingnight-<시각>.sql.gz`로 남기고 최근 30개만 둔다. R2 키가 모두 있으면 같은 파일을 `s3://<R2_BACKUP_BUCKET>/db/`에도 올린다.

### R2 준비

1. Cloudflare → R2 → 버킷 생성 (비공개, 예: `theclimbingnight-backup`).
2. 버킷 Settings → Object lifecycle rules → `db/` 접두어, 30일 뒤 삭제.
3. R2 → Manage API tokens → 이 버킷에만 Object Read & Write 권한 토큰 → `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`. 계정 ID는 `R2_ACCOUNT_ID`.

### 매일 자동 실행 (DSM 작업 스케줄러)

제어판 → 작업 스케줄러 → 생성 → 예약된 작업 → 사용자 정의 스크립트. 사용자 `root`, 매일 04:00.

```sh
bash /volume1/docker/theclimbingnight/deploy/backup.sh
```

### 복구

```sh
cd /volume1/docker/theclimbingnight
gunzip -c backups/theclimbingnight-<시각>.sql.gz | sudo docker compose -f deploy/compose.yml --env-file deploy/.env \
  exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'
```

복구 전에 api를 멈춘다 (`stop api`). 복구 연습은 빈 MySQL 컨테이너에 먼저 해 본다.

## 5. 환경변수 (`deploy/.env`)

| 키 | 필수 | 설명 |
|---|---|---|
| `IMAGE_TAG` | — | 배포 워크플로가 커밋 SHA로 써 넣는다. 비우면 `latest` |
| `MYSQL_ROOT_PASSWORD` | 예 | MySQL root 비밀번호. 백업도 이 값을 쓴다 |
| `DB_USERNAME` | 예 | API가 쓰는 DB 사용자 (기본 `theclimbingnight`) |
| `DB_PASSWORD` | 예 | 위 사용자 비밀번호 |
| `JWT_SECRET` | 예 | 액세스 토큰 서명 키. 32바이트 이상. 바꾸면 모두 다시 로그인 |
| `KAKAO_APP_ID` | 출시 전 | 카카오 앱 ID. 비면 카카오 로그인이 모두 거부된다 |
| `GOOGLE_CLIENT_IDS` | 출시 전 | 구글 OAuth 클라이언트 ID들(쉼표). 웹·iOS·Android |
| `APPLE_AUDIENCES` | 출시 전 | Apple ID 토큰 aud. `com.theclimbingnight.app` |
| `APPLE_CLIENT_ID` | — | Apple 토큰 폐기용 client_id. 기본 `com.theclimbingnight.app` |
| `APPLE_TEAM_ID` | 출시 전 | Apple Developer 팀 ID |
| `APPLE_KEY_ID` | 출시 전 | Sign in with Apple 키 ID |
| `APPLE_PRIVATE_KEY` | 출시 전 | 그 키의 `.p8` 내용. 한 줄로 넣고 줄바꿈은 `\n`. 셋 중 하나라도 비면 탈퇴 때 Apple 토큰을 폐기하지 않는다 (심사 거절 사유) |
| `EXPO_ACCESS_TOKEN` | 출시 전 | Expo 푸시 액세스 토큰 (8장 Enhanced push security) |
| `CLOUDFLARE_TUNNEL_TOKEN` | 예 | 2장 |
| `PUBLIC_HOST` | 예 | 터널 Public Hostname. 배포 확인과 문서에 쓴다 |
| `R2_ACCOUNT_ID` 외 3개 | — | 4장. 비우면 백업을 NAS에만 둔다 |

API 환경변수를 추가하면 `apps/api/src/main/resources/application.yml`, `apps/api/.env.example`, 이 표, `.env.example`, `compose.yml`의 api `environment`를 함께 고친다.

## 6. 메모리

| 컨테이너 | mem_limit | 로컬 실측 (기동 직후) |
|---|---|---|
| nginx | 32m | 8MiB |
| api | 768m | 411MiB (`-Xmx512m`) |
| web | 256m | 41MiB |
| mysql | 512m | 285MiB (`innodb-buffer-pool-size=128M`) |
| cloudflared | 64m | — |
| 합계 상한 | 약 1.6GB | |

NAS 여유는 약 4.4GB(2026-11-23 실측)다. 다른 프로젝트와 같이 쓰므로 한도를 올리기 전에 `free -m`을 확인한다.

## 7. 로컬에서 같은 구성 확인 (개발용)

저장소 밖(예: 임시 폴더)에 `deploy/.env.example`을 복사해 더미 값을 채운 파일을 만들고:

```sh
docker compose -p tcn-local -f deploy/compose.yml -f deploy/compose.build.yml --env-file <임시 파일> \
  up -d --build --wait mysql api web nginx
docker exec tcn-local-nginx-1 wget -qO- http://127.0.0.1/api/v1/health
docker compose -p tcn-local -f deploy/compose.yml -f deploy/compose.build.yml --env-file <임시 파일> down -v --rmi local
```

cloudflared는 실제 터널 토큰이 있어야 하므로 로컬에서는 띄우지 않는다. nginx 안에서 `localhost`는 IPv6로 풀려 거부되니 `127.0.0.1`을 쓴다.

2026-11-26 로컬 MySQL 8.4(prod 프로필)에서 확인한 것:

- 기동 로그 `MySQL 격리 수준 확인: READ-COMMITTED`.
- `push_tokens`에 대소문자만 다른 토큰 두 개가 따로 저장된다 (ascii_bin).
- 운영진 문의 목록을 상태 필터 없이(`status` null) 조회하면 200.
- nginx가 `/api/*`와 `/app/*`를 나누고, 접속 기록에 쿼리스트링이 남지 않는다.
- `backup.sh`가 덤프를 만들고, R2 키가 없으면 업로드를 건너뛴다.
- api `stop`이 graceful 종료로 1초 안에 끝난다.
