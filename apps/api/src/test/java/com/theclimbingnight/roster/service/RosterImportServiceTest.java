package com.theclimbingnight.roster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.roster.dto.RosterImportResponse;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RosterImportServiceTest {

	@Mock
	RosterMemberRepository repository;

	private RosterImportService service() {
		return new RosterImportService(new RosterCsvParser(), repository);
	}

	private java.io.InputStream csv(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	private List<RosterMember> saved() {
		ArgumentCaptor<List<RosterMember>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		return captor.getValue();
	}

	@Test
	void 새_닉네임만_추가하고_기존_닉네임은_건너뛴다() {
		// given
		given(repository.findExistingLowerNicknames(anyCollection())).willReturn(List.of("alpha"));

		// when
		RosterImportResponse response = service().importCsv(csv("소모임닉네임\nAlpha\nbeta\n"));

		// then
		assertThat(response.addedCount()).isEqualTo(1);
		assertThat(response.skipped()).hasSize(1);
		assertThat(response.skipped().get(0).line()).isEqualTo(2);
		assertThat(response.skipped().get(0).somoimNickname()).isEqualTo("Alpha");
		assertThat(response.skipped().get(0).reason()).isEqualTo("이미 명부에 있습니다.");
		assertThat(saved()).extracting(RosterMember::getSomoimNickname).containsExactly("beta");
	}

	@Test
	void 파일_안의_중복은_첫_행만_추가한다() {
		// given
		given(repository.findExistingLowerNicknames(anyCollection())).willReturn(List.of());

		// when
		RosterImportResponse response = service().importCsv(csv("소모임닉네임\n철수\n철수 \n"));

		// then
		assertThat(response.addedCount()).isEqualTo(1);
		assertThat(response.skipped()).hasSize(1);
		assertThat(response.skipped().get(0).line()).isEqualTo(3);
		assertThat(response.skipped().get(0).reason()).isEqualTo("파일 안에서 중복입니다.");
		assertThat(saved()).hasSize(1);
	}

	@Test
	void 오류_행이_있어도_나머지는_등록한다() {
		// given
		given(repository.findExistingLowerNicknames(anyCollection())).willReturn(List.of());

		// when
		RosterImportResponse response = service().importCsv(csv("소모임닉네임,메모\n,빈이름\n영희,\n"));

		// then
		assertThat(response.addedCount()).isEqualTo(1);
		assertThat(response.errors()).hasSize(1);
		assertThat(response.errors().get(0).line()).isEqualTo(2);
		assertThat(saved()).extracting(RosterMember::getSomoimNickname).containsExactly("영희");
	}

	@Test
	void 추가한_항목의_출처는_IMPORT다() {
		// given
		given(repository.findExistingLowerNicknames(anyCollection())).willReturn(List.of());

		// when
		service().importCsv(csv("소모임닉네임,메모\n철수,메모\n"));

		// then
		List<RosterMember> saved = saved();
		assertThat(saved).extracting(RosterMember::getSource).containsExactly(RosterSource.IMPORT);
		assertThat(saved.get(0).getNote()).isEqualTo("메모");
	}

}
