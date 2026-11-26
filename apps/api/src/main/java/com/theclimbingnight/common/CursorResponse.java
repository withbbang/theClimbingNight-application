package com.theclimbingnight.common;

import java.util.List;

/** 커서 방식 목록 응답. nextCursor가 null이면 끝이다. */
public record CursorResponse<T>(List<T> items, String nextCursor) {
}
