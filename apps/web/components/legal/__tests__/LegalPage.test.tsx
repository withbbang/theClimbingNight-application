import { render, screen } from '@testing-library/react';

import { LegalPage } from '@/components/legal/LegalPage';
import { PRIVACY } from '@/content/privacy';
import { TERMS } from '@/content/terms';

it('links each contents entry to its section', () => {
  const { container } = render(<LegalPage title="이용약관" {...TERMS} />);

  for (const { id, title } of TERMS.sections) {
    expect(screen.getByRole('link', { name: title })).toHaveAttribute('href', `#${id}`);
    expect(container.querySelector(`#${id}`)).toContainElement(
      screen.getByRole('heading', { name: title }),
    );
  }
});

it('has the sections the stores need', () => {
  expect(PRIVACY.sections.map(({ id }) => id)).toEqual(
    expect.arrayContaining([
      'collected',
      'purpose',
      'retention',
      'processors',
      'rights',
      'account-deletion',
      'contact',
    ]),
  );
});

it.each([
  ['terms', TERMS],
  ['privacy', PRIVACY],
])('has unique section ids in %s', (_name, { sections }) => {
  const ids = sections.map(({ id }) => id);
  expect(new Set(ids).size).toBe(ids.length);
});

function privacyBody(id: string) {
  return PRIVACY.sections.find((section) => section.id === id)?.body;
}

// apps/api의 ParticipantResponse가 소모임 닉네임을 준다.
it('says participants are shown by somoim nickname', () => {
  expect(privacyBody('shared')).toContain('신청자의 소모임 닉네임과');
});

it.each([
  '회원 번호',
  '탈퇴 시각',
  '지난 신청과 취소 기록',
  '인증 신청 기록, 문의와 답변 | 바로 삭제',
  '기기 알림 토큰이 들어',
])('says what remains after withdrawal: %s', (text) => {
  expect(privacyBody('retention')).toContain(text);
});
