package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.auth.service.MutableClock;
import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.membership.dto.MembershipRequestResponse;
import com.theclimbingnight.membership.entity.MembershipRequest;
import com.theclimbingnight.membership.entity.MembershipRequestStatus;
import com.theclimbingnight.membership.repository.MembershipRequestRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock UserRepository userRepository;
	@Mock MembershipRequestRepository membershipRequestRepository;

	private MembershipService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new MembershipService(userRepository, membershipRequestRepository, new MutableClock(NOW));
		user = User.create("climber");
		ReflectionTestUtils.setField(user, "id", 7L);
		given(userRepository.findByIdForUpdate(7L)).willReturn(Optional.of(user));
		given(membershipRequestRepository.save(any(MembershipRequest.class))).willAnswer(inv -> {
			MembershipRequest saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 1L);
			return saved;
		});
	}

	@Test
	void 신청하면_공백을_자르고_PENDING으로_저장한다() {
		// given, when
		MembershipRequestResponse response = service.apply(7L, "  철수  ");

		// then
		ArgumentCaptor<MembershipRequest> captor = ArgumentCaptor.forClass(MembershipRequest.class);
		verify(membershipRequestRepository).save(captor.capture());
		assertThat(captor.getValue().getSomoimNickname()).isEqualTo("철수");
		assertThat(captor.getValue().getStatus()).isEqualTo(MembershipRequestStatus.PENDING);
		assertThat(response.somoimNickname()).isEqualTo("철수");
		assertThat(response.status()).isEqualTo(MembershipRequestStatus.PENDING);
	}

	@Test
	void 심사_중_신청이_있으면_409다() {
		// given
		given(membershipRequestRepository.existsByUserIdAndStatus(7L, MembershipRequestStatus.PENDING))
				.willReturn(true);

		// when, then
		assertThatThrownBy(() -> service.apply(7L, "철수"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBERSHIP_REQUEST_PENDING));
		verify(membershipRequestRepository, never()).save(any());
	}

	@Test
	void 거절된_뒤에는_다시_신청할_수_있다() {
		// given
		given(membershipRequestRepository.existsByUserIdAndStatus(7L, MembershipRequestStatus.PENDING))
				.willReturn(false);

		// when
		MembershipRequestResponse response = service.apply(7L, "철수");

		// then
		assertThat(response.status()).isEqualTo(MembershipRequestStatus.PENDING);
		verify(membershipRequestRepository).save(any(MembershipRequest.class));
	}

	@Test
	void MEMBER나_ADMIN이면_409다() {
		for (Role role : new Role[] {Role.MEMBER, Role.ADMIN}) {
			// given
			user.changeRole(role);

			// when, then
			assertThatThrownBy(() -> service.apply(7L, "철수"))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBERSHIP_ALREADY_MEMBER));
		}
		verify(membershipRequestRepository, never()).save(any());
	}

	@Test
	void 신청할_때_사용자_행을_잠근다() {
		// when
		service.apply(7L, "철수");

		// then
		verify(userRepository).findByIdForUpdate(7L);
	}

	@Test
	void 내_최근_신청을_돌려주고_없으면_비어있다() {
		// given
		MembershipRequest request = MembershipRequest.create(user, "철수");
		ReflectionTestUtils.setField(request, "id", 3L);
		given(membershipRequestRepository.findFirstByUserIdOrderByIdDesc(7L)).willReturn(Optional.of(request));
		given(membershipRequestRepository.findFirstByUserIdOrderByIdDesc(8L)).willReturn(Optional.empty());

		// when, then
		assertThat(service.getMine(7L)).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(3L));
		assertThat(service.getMine(8L)).isEmpty();
	}
}
