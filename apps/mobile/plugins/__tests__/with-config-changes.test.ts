import type { AndroidConfig } from 'expo/config-plugins';

import { addConfigChanges } from '../with-config-changes';

type Manifest = AndroidConfig.Manifest.AndroidManifest;

function manifest(configChanges?: string) {
  return {
    manifest: {
      application: [
        {
          $: { 'android:name': '.MainApplication' },
          activity: [
            {
              $: { 'android:name': '.MainActivity', 'android:configChanges': configChanges },
              'intent-filter': [
                {
                  action: [{ $: { 'android:name': 'android.intent.action.MAIN' } }],
                  category: [{ $: { 'android:name': 'android.intent.category.LAUNCHER' } }],
                },
              ],
            },
          ],
        },
      ],
    },
  } as unknown as Manifest;
}

const changes = (value: Manifest) =>
  value.manifest.application?.[0].activity?.[0].$['android:configChanges'];

it('keeps the activity when the font size, density or language changes', () => {
  expect(changes(addConfigChanges(manifest('keyboard|uiMode')))).toBe(
    'keyboard|uiMode|fontScale|density|locale|layoutDirection',
  );
});

it('does not repeat values', () => {
  expect(changes(addConfigChanges(manifest('uiMode|fontScale')))).toBe(
    'uiMode|fontScale|density|locale|layoutDirection',
  );
});
