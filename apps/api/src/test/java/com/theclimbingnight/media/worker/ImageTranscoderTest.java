package com.theclimbingnight.media.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.media.worker.ProcessRunner.ProcessResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageTranscoderTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Mock ProcessRunner runner;
	@TempDir Path workDir;

	private ImageTranscoder transcoder;
	private Path source;

	@BeforeEach
	void setUp() {
		transcoder = new ImageTranscoder(runner, TIMEOUT);
		source = workDir.resolve("source.jpg");
	}

	/** ffprobe는 -o 다음 경로에 크기를 쓴다. 나머지 명령은 성공만 한다. */
	private void givenRunnerSucceeds() throws Exception {
		given(runner.run(anyList(), eq(TIMEOUT))).willAnswer(inv -> {
			List<String> command = inv.getArgument(0);
			if (command.get(0).equals("ffprobe")) {
				Files.writeString(Path.of(command.get(command.indexOf("-o") + 1)), "1080x810\n");
			}
			return new ProcessResult(0, "");
		});
	}

	@SuppressWarnings("unchecked")
	private List<List<String>> commands() throws Exception {
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(runner, org.mockito.Mockito.times(3)).run(captor.capture(), eq(TIMEOUT));
		return captor.getAllValues();
	}

	@Test
	void 메타데이터를_지우고_스레드_2개로_1080과_480_WebP를_만든다() throws Exception {
		// given
		givenRunnerSucceeds();

		// when
		transcoder.transcode(source, workDir);

		// then
		List<List<String>> commands = commands();
		List<String> display = commands.get(0);
		List<String> thumb = commands.get(1);
		for (List<String> command : List.of(display, thumb)) {
			assertThat(command).startsWith("ffmpeg");
			assertThat(command).containsSubsequence("-threads", "2", "-i", source.toString());
			assertThat(command).containsSubsequence("-map_metadata", "-1");
			assertThat(command).containsSubsequence("-c:v", "libwebp", "-quality", "80");
		}
		assertThat(display).contains(workDir.resolve("d.webp").toString());
		assertThat(display.get(display.indexOf("-vf") + 1))
				.isEqualTo("scale='if(gte(iw,ih),min(1080,iw),-1)':'if(gte(iw,ih),-1,min(1080,ih))'"
						+ ",sidedata=mode=delete,metadata=mode=delete");
		assertThat(thumb).contains(workDir.resolve("t.webp").toString());
		assertThat(thumb.get(thumb.indexOf("-vf") + 1))
				.isEqualTo("scale='if(gte(iw,ih),min(480,iw),-1)':'if(gte(iw,ih),-1,min(480,ih))'"
						+ ",sidedata=mode=delete,metadata=mode=delete");
	}

	@Test
	void 결과_크기는_변환된_표시용_사진을_ffprobe로_읽는다() throws Exception {
		// given
		givenRunnerSucceeds();

		// when
		TranscodedImage image = transcoder.transcode(source, workDir);

		// then
		List<String> probe = commands().get(2);
		assertThat(probe).startsWith("ffprobe").endsWith(workDir.resolve("d.webp").toString());
		assertThat(image.width()).isEqualTo(1080);
		assertThat(image.height()).isEqualTo(810);
		assertThat(image.display()).isEqualTo(workDir.resolve("d.webp"));
		assertThat(image.thumb()).isEqualTo(workDir.resolve("t.webp"));
	}

	@Test
	void 종료_코드가_0이_아니면_stderr_꼬리를_담은_예외다() throws Exception {
		// given
		given(runner.run(anyList(), eq(TIMEOUT))).willReturn(new ProcessResult(1, "Invalid data found"));

		// when / then
		assertThatThrownBy(() -> transcoder.transcode(source, workDir))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1")
				.hasMessageContaining("Invalid data found");
	}
}
