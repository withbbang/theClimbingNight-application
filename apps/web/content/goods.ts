export interface GoodsItem {
  name: string;
  description: string;
  /** public/goods/ 아래 이미지 경로. 가로세로 같은 비율로 넣는다. */
  image: string;
  alt: string;
}

// 가격은 스마트스토어와 어긋나지 않게 싣지 않는다.
export const GOODS: GoodsItem[] = [];
