import type { UserResponse } from './user';

export type SocialProvider = 'KAKAO' | 'NAVER' | 'GOOGLE' | 'APPLE';

export interface TokenResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  user: UserResponse;
}

export type LoginResponse =
  | { status: 'LOGGED_IN'; tokens: TokenResponse }
  | { status: 'SIGNUP_REQUIRED'; signupToken: string };
