import { redirectSystemPath } from '../app/+native-intent';

it.each([
  'kakaoabc://oauth?code=SECRET',
  'KAKAOabc://oauth?code=SECRET',
  'theclimbingnightnaver://thirdPartyLoginResult?code=0',
  'com.googleusercontent.apps.1-a:/oauth2redirect?code=SECRET',
])('does not turn the login callback %s into a route', (path) => {
  expect(redirectSystemPath({ path, initial: false })).toBeNull();
  expect(redirectSystemPath({ path, initial: true })).toBe('/');
});

it.each(['theclimbingnight:///meetups', '/meetups', 'https://example.com/a'])(
  'keeps %s',
  (path) => {
    expect(redirectSystemPath({ path, initial: false })).toBe(path);
    expect(redirectSystemPath({ path, initial: true })).toBe(path);
  },
);
