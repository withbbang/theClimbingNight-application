package com.theclimbingnight.roster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RosterCsvParserTest {

	private final RosterCsvParser parser = new RosterCsvParser();

	private RosterCsvResult parse(String csv) {
		return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void 머리글과_행을_읽고_앞뒤_공백을_자른다() {
		// given
		String csv = "소모임닉네임,메모\n 철수 , 2019 가입 \n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.errors()).isEmpty();
		assertThat(result.rows()).containsExactly(new RosterCsvRow(2, "철수", "2019 가입"));
	}

	@Test
	void BOM이_있어도_머리글을_인식한다() {
		// given
		String csv = "﻿소모임닉네임,메모\n철수,\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.rows()).containsExactly(new RosterCsvRow(2, "철수", null));
	}

	@Test
	void 메모_열이_없어도_된다() {
		// given
		String csv = "소모임닉네임\n철수\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.errors()).isEmpty();
		assertThat(result.rows()).containsExactly(new RosterCsvRow(2, "철수", null));
	}

	@Test
	void 따옴표로_감싼_쉼표를_한_칸으로_읽는다() {
		// given
		String csv = "소모임닉네임,메모\n\"철수\",\"메모, 쉼표\"\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.rows()).containsExactly(new RosterCsvRow(2, "철수", "메모, 쉼표"));
	}

	@Test
	void 빈_줄은_건너뛰고_줄번호는_파일_기준이다() {
		// given
		String csv = "소모임닉네임\n\n영희\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.rows()).containsExactly(new RosterCsvRow(3, "영희", null));
	}

	@Test
	void 머리글에_소모임닉네임이_없으면_ROSTER_CSV_INVALID다() {
		// given
		String csv = "이름,메모\n철수,x\n";

		// when, then
		assertThatThrownBy(() -> parse(csv))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROSTER_CSV_INVALID));
	}

	@Test
	void 빈_파일이면_ROSTER_CSV_INVALID다() {
		// given, when, then
		assertThatThrownBy(() -> parse(""))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROSTER_CSV_INVALID));
	}

	@Test
	void UTF8이_아니면_ROSTER_CSV_ENCODING이다() {
		// given
		byte[] bytes = "소모임닉네임\n철수\n".getBytes(Charset.forName("MS949"));

		// when, then
		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(bytes)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROSTER_CSV_ENCODING));
	}

	@Test
	void 닉네임_메모_길이_경계() {
		// given
		String nick50 = "가".repeat(50);
		String nick51 = "가".repeat(51);
		String note255 = "a".repeat(255);
		String note256 = "a".repeat(256);
		String csv = "소모임닉네임,메모\n" + nick50 + "," + note255 + "\n"
				+ nick51 + ",\n"
				+ " ,메모\n"
				+ "정상," + note256 + "\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.rows()).containsExactly(new RosterCsvRow(2, nick50, note255));
		assertThat(result.errors()).extracting("line", "reason").containsExactly(
				org.assertj.core.groups.Tuple.tuple(3L, "소모임닉네임은 50자 이하여야 합니다."),
				org.assertj.core.groups.Tuple.tuple(4L, "소모임닉네임이 비어 있습니다."),
				org.assertj.core.groups.Tuple.tuple(5L, "메모는 255자 이하여야 합니다."));
	}

	@Test
	void 열_개수가_다르면_오류_행이다() {
		// given
		String csv = "소모임닉네임,메모\n철수,메모,초과\n";

		// when
		RosterCsvResult result = parse(csv);

		// then
		assertThat(result.rows()).isEmpty();
		assertThat(result.errors()).extracting("line", "reason")
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2L, "열 개수가 머리글과 다릅니다."));
	}

	@Test
	void 데이터_5000행까지_받고_5001행이면_ROSTER_CSV_TOO_MANY_ROWS다() {
		// given
		StringBuilder ok = new StringBuilder("소모임닉네임\n");
		for (int i = 0; i < 5000; i++) {
			ok.append("n").append(i).append('\n');
		}
		String over = ok + "n5000\n";

		// when
		RosterCsvResult result = parse(ok.toString());

		// then
		assertThat(result.rows()).hasSize(5000);
		assertThatThrownBy(() -> parse(over))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROSTER_CSV_TOO_MANY_ROWS));
	}

}
