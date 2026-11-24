package com.theclimbingnight.push;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import com.theclimbingnight.push.service.Pause;

@Configuration
public class PushConfig {

	private static final int TIMEOUT_MILLIS = 5000;

	/** 커밋 뒤 발송 전용. 스레드 1개에 큐 1000개, 종료 때 10초까지 남은 발송을 기다린다. */
	@Bean
	TaskExecutor pushExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("push-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.push", name = "enabled", havingValue = "true")
	RestClient pushRestClient() {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withTimeouts(Duration.ofMillis(TIMEOUT_MILLIS), Duration.ofMillis(TIMEOUT_MILLIS));
		return RestClient.builder()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}

	/** 인터럽트되면 플래그를 되살리고 바로 돌아온다. 호출한 쪽이 플래그를 보고 재시도를 멈춘다. */
	@Bean
	Pause pause() {
		return duration -> {
			try {
				Thread.sleep(duration.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
	}
}
