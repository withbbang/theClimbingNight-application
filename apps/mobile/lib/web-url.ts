import { config } from '@/lib/config';

export type WebPath =
  | '/app/notices'
  | `/app/notices/${number}`
  | '/app/about'
  | '/app/goods'
  | '/app/terms'
  | '/app/privacy';

export function webUrl(path: WebPath): string {
  return `${config.webUrl}${path}`;
}

// React Native의 URL은 주소를 온전히 해석하지 못해 직접 나눈다. 역슬래시는 브라우저마다 달리 읽으므로 받지 않는다.
const WEB = /^(https?):\/\/([^/?#\\\s]+)(\/[^?#\\\s]*)?(?:[?#][^\\\s]*)?$/i;
const DOTS = /\/(?:\.|%2e){1,2}(?:\/|$)/i;
const DEFAULT_PORT: Record<string, string> = { http: ':80', https: ':443' };

function parse(url: string) {
  const match = WEB.exec(url);
  if (!match) {
    return null;
  }
  const scheme = match[1].toLowerCase();
  const host = match[2].toLowerCase();
  const port = DEFAULT_PORT[scheme];
  return {
    origin: `${scheme}://${host.endsWith(port) ? host.slice(0, -port.length) : host}`,
    path: match[3] ?? '/',
  };
}

/** 웹뷰가 열려는 주소를 안에서 열지, 시스템 브라우저로 넘길지, 막을지 정한다. */
export function decideLink(url: string): 'inside' | 'outside' | 'block' {
  const target = parse(url);
  if (!target) {
    return /^(mailto|tel):/i.test(url) ? 'outside' : 'block';
  }
  const ours = target.origin === parse(config.webUrl)?.origin;
  return ours && target.path.startsWith('/app/') && !DOTS.test(target.path) ? 'inside' : 'outside';
}
