package com.theclimbingnight.comment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CommentTest {

	private Comment comment() {
		return Comment.create(1L, 2L, null, null, "본문");
	}

	@Test
	void 숨기면_대기_여부에_따라_상태가_바뀐다() {
		// given
		Comment pending = comment();
		Comment confirmed = comment();

		// when
		pending.hide(true);
		confirmed.hide(false);

		// then
		assertThat(pending.getStatus()).isEqualTo(CommentStatus.HIDDEN_PENDING);
		assertThat(confirmed.getStatus()).isEqualTo(CommentStatus.HIDDEN);
		assertThat(pending.isHidden()).isTrue();
		assertThat(confirmed.isHidden()).isTrue();
		assertThat(pending.isShown()).isFalse();
	}

	@Test
	void 복원하면_VISIBLE이다() {
		// given
		Comment comment = comment();
		comment.hide(false);

		// when
		comment.restore();

		// then
		assertThat(comment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
		assertThat(comment.isShown()).isTrue();
		assertThat(comment.isHidden()).isFalse();
	}

	@Test
	void 지운_댓글은_보이지_않는다() {
		// given
		Comment comment = comment();
		assertThat(comment.isShown()).isTrue();

		// when
		comment.softDelete(Instant.parse("2026-11-27T00:00:00Z"));

		// then
		assertThat(comment.isShown()).isFalse();
		assertThat(comment.getDeletedAt()).isNotNull();
	}
}
