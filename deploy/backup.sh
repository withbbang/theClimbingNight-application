#!/usr/bin/env bash
# theclimbingnight DB를 gzip 덤프로 남기고 최근 KEEP개(기본 30)만 보관한다. 하루 1회 실행한다.
# R2 키가 모두 있으면 같은 파일을 R2 비공개 버킷의 db/ 아래에도 올린다 (30일 삭제는 버킷 수명 주기 규칙).
#   sudo bash deploy/backup.sh
# 환경변수(선택):
#   BACKUP_DIR    덤프 위치 (기본: 배포 폴더의 backups/)
#   KEEP          보관 개수 (기본: 30)
#   ENV_FILE      compose용 env 파일 (기본: deploy 폴더의 .env)
#   COMPOSE_PROJECT  compose 프로젝트 이름 (기본: compose.yml의 name)
# 비밀번호는 명령행에 싣지 않는다. 컨테이너 안의 MYSQL_ROOT_PASSWORD를 MYSQL_PWD로 넘긴다.
set -euo pipefail

# DSM 작업 스케줄러는 PATH가 짧다. Synology의 docker는 /usr/local/bin에 있다.
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
KEEP="${KEEP:-30}"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

compose=(docker compose -f "$SCRIPT_DIR/compose.yml" --env-file "$ENV_FILE")
if [[ -n "${COMPOSE_PROJECT:-}" ]]; then
	compose+=(-p "$COMPOSE_PROJECT")
fi

case "$KEEP" in
	'' | *[!0-9]* | 0) echo "KEEP은 1 이상의 정수여야 합니다: $KEEP" >&2; exit 1 ;;
esac

# env 파일에서 한 키의 값을 읽는다 (따옴표·공백 제거). 파일을 source 하지 않는다.
env_value() {
	grep "^$1=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d "\"' \t\r" || true
}

umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
name="theclimbingnight-$stamp.sql.gz"
target="$BACKUP_DIR/$name"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

# 작은따옴표 안의 $MYSQL_*는 컨테이너 안에서 풀린다. 호스트 ps에는 변수 이름만 보인다.
"${compose[@]}" exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --no-tablespaces --routines --triggers --hex-blob --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
	| gzip -9 > "$partial"

# 정상 종료한 mysqldump는 마지막 줄에 "-- Dump completed"를 남긴다.
if ! gzip -dc "$partial" | tail -n 1 | grep -q '^-- Dump completed'; then
	echo "덤프가 끝까지 기록되지 않았습니다: $partial" >&2
	exit 1
fi
mv "$partial" "$target"
trap - EXIT

# 이름에 시각이 들어 있어 사전순 = 시간순. 오래된 것부터 지운다.
dumps=()
while IFS= read -r f; do
	dumps+=("$f")
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'theclimbingnight-*.sql.gz' | sort)
excess=$(( ${#dumps[@]} - KEEP ))
for (( i = 0; i < excess; i++ )); do
	rm -f -- "${dumps[$i]}"
done

echo "백업 완료: $target ($(du -h "$target" | cut -f1)), 보관 $(( ${#dumps[@]} < KEEP ? ${#dumps[@]} : KEEP ))개"

# ---- R2 사본 ----
r2_account="$(env_value R2_ACCOUNT_ID)"
r2_bucket="$(env_value R2_BACKUP_BUCKET)"
r2_key="$(env_value R2_ACCESS_KEY_ID)"
r2_secret="$(env_value R2_SECRET_ACCESS_KEY)"
if [[ -z "$r2_account" || -z "$r2_bucket" || -z "$r2_key" || -z "$r2_secret" ]]; then
	echo "R2 업로드 건너뜀 (R2 키가 비어 있음)"
	exit 0
fi

# 키는 -e 이름만 넘겨 docker 명령행에 값이 보이지 않게 한다.
AWS_ACCESS_KEY_ID="$r2_key" AWS_SECRET_ACCESS_KEY="$r2_secret" \
	docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
	-v "$BACKUP_DIR:/backups:ro" amazon/aws-cli \
	s3 cp "/backups/$name" "s3://$r2_bucket/db/$name" \
	--endpoint-url "https://$r2_account.r2.cloudflarestorage.com" --only-show-errors
echo "R2 업로드 완료: s3://$r2_bucket/db/$name"
