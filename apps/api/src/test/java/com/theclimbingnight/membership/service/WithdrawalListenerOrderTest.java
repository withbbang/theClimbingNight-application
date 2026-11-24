package com.theclimbingnight.membership.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.meetup.service.MeetupWithdrawalListener;
import com.theclimbingnight.push.service.PushWithdrawalListener;
import com.theclimbingnight.user.service.UserWithdrawnEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

class WithdrawalListenerOrderTest {

	private static int order(Class<?> type) throws Exception {
		Method on = type.getMethod("on", UserWithdrawnEvent.class);
		Order order = AnnotatedElementUtils.findMergedAnnotation(on, Order.class);
		assertThat(order).as(type.getSimpleName()).isNotNull();
		return order.value();
	}

	@Test
	void 탈퇴_리스너는_벙_명부_푸시_순서로_돈다() throws Exception {
		// given, when
		int meetup = order(MeetupWithdrawalListener.class);
		int membership = order(MembershipWithdrawalListener.class);
		int push = order(PushWithdrawalListener.class);

		// then
		assertThat(meetup).isLessThan(membership);
		assertThat(membership).isLessThan(push);
	}
}
