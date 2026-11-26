import { View } from 'react-native';

import { useRouter, type Href } from 'expo-router';

import { AdminScreen } from '@/components/admin/AdminScreen';
import { MenuRow } from '@/components/my/MenuRow';

const MENUS: { label: string; href: Href }[] = [
  { label: '벙 열기', href: '/admin/meetups/new' },
  { label: '인증 심사', href: '/admin/requests' },
  { label: '회원 목록', href: '/admin/members' },
  { label: '블랙리스트', href: '/admin/penalties' },
  { label: '공지 관리', href: '/admin/notices' },
  { label: '문의 답변', href: '/admin/inquiries' },
  { label: '신고 목록', href: '/admin/reports' },
];

export default function AdminMenuScreen() {
  const router = useRouter();
  return (
    <AdminScreen title="운영진 메뉴" scroll>
      <View>
        {MENUS.map(({ label, href }, i) => (
          <MenuRow key={label} label={label} index={i + 1} onPress={() => router.push(href)} />
        ))}
      </View>
    </AdminScreen>
  );
}
