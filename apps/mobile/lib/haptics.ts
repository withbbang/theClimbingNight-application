import * as Haptics from 'expo-haptics';

export function notifySuccess() {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

export function notifyError() {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
}
