package com.theclimbingnight.media.worker;

import java.time.Duration;
import java.util.List;

/** 외부 명령(ffmpeg·ffprobe)을 실행한다. 시간을 넘기면 프로세스를 죽이고 예외를 던진다. */
public interface ProcessRunner {

	ProcessResult run(List<String> command, Duration timeout) throws java.io.IOException;

	/** stderrTail은 stderr의 마지막 2,000자. */
	record ProcessResult(int exitCode, String stderrTail) {
	}
}
