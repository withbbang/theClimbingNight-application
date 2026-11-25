'use client';

import Image from 'next/image';

import { Notice } from '@/components/ui/Notice';
import { Reveal } from '@/components/ui/Reveal';
import { SettingLink, settingsFailed } from '@/components/ui/SettingLink';
import type { GoodsItem } from '@/content/goods';
import { useAppSettings } from '@/hooks/use-app-settings';

export function Goods({ items }: { items: GoodsItem[] }) {
  const settings = useAppSettings();
  const storeUrl = settings.data?.smartstoreUrl;
  const showStore = storeUrl || settingsFailed(settings);
  const store = (label: string) =>
    showStore ? (
      <SettingLink
        query={settings}
        url={storeUrl}
        label={label}
        failedMessage="스토어 링크를 불러오지 못했어요."
      />
    ) : null;

  if (items.length === 0) {
    return (
      <Notice
        title="굿즈를 준비하고 있어요"
        description="판매를 시작하면 이곳에서 볼 수 있어요."
        action={store('스마트스토어 보기')}
      />
    );
  }
  return (
    <>
      <ul className="grid grid-cols-1 gap-x-6 gap-y-10 sm:grid-cols-2">
        {items.map(({ name, description, image, alt }) => (
          <li key={name} className="rise">
            <Image
              src={image}
              alt={alt}
              width={640}
              height={640}
              unoptimized
              className="aspect-square w-full rounded-card bg-surface object-cover"
            />
            <h2 className="mt-4 text-lg font-bold">{name}</h2>
            <p className="text-muted">{description}</p>
          </li>
        ))}
      </ul>
      {showStore ? <Reveal className="mt-10">{store('스마트스토어에서 구매하기')}</Reveal> : null}
    </>
  );
}
