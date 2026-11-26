import { Tabs } from 'expo-router/js-tabs';

import { useReducedMotion } from 'react-native-reanimated';

import { AnimatedTabBar } from '@/components/navigation/AnimatedTabBar';

export default function TabsLayout() {
  const reduced = useReducedMotion();
  return (
    <Tabs
      tabBar={(props) => <AnimatedTabBar {...props} />}
      screenOptions={{ headerShown: false, animation: reduced ? 'none' : 'shift' }}
    >
      <Tabs.Screen name="index" options={{ title: '홈' }} />
      <Tabs.Screen name="meetups" options={{ title: '벙' }} />
      <Tabs.Screen name="feed" options={{ title: '피드' }} />
      <Tabs.Screen name="goods" options={{ title: '굿즈' }} />
      <Tabs.Screen name="my" options={{ title: '마이' }} />
    </Tabs>
  );
}
