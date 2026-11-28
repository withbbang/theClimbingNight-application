import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';

import { useRouter } from 'expo-router';

import { Avatar } from '@/components/ui/Avatar';
import { useSession } from '@/hooks/use-session';

interface AuthorLinkProps {
  userId: number;
  nickname: string;
  imageUrl: string | null;
  /** 탈퇴한 사용자는 누를 수 없다. */
  withdrawn?: boolean;
  /** 없으면 아바타를 보여 준다. */
  children?: ReactNode;
}

/** 작성자 아바타·닉네임을 눌러 프로필로 간다. 비로그인이면 로그인 화면으로 보낸다. */
export function AuthorLink({
  userId,
  nickname,
  imageUrl,
  withdrawn = false,
  children,
}: AuthorLinkProps) {
  const router = useRouter();
  const signedIn = useSession().status === 'authenticated';
  const content = children ?? <Avatar nickname={nickname} imageUrl={imageUrl} />;
  if (withdrawn) {
    return <View>{content}</View>;
  }
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={`${nickname} 프로필 보기`}
      hitSlop={4}
      onPress={() => router.push(signedIn ? `/users/${userId}` : '/login')}
    >
      {content}
    </Pressable>
  );
}
