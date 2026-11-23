#!/usr/bin/env bash
# 명부 CSV 일괄 등록 (운영진 전용).
#
# 사용법: API_URL=https://api.example.com ADMIN_ACCESS_TOKEN=<토큰> ./import-roster.sh <csv경로>
#
# CSV 형식
#   - UTF-8로 저장 (BOM 있어도 됨)
#   - 첫 줄은 머리글: 소모임닉네임,메모   (메모 열은 없어도 됨)
#   - 빈 줄은 무시, 값 앞뒤 공백은 자름
#   - 데이터는 한 번에 최대 5,000행, 파일 크기는 1MB 이하
#   - 이미 명부에 있거나 파일 안에서 중복인 닉네임(대소문자 무시)은 건너뜀
#   - 오류 행이 있어도 나머지는 등록되므로, 고친 파일을 다시 올리면 추가분만 들어감
set -euo pipefail

usage() {
  echo "사용법: API_URL=<API 주소> ADMIN_ACCESS_TOKEN=<운영진 액세스 토큰> $0 <csv경로>" >&2
  exit 1
}

if [[ $# -ne 1 || -z "${API_URL:-}" || -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  usage
fi
if [[ ! -f "$1" ]]; then
  echo "파일을 찾을 수 없습니다: $1" >&2
  exit 1
fi

curl --fail-with-body -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -F "file=@$1;type=text/csv" \
  "$API_URL/api/v1/roster-members/import"
echo
