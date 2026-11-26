package com.theclimbingnight.media.worker;

import com.theclimbingnight.media.worker.ProcessRunner.ProcessResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JPEG 원본을 WebP 두 장(긴 변 1080px 표시용, 480px 썸네일)으로 바꾼다. 원본보다 키우지 않고, 위치 정보 같은 메타데이터는
 * 모두 지운다. 크기는 회전이 반영된 결과물을 ffprobe로 읽는다.
 * <p>
 * {@code -map_metadata -1}은 컨테이너·스트림 메타데이터만 지운다. 최근 ffmpeg는 JPEG EXIF(GPS 포함)를 프레임 사이드
 * 데이터·프레임 태그로 들고 다녀서 인코더가 다시 써 넣을 수 있으므로, 필터에서 {@link #STRIP_FRAME_METADATA}로 함께 지운다.
 */
public class ImageTranscoder {

	static final int DISPLAY_LONG_SIDE = 1080;
	static final int THUMB_LONG_SIDE = 480;
	static final String STRIP_FRAME_METADATA = "sidedata=mode=delete,metadata=mode=delete";
	private static final Pattern SIZE = Pattern.compile("(\\d+)x(\\d+)");

	private final ProcessRunner runner;
	private final Duration timeout;

	public ImageTranscoder(ProcessRunner runner, Duration timeout) {
		this.runner = runner;
		this.timeout = timeout;
	}

	public TranscodedImage transcode(Path source, Path workDir) throws IOException {
		Path display = workDir.resolve("d.webp");
		Path thumb = workDir.resolve("t.webp");
		run(webp(source, display, DISPLAY_LONG_SIDE));
		run(webp(source, thumb, THUMB_LONG_SIDE));
		Path probe = workDir.resolve("probe.txt");
		run(List.of("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
				"-of", "csv=s=x:p=0", "-o", probe.toString(), display.toString()));
		Matcher size = SIZE.matcher(Files.readString(probe).strip());
		if (!size.matches()) {
			throw new IllegalStateException("ffprobe 크기 형식 오류");
		}
		return new TranscodedImage(display, thumb, Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)));
	}

	/** 가로가 길면 너비를, 세로가 길면 높이를 longSide 이하로 맞추고 다른 변은 비율대로(-1) 둔다. */
	static String scale(int longSide) {
		return "scale='if(gte(iw,ih),min(" + longSide + ",iw),-1)':'if(gte(iw,ih),-1,min(" + longSide + ",ih))'";
	}

	private static List<String> webp(Path source, Path out, int longSide) {
		return List.of("ffmpeg", "-nostdin", "-v", "error", "-y", "-threads", "2", "-i", source.toString(),
				"-map_metadata", "-1", "-vf", scale(longSide) + "," + STRIP_FRAME_METADATA,
				"-c:v", "libwebp", "-quality", "80", "-frames:v", "1", out.toString());
	}

	private void run(List<String> command) throws IOException {
		ProcessResult result = runner.run(command, timeout);
		if (result.exitCode() != 0) {
			throw new IllegalStateException(command.get(0) + " 종료 코드 " + result.exitCode() + ": " + result.stderrTail());
		}
	}
}
