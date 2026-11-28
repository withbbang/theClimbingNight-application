package com.theclimbingnight.media.worker;

import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.MediaProperties;
import com.theclimbingnight.media.MediaUrls;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.job.MediaJobScheduler;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.profile.repository.ProfileImageRepository;
import com.theclimbingnight.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * 워커 컨테이너(app.worker.enabled=true, worker 프로필)에서만 뜨는 빈. API 컨테이너에는 워커 루프와 처리기가 없다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfig {

	private static final Duration PURGE_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	TransactionTemplate workerTransactionTemplate(PlatformTransactionManager transactionManager) {
		return new TransactionTemplate(transactionManager);
	}

	@Bean
	ProcessRunner processRunner() {
		return new FfmpegProcessRunner();
	}

	@Bean
	ImageTranscoder imageTranscoder(ProcessRunner runner, WorkerProperties properties) {
		return new ImageTranscoder(runner, properties.ffmpegTimeout());
	}

	@Bean
	OriginalStore originalStore(MediaProperties media) {
		return new OriginalStore(media.originalsDir());
	}

	@Bean
	CachePurger cachePurger(WorkerProperties properties) {
		if (!properties.cachePurgeConfigured()) {
			return new NoopCachePurger();
		}
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(PURGE_TIMEOUT, PURGE_TIMEOUT);
		RestClient restClient = RestClient.builder()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
		return new CloudflareCachePurger(restClient, properties.cloudflareZoneId(),
				properties.cloudflareCachePurgeToken());
	}

	@Bean
	RetryPolicy retryPolicy() {
		return new RetryPolicy();
	}

	@Bean
	ProcessJobHandler processJobHandler(MediaStorage storage, OriginalStore originals, ImageTranscoder transcoder,
			PostRepository posts, PostMediaRepository mediaRepository, TransactionTemplate workerTransactionTemplate,
			Clock clock) {
		return new ProcessJobHandler(storage, originals, transcoder, posts, mediaRepository, workerTransactionTemplate,
				clock);
	}

	@Bean
	QuarantineJobHandler quarantineJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			PostRepository posts, PostMediaRepository mediaRepository, TransactionTemplate workerTransactionTemplate) {
		return new QuarantineJobHandler(storage, purger, urls, posts, mediaRepository, workerTransactionTemplate);
	}

	@Bean
	ReleaseJobHandler releaseJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			OriginalStore originals, ProcessJobHandler process, PostRepository posts,
			PostMediaRepository mediaRepository, TransactionTemplate workerTransactionTemplate, Clock clock,
			SecureRandom random) {
		return new ReleaseJobHandler(storage, purger, urls, originals, process, posts, mediaRepository,
				workerTransactionTemplate, clock, random);
	}

	@Bean
	DeleteMediaJobHandler deleteMediaJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			PostMediaRepository mediaRepository, TransactionTemplate workerTransactionTemplate) {
		return new DeleteMediaJobHandler(storage, purger, urls, mediaRepository, workerTransactionTemplate);
	}

	@Bean
	PurgeOriginalJobHandler purgeOriginalJobHandler(OriginalStore originals, PostMediaRepository mediaRepository,
			TransactionTemplate workerTransactionTemplate) {
		return new PurgeOriginalJobHandler(originals, mediaRepository, workerTransactionTemplate);
	}

	@Bean
	ProfileProcessJobHandler profileProcessJobHandler(MediaStorage storage, OriginalStore originals,
			ImageTranscoder transcoder, UserRepository users, ProfileImageRepository images,
			MediaJobScheduler scheduler, TransactionTemplate workerTransactionTemplate, Clock clock) {
		return new ProfileProcessJobHandler(storage, originals, transcoder, users, images, scheduler,
				workerTransactionTemplate, clock);
	}

	@Bean
	ProfileQuarantineJobHandler profileQuarantineJobHandler(MediaStorage storage, CachePurger purger, MediaUrls urls,
			ProfileImageRepository images, TransactionTemplate workerTransactionTemplate) {
		return new ProfileQuarantineJobHandler(storage, purger, urls, images, workerTransactionTemplate);
	}

	@Bean
	ProfileDeleteMediaJobHandler profileDeleteMediaJobHandler(MediaStorage storage, CachePurger purger,
			MediaUrls urls, ProfileImageRepository images, TransactionTemplate workerTransactionTemplate) {
		return new ProfileDeleteMediaJobHandler(storage, purger, urls, images, workerTransactionTemplate);
	}

	@Bean
	ProfilePurgeOriginalJobHandler profilePurgeOriginalJobHandler(OriginalStore originals,
			ProfileImageRepository images, TransactionTemplate workerTransactionTemplate) {
		return new ProfilePurgeOriginalJobHandler(originals, images, workerTransactionTemplate);
	}

	@Bean
	MediaJobProcessor mediaJobProcessor(MediaJobRepository jobs, List<JobHandler> handlers, RetryPolicy retryPolicy,
			PostRepository posts, PostMediaRepository mediaRepository, ProfileImageRepository profileImages,
			UserRepository users, MediaJobScheduler scheduler, TransactionTemplate workerTransactionTemplate,
			Clock clock) {
		return new MediaJobProcessor(jobs, handlers, retryPolicy, posts, mediaRepository, profileImages, users,
				scheduler, workerTransactionTemplate, clock);
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true")
	MediaWorkerLoop mediaWorkerLoop(MediaJobProcessor processor, MediaJobRepository jobs,
			TransactionTemplate workerTransactionTemplate, WorkerProperties properties, Clock clock) {
		return new MediaWorkerLoop(processor, jobs, workerTransactionTemplate, properties, clock);
	}
}
