package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/** .claude/rules/api.md: Service 클래스에 @Transactional(readOnly = true), 쓰기 메서드만 @Transactional. */
class ServiceTransactionConventionTest {

	private static final List<String> PACKAGES = List.of("com.theclimbingnight.feed", "com.theclimbingnight.moderation",
			"com.theclimbingnight.media", "com.theclimbingnight.comment", "com.theclimbingnight.profile");

	@Test
	void 피드_신고_미디어_서비스는_클래스에_읽기_전용_트랜잭션을_건다() throws Exception {
		// given
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));

		// when
		List<Class<?>> services = new ArrayList<>();
		for (String pkg : PACKAGES) {
			for (BeanDefinition candidate : scanner.findCandidateComponents(pkg)) {
				services.add(ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader()));
			}
		}

		// then
		assertThat(services).isNotEmpty().allSatisfy(service -> {
			Transactional tx = service.getAnnotation(Transactional.class);
			assertThat(tx).as(service.getSimpleName()).isNotNull();
			assertThat(tx.readOnly()).as(service.getSimpleName()).isTrue();
		});
	}
}
