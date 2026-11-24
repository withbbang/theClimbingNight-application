package com.theclimbingnight.common;

import java.sql.SQLException;

/**
 * 로그에 남길 실패 요약. 예외 메시지에는 푸시 토큰 같은 값이 들어 있을 수 있어,
 * 예외 클래스 이름과 DB 오류 코드(SQLState·벤더 번호)만 담는다.
 */
public final class FailureSummary {

	/** 원인 사슬이 순환해도 끝나도록 따라가는 깊이를 제한한다. */
	private static final int MAX_DEPTH = 20;

	private FailureSummary() {
	}

	public static String of(Throwable e) {
		String type = e.getClass().getSimpleName();
		Throwable t = e;
		for (int depth = 0; t != null && depth < MAX_DEPTH; depth++) {
			if (t instanceof SQLException sql) {
				return type + " sqlState=" + sql.getSQLState() + " errorCode=" + sql.getErrorCode();
			}
			t = t.getCause();
		}
		return type;
	}
}
