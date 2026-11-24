import { appleAdapter } from './apple';
import { googleAdapter } from './google';
import { kakaoAdapter } from './kakao';
import { naverAdapter } from './naver';
import type { SocialAdapter } from './types';

// 로그인 화면에 보이는 순서. Apple로만 가입하면 Android에서 이어 쓸 수 없어 마지막에 둔다.
const ADAPTERS: SocialAdapter[] = [kakaoAdapter, naverAdapter, googleAdapter, appleAdapter];

export function availableAdapters(): SocialAdapter[] {
  return ADAPTERS.filter((adapter) => adapter.isAvailable());
}

export async function signOutSocial(): Promise<void> {
  await Promise.allSettled(availableAdapters().map((adapter) => adapter.signOut()));
}
