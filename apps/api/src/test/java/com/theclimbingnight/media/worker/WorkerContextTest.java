package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.ApiApplication;
import com.theclimbingnight.common.config.SchedulingConfig;
import com.theclimbingnight.meetup.service.MeetupReminderJob;
import com.theclimbingnight.push.service.PushReceiptJob;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.web.context.WebApplicationContext;

/**
 * worker 프로필로 실제 기동해 본다. 테스트 JVM에는 모든 컨텍스트가 같이 쓰는 {@code app.scheduling.enabled=false}
 * 시스템 속성이 있어서, 기동하는 동안만 그 속성을 빼고 application-worker.yml 값이 쓰이는지 본다.
 * 워커 루프가 공유 H2의 작업을 가져가지 않게 별도 메모리 DB를 쓰고, 테스트에서만 Flyway를 켠다.
 */
class WorkerContextTest {

	private static final String SCHEDULING = "app.scheduling.enabled";

	private static ConfigurableApplicationContext startWorker() {
		String saved = System.clearProperty(SCHEDULING);
		try {
			SpringApplication app = new SpringApplication(ApiApplication.class);
			app.setAdditionalProfiles("local", "worker");
			return app.run("--spring.datasource.url=jdbc:h2:mem:worker-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
					"--spring.flyway.enabled=true");
		} finally {
			if (saved != null) {
				System.setProperty(SCHEDULING, saved);
			}
		}
	}

	@Test
	void worker_프로필은_예약_작업을_등록하지_않고_웹_서버_없이_워커_루프만_돈다() {
		try (ConfigurableApplicationContext context = startWorker()) {
			// then: 기존 @Scheduled 빈은 있지만 하나도 예약되지 않았다
			assertThat(context.getBeansOfType(PushReceiptJob.class)).hasSize(1);
			assertThat(context.getBeansOfType(MeetupReminderJob.class)).hasSize(1);
			assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
					.allSatisfy(holder -> assertThat(holder.getScheduledTasks()).isEmpty());
			assertThat(context.getBean(MediaWorkerLoop.class).isRunning()).isTrue();
			assertThat(context).isNotInstanceOf(WebServerApplicationContext.class)
					.isNotInstanceOf(WebApplicationContext.class);
			assertThat(context.getEnvironment().getProperty("app.worker.poll-interval", Duration.class))
					.isEqualTo(Duration.ofSeconds(2));
		}
	}

	@Test
	void 예약_작업_설정은_app_scheduling_enabled로_켜고_끈다() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(SchedulingConfig.class, Ticker.class);

		// when / then
		runner.withPropertyValues(SCHEDULING + "=false")
				.run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskHolder.class));
		runner.withPropertyValues(SCHEDULING + "=true")
				.run(context -> assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(1));
	}

	@Test
	void 종료_대기는_ffmpeg_두_번과_업로드를_기다리고_compose_유예_90초_안에_끝난다() throws Exception {
		// given
		Duration graceperiod = Duration.ofSeconds(90);
		var yaml = new org.springframework.boot.env.YamlPropertySourceLoader()
				.load("worker", new org.springframework.core.io.ClassPathResource("application-worker.yml")).get(0);
		Duration ffmpeg = org.springframework.boot.convert.DurationStyle.detectAndParse(
				String.valueOf(yaml.getProperty("app.worker.ffmpeg-timeout")));
		Object phase = yaml.getProperty("spring.lifecycle.timeout-per-shutdown-phase");

		// then: 작업 스레드는 ffmpeg 2번보다 오래 기다리고, 단계 제한은 그보다 길되 compose 유예보다 짧다
		assertThat(MediaWorkerLoop.STOP_TIMEOUT).isGreaterThan(ffmpeg.multipliedBy(2)).isLessThan(graceperiod);
		assertThat(phase).as("spring.lifecycle.timeout-per-shutdown-phase").isNotNull();
		Duration phaseTimeout = org.springframework.boot.convert.DurationStyle.detectAndParse(String.valueOf(phase));
		assertThat(phaseTimeout).isGreaterThan(MediaWorkerLoop.STOP_TIMEOUT).isLessThan(graceperiod);
	}

	static class Ticker {

		@Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
		void tick() {
		}
	}
}
