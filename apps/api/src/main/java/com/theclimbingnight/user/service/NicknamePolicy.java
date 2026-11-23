package com.theclimbingnight.user.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NicknamePolicy {

	private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z0-9_]{2,16}$");

	/** 앞뒤 공백을 제거하고 규칙(2~16자, 한글·영문·숫자·밑줄)을 검사한다. */
	public String normalize(String raw) {
		if (raw == null) {
			throw new BusinessException(ErrorCode.USER_NICKNAME_INVALID);
		}
		String nickname = raw.strip();
		if (!ALLOWED.matcher(nickname).matches()) {
			throw new BusinessException(ErrorCode.USER_NICKNAME_INVALID);
		}
		return nickname;
	}

}
