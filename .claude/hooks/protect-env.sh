#!/usr/bin/env bash
# PreToolUse(Read|Edit|Write|MultiEdit|Bash): .env* 파일 읽기·수정 차단.
# exit 2 → 도구 호출 차단, stderr 내용이 Claude 에게 전달됨.
# .env.example 은 허용.
# - Read/Edit/Write: file_path 의 파일명이 .env 또는 .env.* 이면 차단
# - Bash: 명령 안에 .env / .env.* 경로 토큰이 있으면 차단 (.env.example 만 있으면 허용)
#   `process.env.X` 처럼 앞에 이름이 붙은 건 경로가 아니므로 통과한다.

input=$(cat)

result=$(printf '%s' "$input" | python3 -c '
import json, os, re, sys

try:
    data = json.load(sys.stdin)
except ValueError:
    sys.exit(0)
tool = data.get("tool_name", "")
args = data.get("tool_input") or {}

def is_env(name):
    return name != ".env.example" and (name == ".env" or name.startswith(".env."))

if tool == "Bash":
    command = args.get("command") or ""
    # 경로 구분자·공백·따옴표·리다이렉션 뒤에 오는 .env 토큰
    for match in re.finditer(r"(?:^|[\s/\"'"'"'=<>:;|&(`])(\.env(?:\.[\w.-]+)?)(?=$|[\s\"'"'"';|&)<>`])", command):
        if is_env(match.group(1)):
            print("bash:" + match.group(1))
            break
else:
    path = args.get("file_path") or ""
    if path and is_env(os.path.basename(path)):
        print("file:" + path)
')

case "$result" in
  file:*)
    echo "차단: '${result#file:}' 은 환경변수 파일입니다. 값은 사용자가 직접 넣고, 키 목록만 .env.example 에 적으세요." >&2
    exit 2
    ;;
  bash:*)
    echo "차단: 명령이 환경변수 파일 '${result#bash:}' 에 접근합니다. .env 는 읽거나 고치지 않습니다. 키 목록은 .env.example 에만 적으세요." >&2
    exit 2
    ;;
esac
exit 0
