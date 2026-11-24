const PATTERN = /^[가-힣a-zA-Z0-9_]{2,16}$/;

/** 서버의 NicknamePolicy와 같은 규칙. 최종 판정은 서버가 한다. */
export function nicknameError(value: string): string | null {
  return PATTERN.test(value.trim())
    ? null
    : '닉네임은 2~16자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있습니다.';
}
