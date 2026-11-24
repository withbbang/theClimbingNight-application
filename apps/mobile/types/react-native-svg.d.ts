import 'react-native-svg';

// phosphor 아이콘 소스를 직접 가져오면 타입 검사 대상이 되는데, 소스가 쓰는 className이 빠져 있다.
declare module 'react-native-svg' {
  interface SvgProps {
    className?: string;
  }
}
