package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	private static final long USER_ID = 7L;

	@Mock
	UserRepository userRepository;

	@Mock
	com.theclimbingnight.membership.repository.MemberQueryRepository memberQueryRepository;

	private MemberService service;

	@BeforeEach
	void setUp() {
		service = new MemberService(memberQueryRepository, userRepository);
	}

	private User user(Role role) {
		User user = User.create("회원");
		user.changeRole(role);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	@Test
	void 자격을_해제하면_USER가_되고_명부_연결은_남는다() {
		// given: 서비스는 명부 저장소를 의존하지 않으므로 명부 연결은 건드릴 수 없다
		User member = user(Role.MEMBER);
		given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(member));

		// when
		service.revoke(USER_ID);

		// then
		assertThat(member.getRole()).isEqualTo(Role.USER);
		verify(userRepository).findByIdForUpdate(USER_ID);
		verifyNoMoreInteractions(userRepository, memberQueryRepository);
	}

	@Test
	void MEMBER가_아니면_404다() {
		// given
		User plain = user(Role.USER);
		User admin = user(Role.ADMIN);
		User withdrawn = user(Role.MEMBER);
		withdrawn.withdraw(Instant.parse("2026-01-01T00:00:00Z"));
		given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(plain));
		given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(admin));
		given(userRepository.findByIdForUpdate(3L)).willReturn(Optional.of(withdrawn));
		given(userRepository.findByIdForUpdate(4L)).willReturn(Optional.empty());

		// when & then
		for (long id : new long[] {1L, 2L, 3L, 4L}) {
			assertThatThrownBy(() -> service.revoke(id)).isInstanceOf(BusinessException.class)
					.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND);
		}
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(plain.getRole()).isEqualTo(Role.USER);
	}
}
