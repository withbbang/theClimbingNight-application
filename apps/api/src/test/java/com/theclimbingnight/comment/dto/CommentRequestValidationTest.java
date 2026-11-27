package com.theclimbingnight.comment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CommentRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void 공백뿐인_본문은_거절한다() {
		// given
		CommentCreateRequest request = new CommentCreateRequest("  \n\t ", null);

		// when / then
		assertThat(validator.validate(request)).isNotEmpty();
	}

	@Test
	void 본문_500자는_되고_501자는_거절한다() {
		// given
		CommentCreateRequest ok = new CommentCreateRequest("가".repeat(500), null);
		CommentCreateRequest tooLong = new CommentCreateRequest("가".repeat(501), null);

		// when / then
		assertThat(validator.validate(ok)).isEmpty();
		assertThat(validator.validate(tooLong)).isNotEmpty();
	}
}
