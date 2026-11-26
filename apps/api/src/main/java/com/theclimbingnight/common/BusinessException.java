package com.theclimbingnight.common;

import java.util.List;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<ApiResponse.FieldReason> fields;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	/** 어느 필드가 잘못됐는지 응답에 함께 싣는다. */
	public BusinessException(ErrorCode errorCode, List<ApiResponse.FieldReason> fields) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.fields = fields;
	}

}
