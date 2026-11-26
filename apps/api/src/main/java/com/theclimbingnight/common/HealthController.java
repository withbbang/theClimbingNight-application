package com.theclimbingnight.common;

import com.theclimbingnight.auth.web.PublicApi;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 컨테이너 상태 확인용. DB는 보지 않는다 (mysql은 compose healthcheck가 따로 본다). */
@RestController
public class HealthController {

	@PublicApi
	@GetMapping("/api/v1/health")
	public ApiResponse<Map<String, String>> health() {
		return ApiResponse.success(Map.of("status", "UP"));
	}

}
