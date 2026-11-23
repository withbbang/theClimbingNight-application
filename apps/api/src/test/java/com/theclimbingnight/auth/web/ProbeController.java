package com.theclimbingnight.auth.web;

import com.theclimbingnight.user.entity.Role;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProbeController {

	@GetMapping("/api/probe/user")
	Map<String, Object> user(@CurrentUser AuthenticatedUser user) {
		return describe(user);
	}

	@RequireRole(Role.MEMBER)
	@GetMapping("/api/probe/member")
	Map<String, Object> member(@CurrentUser AuthenticatedUser user) {
		return describe(user);
	}

	@PublicApi
	@GetMapping("/api/probe/public")
	Map<String, Object> publicApi(@CurrentUser AuthenticatedUser user) {
		return describe(user);
	}

	@PublicApi
	@RequireRole(Role.ADMIN)
	@GetMapping("/api/probe/public-admin")
	Map<String, Object> publicAdmin(@CurrentUser AuthenticatedUser user) {
		return describe(user);
	}

	private static Map<String, Object> describe(AuthenticatedUser user) {
		Map<String, Object> body = new HashMap<>();
		body.put("id", user == null ? null : user.id());
		body.put("role", user == null ? null : user.role().name());
		body.put("anonymous", user == null);
		return body;
	}

}
