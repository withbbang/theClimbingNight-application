// 소셜 SDK가 네이티브에서 처리하는 로그인 콜백. 화면 주소로 바꾸면 없는 화면이 열리고 인가 코드가 주소에 남는다.
const LOGIN_CALLBACK =
  /^(kakao[^:/]*|theclimbingnightnaver|com\.googleusercontent\.apps\.[^:/]*):/i;

export function redirectSystemPath({ path, initial }: { path: string; initial: boolean }) {
  if (!LOGIN_CALLBACK.test(path)) {
    return path;
  }
  return initial ? '/' : null;
}
