package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.media.worker.ProcessRunner.ProcessResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** 실제 sh 프로세스로 종료 코드·stderr·시간 초과를 본다. 네트워크는 쓰지 않는다. */
@EnabledOnOs({OS.MAC, OS.LINUX})
class FfmpegProcessRunnerTest {

	private final FfmpegProcessRunner runner = new FfmpegProcessRunner();

	@Test
	void 종료_코드와_stderr를_돌려준다() throws Exception {
		// when
		ProcessResult result = runner.run(List.of("sh", "-c", "echo out; echo bad >&2; exit 3"), Duration.ofSeconds(5));

		// then
		assertThat(result.exitCode()).isEqualTo(3);
		assertThat(result.stderrTail()).isEqualTo("bad\n");
	}

	@Test
	void stderr는_마지막_2000자만_남긴다() throws Exception {
		// when
		ProcessResult result = runner.run(
				List.of("sh", "-c", "i=0; while [ $i -lt 300 ]; do printf 0123456789 >&2; i=$((i+1)); done; printf END >&2"),
				Duration.ofSeconds(5));

		// then
		assertThat(result.stderrTail()).hasSize(2000).endsWith("789END");
	}

	@Test
	void 시간을_넘기면_프로세스를_죽이고_예외다() {
		// given
		long started = System.nanoTime();

		// when / then
		assertThatThrownBy(() -> runner.run(List.of("sleep", "10"), Duration.ofMillis(200)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("시간 초과");
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
	}
}
