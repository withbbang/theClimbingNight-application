package com.theclimbingnight.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ThrowingController {

	@GetMapping("/test/forbidden")
	void forbidden() {
		throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
	}

	@PostMapping("/test/valid")
	void valid(@Valid @RequestBody NameRequest request) {
	}

	@GetMapping("/test/boom")
	void boom() {
		throw new RuntimeException("secret");
	}

	record NameRequest(@NotBlank String name) {
	}

}
