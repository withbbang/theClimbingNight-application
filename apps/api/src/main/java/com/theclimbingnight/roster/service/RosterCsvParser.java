package com.theclimbingnight.roster.service;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.roster.dto.RosterImportResponse.RowError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * 명부 CSV 파서. 형식: UTF-8(BOM 허용), 첫 줄 머리글 `소모임닉네임,메모`(메모 열은 생략 가능),
 * 빈 줄 무시, 값 앞뒤 공백 제거, 데이터 최대 5,000행.
 */
@Component
public class RosterCsvParser {

	static final int MAX_ROWS = 5000;
	static final int MAX_NICKNAME_LENGTH = 50;
	static final int MAX_NOTE_LENGTH = 255;
	private static final String NICKNAME_HEADER = "소모임닉네임";
	private static final String NOTE_HEADER = "메모";
	private static final char BOM = '﻿';

	private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setTrim(true)
			.setIgnoreEmptyLines(true)
			.get();

	public RosterCsvResult parse(InputStream csv) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv,
				StandardCharsets.UTF_8.newDecoder()
						.onMalformedInput(CodingErrorAction.REPORT)
						.onUnmappableCharacter(CodingErrorAction.REPORT)))) {
			skipBom(reader);
			try (CSVParser parser = FORMAT.parse(reader)) {
				if (!parser.getHeaderMap().containsKey(NICKNAME_HEADER)) {
					throw new BusinessException(ErrorCode.ROSTER_CSV_INVALID);
				}
				return readRows(parser);
			}
		} catch (IOException | UncheckedIOException e) {
			if (isEncodingFailure(e)) {
				throw new BusinessException(ErrorCode.ROSTER_CSV_ENCODING);
			}
			throw e instanceof UncheckedIOException unchecked ? unchecked : new UncheckedIOException((IOException) e);
		} catch (IllegalArgumentException e) {
			// 머리글이 비었거나 열 이름이 비어 있는 경우
			throw new BusinessException(ErrorCode.ROSTER_CSV_INVALID);
		}
	}

	private RosterCsvResult readRows(CSVParser parser) {
		List<RosterCsvRow> rows = new ArrayList<>();
		List<RowError> errors = new ArrayList<>();
		int count = 0;
		for (CSVRecord record : parser) {
			if (++count > MAX_ROWS) {
				throw new BusinessException(ErrorCode.ROSTER_CSV_TOO_MANY_ROWS);
			}
			long line = parser.getCurrentLineNumber();
			String error = validate(record);
			if (error != null) {
				errors.add(new RowError(line, error));
				continue;
			}
			rows.add(new RosterCsvRow(line, record.get(NICKNAME_HEADER), note(record)));
		}
		return new RosterCsvResult(rows, errors);
	}

	private static String validate(CSVRecord record) {
		if (!record.isConsistent()) {
			return "열 개수가 머리글과 다릅니다.";
		}
		String nickname = record.get(NICKNAME_HEADER);
		if (nickname.isEmpty()) {
			return "소모임닉네임이 비어 있습니다.";
		}
		if (nickname.length() > MAX_NICKNAME_LENGTH) {
			return "소모임닉네임은 50자 이하여야 합니다.";
		}
		String note = note(record);
		if (note != null && note.length() > MAX_NOTE_LENGTH) {
			return "메모는 255자 이하여야 합니다.";
		}
		return null;
	}

	private static String note(CSVRecord record) {
		if (!record.isMapped(NOTE_HEADER)) {
			return null;
		}
		String note = record.get(NOTE_HEADER);
		return note.isEmpty() ? null : note;
	}

	private static void skipBom(BufferedReader reader) throws IOException {
		reader.mark(1);
		if (reader.read() != BOM) {
			reader.reset();
		}
	}

	private static boolean isEncodingFailure(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof CharacterCodingException) {
				return true;
			}
		}
		return false;
	}

}
