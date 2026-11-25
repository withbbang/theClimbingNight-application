import { decideLink, webUrl } from '@/lib/web-url';

it('opens pages at the API address by default', () => {
  expect(webUrl('/app/notices/3')).toBe('https://api.test/app/notices/3');
});

it.each([
  ['https://api.test/app/notices/1', 'inside'],
  ['https://api.test/app/about?x=1#y', 'inside'],
  ['HTTPS://API.TEST/app/about', 'inside'],
  ['https://api.test:443/app/about', 'inside'],
  ['https://api.test', 'outside'],
  ['https://api.test/application', 'outside'],
  ['https://api.test/api/v1/notices', 'outside'],
  ['https://api.test/app/../api/v1/notices', 'outside'],
  ['https://api.test/app/%2E%2e/api/v1/notices', 'outside'],
  ['https://api.test.evil.com/app/notices', 'outside'],
  ['https://api.test@evil.com/app/notices', 'outside'],
  ['https://user@api.test/app/notices', 'outside'],
  ['https://api.test:8443/app/notices', 'outside'],
  ['http://api.test/app/notices', 'outside'],
  ['https://evil.com?https://api.test/app/', 'outside'],
  ['https://smartstore.naver.com/x', 'outside'],
  ['mailto:a@b.c', 'outside'],
  ['tel:118', 'outside'],
  ['https://api.test\\@evil.com/app/', 'block'],
  ['intent://x#Intent;end', 'block'],
  ['javascript:alert(1)', 'block'],
  ['file:///etc/hosts', 'block'],
  ['data:text/html,x', 'block'],
  ['about:blank', 'block'],
  ['', 'block'],
])('decides %s as %s', (url, decision) => {
  expect(decideLink(url)).toBe(decision);
});
