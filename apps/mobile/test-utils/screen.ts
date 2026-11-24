import { fireEvent, screen } from 'expo-router/testing-library';

import { flush } from '@/test-utils/app';

export const button = (name: string | RegExp) => screen.queryByRole('button', { name });

export async function press(name: string | RegExp) {
  await fireEvent.press(screen.getByRole('button', { name }));
  await flush();
}

export async function type(label: string, text: string) {
  await fireEvent.changeText(screen.getByLabelText(label), text);
}
