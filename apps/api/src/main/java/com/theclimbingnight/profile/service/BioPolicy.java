package com.theclimbingnight.profile.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class BioPolicy {

	static final int MAX_CODE_POINTS = 150;
	static final int MAX_NEWLINES = 3;

	/** 줄바꿈을 \n으로 맞추고 앞뒤 공백을 자른다. 비면 null, 150자(코드포인트)·4줄을 넘으면 USER_BIO_INVALID. */
	public String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String bio = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
		if (bio.isEmpty()) {
			return null;
		}
		if (bio.codePointCount(0, bio.length()) > MAX_CODE_POINTS
				|| bio.chars().filter(c -> c == '\n').count() > MAX_NEWLINES) {
			throw new BusinessException(ErrorCode.USER_BIO_INVALID);
		}
		return bio;
	}
}
