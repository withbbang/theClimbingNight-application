import { useNavigation, useRouter } from 'expo-router';

const AUTH_ROUTES = ['login', 'signup'];

/** 로그인과 가입 화면을 걷어내고 그 전에 보던 화면으로 돌아간다. */
export function useLeaveAuth() {
  const router = useRouter();
  const navigation = useNavigation();
  return () => {
    const routes = navigation.getState()?.routes ?? [];
    const below = routes.findLastIndex(({ name }) => !AUTH_ROUTES.includes(name));
    const count = routes.length - 1 - below;
    if (below >= 0 && count > 0) {
      router.dismiss(count);
    } else {
      // 돌아갈 화면이 없거나, 그사이 다른 화면이 위에 열렸다.
      router.dismissTo('/');
    }
  };
}
