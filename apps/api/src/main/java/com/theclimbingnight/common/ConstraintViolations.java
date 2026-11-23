package com.theclimbingnight.common;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;

/** DB 제약 위반 예외에서 위반된 제약 이름을 찾는 공용 도우미. */
public final class ConstraintViolations {

	private ConstraintViolations() {
	}

	/** 원인 사슬의 제약 이름과 메시지를 소문자로 이어 붙인다. (DB마다 이름이 메시지에만 실리기도 한다.) */
	public static String violated(Throwable e) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				sb.append(cve.getConstraintName()).append(' ');
			}
			if (t.getMessage() != null) {
				sb.append(t.getMessage()).append(' ');
			}
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

}
