package com.theclimbingnight.media.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ProcessBuilder로 명령을 실행한다. stdout은 버리고(ffprobe는 -o로 파일에 쓴다), stderr는 임시 파일에 받아 꼬리만 돌려준다.
 * 파이프를 읽는 스레드가 따로 없어도 출력이 많아 멈추는 일이 없다.
 */
public class FfmpegProcessRunner implements ProcessRunner {

	static final int STDERR_TAIL_CHARS = 2000;

	@Override
	public ProcessResult run(List<String> command, Duration timeout) throws IOException {
		Path stderr = Files.createTempFile("media-proc-", ".log");
		try {
			Process process = new ProcessBuilder(command)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(stderr.toFile())
					.start();
			boolean finished;
			try {
				finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				process.destroyForcibly();
				Thread.currentThread().interrupt();
				throw new IllegalStateException(command.get(0) + " 실행 중 중단됨", e);
			}
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalStateException(command.get(0) + " 시간 초과(" + timeout.toSeconds() + "초)");
			}
			return new ProcessResult(process.exitValue(), tail(stderr));
		} finally {
			Files.deleteIfExists(stderr);
		}
	}

	private static String tail(Path file) throws IOException {
		String all = Files.readString(file, StandardCharsets.UTF_8);
		return all.length() <= STDERR_TAIL_CHARS ? all : all.substring(all.length() - STDERR_TAIL_CHARS);
	}
}
