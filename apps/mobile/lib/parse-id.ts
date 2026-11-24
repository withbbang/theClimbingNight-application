/** 라우트 파라미터나 푸시 데이터의 ID. 양의 안전한 정수가 아니면 null. */
export function parseId(value: unknown): number | null {
  if (typeof value !== 'number' && !(typeof value === 'string' && /^\d+$/.test(value))) {
    return null;
  }
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
