# 벽헤는밤 앱

Expo SDK 58, React Native, TypeScript. 카카오·네이버·구글 SDK가 네이티브 모듈이라 Expo Go로는 열 수 없고 development build가 필요하다.

## 준비물

값은 `.env.example`을 복사한 파일에 넣는다. 비어 있는 제공자는 로그인 버튼이 숨고, 나머지는 그대로 동작한다.

| 무엇                    | 어디서                                                                         | 환경변수                                                               |
| ----------------------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------- |
| 카카오 네이티브 앱 키   | Kakao Developers. 플랫폼에 iOS 번들 ID와 Android 패키지·키 해시 등록           | `EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY`                                     |
| 네이버 Client ID·Secret | 네이버 개발자센터. iOS URL 스킴은 `theclimbingnightnaver`                      | `EXPO_PUBLIC_NAVER_CLIENT_ID`, `EXPO_PUBLIC_NAVER_CLIENT_SECRET`       |
| 구글 OAuth 클라이언트   | Google Cloud. 웹·iOS·Android(SHA-1) 세 개를 만들고 앱에는 웹과 iOS ID를 넣는다 | `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID` |
| Apple 로그인            | Apple Developer에서 App ID에 Sign in with Apple 켜기                           | 없음                                                                   |
| EAS 프로젝트 ID         | `pnpm dlx eas-cli init`                                                        | `EAS_PROJECT_ID`                                                       |
| FCM 설정                | Firebase의 `google-services.json` 경로                                         | `GOOGLE_SERVICES_JSON`                                                 |

번들 ID와 패키지는 둘 다 `com.theclimbingnight.app`이다. `EXPO_PUBLIC_*`는 앱 번들에 그대로 들어간다.

## 실행

```bash
pnpm install
pnpm expo run:ios        # 시뮬레이터용 development build를 만들고 연다
pnpm expo run:android
pnpm start               # 빌드가 이미 있으면 개발 서버만
```

환경변수를 바꾸면 네이티브 설정도 바뀌므로 다시 빌드한다. 다른 프로젝트가 8081을 쓰고 있으면 `pnpm start --port 8090`처럼 바꾼다. 실기기 빌드는 `pnpm dlx eas-cli build --profile development`로 만든다.

## 로컬 API 연결

`EXPO_PUBLIC_API_URL`을 넣는다.

| 기기               | 주소                      |
| ------------------ | ------------------------- |
| iOS 시뮬레이터     | `http://localhost:8080`   |
| Android 에뮬레이터 | `http://10.0.2.2:8080`    |
| 실기기             | 같은 와이파이의 컴퓨터 IP |

## Android 키 해시와 SHA-1

`pnpm expo prebuild -p android`를 한 번 돌린 뒤 debug 키로 구한다. 배포 키는 `pnpm dlx eas-cli credentials`에서 확인한다.

```bash
# 구글: SHA-1
keytool -list -v -keystore android/app/debug.keystore -alias androiddebugkey -storepass android | grep SHA1
# 카카오: 키 해시
keytool -exportcert -alias androiddebugkey -keystore android/app/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
```

## 검사

```bash
pnpm lint && pnpm typecheck && pnpm test
pnpm expo install --check
```
