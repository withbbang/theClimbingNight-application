// 가입 토큰은 메모리에만 둔다. 주소나 저장소에 남기지 않는다.
let signupToken: string | null = null;

export const pendingSignup = {
  set(token: string) {
    signupToken = token;
  },
  get: () => signupToken,
  clear() {
    signupToken = null;
  },
};
