package com.theclimbingnight.media.worker;

import com.theclimbingnight.common.FailureSummary;
import com.theclimbingnight.media.job.MediaJobRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 워커 컨테이너의 작업 루프. {@code @Scheduled}가 아니라 스레드 하나로 돈다(동시성 1).
 * 매번 10분 넘게 RUNNING인 작업을 되돌리고 작업 하나를 처리한다. 작업이 있으면 쉬지 않고 다음 작업을, 없으면 pollInterval부터
 * idleMaxInterval까지 두 배씩 늘려 쉰다. 종료 신호를 받으면 지금 작업을 마치고(최대 {@link #STOP_TIMEOUT} 기다림) 멈춘다.
 * {@link #stop()}은 동기라 Spring이 이 메서드가 돌아올 때까지 다음 종료 단계(DB 풀 닫기 등)로 가지 않는다.
 * 데몬이 아닌 스레드라 웹 서버가 없는 워커 프로세스가 이 스레드로 살아 있다.
 */
public class MediaWorkerLoop implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(MediaWorkerLoop.class);
	static final Duration STALE_AFTER = Duration.ofMinutes(10);
	/**
	 * PROCESS 작업은 ffmpeg를 최대 2번(각 30초) 돌리고 R2에 올린다. 그만큼 기다려야 RUNNING에 묶이지 않는다.
	 * worker 프로필의 spring.lifecycle.timeout-per-shutdown-phase(85초)보다 짧고, compose stop_grace_period(90초)보다 짧다.
	 */
	static final Duration STOP_TIMEOUT = Duration.ofSeconds(80);

	private final MediaJobProcessor processor;
	private final MediaJobRepository jobs;
	private final TransactionTemplate tx;
	private final WorkerProperties properties;
	private final Clock clock;
	private final Object monitor = new Object();
	private volatile boolean running;
	private Thread thread;

	public MediaWorkerLoop(MediaJobProcessor processor, MediaJobRepository jobs, TransactionTemplate tx,
			WorkerProperties properties, Clock clock) {
		this.processor = processor;
		this.jobs = jobs;
		this.tx = tx;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void start() {
		running = true;
		thread = new Thread(this::loop, "media-worker");
		thread.setDaemon(false);
		thread.start();
		log.info("미디어 워커 시작 {}", properties);
	}

	@Override
	public void stop() {
		synchronized (monitor) {
			running = false;
			monitor.notifyAll();
		}
		if (thread == null) {
			return;
		}
		try {
			thread.join(STOP_TIMEOUT.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (thread.isAlive()) {
			log.warn("미디어 워커가 {}초 안에 멈추지 않았습니다. 진행 중 작업은 10분 뒤 다시 QUEUED가 됩니다.",
					STOP_TIMEOUT.toSeconds());
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void loop() {
		Duration delay = properties.pollInterval();
		while (running) {
			boolean worked = false;
			try {
				tx.executeWithoutResult(s -> jobs.requeueStale(clock.instant().minus(STALE_AFTER)));
				worked = processor.runNext();
			} catch (RuntimeException e) {
				log.warn("미디어 워커 반복 실패 {}", FailureSummary.of(e));
			}
			if (worked) {
				delay = properties.pollInterval();
				continue;
			}
			pause(delay);
			delay = min(delay.multipliedBy(2), properties.idleMaxInterval());
		}
		log.info("미디어 워커 멈춤");
	}

	private void pause(Duration delay) {
		synchronized (monitor) {
			if (!running) {
				return;
			}
			try {
				monitor.wait(delay.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				running = false;
			}
		}
	}

	private static Duration min(Duration a, Duration b) {
		return a.compareTo(b) <= 0 ? a : b;
	}
}
