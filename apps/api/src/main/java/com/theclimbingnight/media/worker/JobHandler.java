package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;

/**
 * 작업 종류 하나를 처리한다. 예외를 던지면 재시도 정책에 따라 다시 하므로, 처리는 여러 번 돌아도 결과가 같아야 한다.
 * 대상 사진 행이 없으면 할 일이 없으므로 정상 종료한다.
 */
public interface JobHandler {

	MediaJobKind kind();

	void handle(MediaJob job) throws Exception;
}
