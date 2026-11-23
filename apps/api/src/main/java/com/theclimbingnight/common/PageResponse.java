package com.theclimbingnight.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 페이지 번호 방식 목록 응답. page는 0부터 센다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
