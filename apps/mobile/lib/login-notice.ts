// 로그인 화면으로 돌려보낼 때 보여줄 문구. 딥링크로 바꿀 수 없게 주소에 넣지 않는다.
let message: string | null = null;

export const loginNotice = {
  set(next: string) {
    message = next;
  },
  /** 한 번 읽으면 비운다. */
  take() {
    const taken = message;
    message = null;
    return taken;
  },
};
