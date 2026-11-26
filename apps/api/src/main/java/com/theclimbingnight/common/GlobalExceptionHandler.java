package com.theclimbingnight.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		if (e.getFields() != null) {
			return ResponseEntity.status(e.getErrorCode().getStatus())
					.body(ApiResponse.failure(e.getErrorCode(), e.getFields()));
		}
		return respond(e.getErrorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<ApiResponse.FieldReason> fields = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiResponse.FieldReason(error.getField(), error.getDefaultMessage()))
				.toList();
		ErrorCode code = ErrorCode.COMMON_VALIDATION_FAILED;
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code, fields));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
		List<ApiResponse.FieldReason> fields = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream().map(error -> new ApiResponse.FieldReason(
						result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
				.toList();
		ErrorCode code = ErrorCode.COMMON_VALIDATION_FAILED;
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code, fields));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestPartException.class, MultipartException.class, MaxUploadSizeExceededException.class })
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception e) {
		return respond(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return respond(ErrorCode.COMMON_NOT_FOUND);
	}

	/** Allow 헤더 등 Spring이 채운 응답 헤더를 그대로 살린다. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		return respond(ErrorCode.COMMON_METHOD_NOT_ALLOWED, e.getHeaders());
	}

	/** Accept 헤더 등 Spring이 채운 응답 헤더를 그대로 살린다. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
		return respond(ErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE, e.getHeaders());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		// 필수 파라미터 누락 등 Spring MVC의 그 밖의 클라이언트 오류는 400으로 돌려준다.
		if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
			return respond(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		log.error("처리되지 않은 예외", e);
		return respond(ErrorCode.COMMON_INTERNAL_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code) {
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code));
	}

	private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, HttpHeaders headers) {
		return ResponseEntity.status(code.getStatus()).headers(headers).body(ApiResponse.failure(code));
	}

}
