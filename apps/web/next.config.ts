import type { NextConfig } from 'next';

const apiOrigin = process.env.API_ORIGIN ?? 'http://localhost:8080';

const config: NextConfig = {
  output: 'standalone',
  poweredByHeader: false,
  // Android 에뮬레이터가 호스트를 부르는 주소. 개발 서버에만 쓰인다.
  allowedDevOrigins: ['10.0.2.2'],
  // 개발용. 운영에서는 nginx가 /api를 API로 보낸다.
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${apiOrigin}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Content-Security-Policy', value: "frame-ancestors 'none'" },
        ],
      },
    ];
  },
};

export default config;
