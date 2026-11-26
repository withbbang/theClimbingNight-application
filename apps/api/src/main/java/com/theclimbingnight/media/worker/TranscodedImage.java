package com.theclimbingnight.media.worker;

import java.nio.file.Path;

/** width·height는 표시용(display) 사진의 실제 크기다. */
public record TranscodedImage(Path display, Path thumb, int width, int height) {
}
