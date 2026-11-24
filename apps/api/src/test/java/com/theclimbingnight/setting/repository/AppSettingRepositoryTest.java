package com.theclimbingnight.setting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.setting.dto.AppSettingsResponse;
import com.theclimbingnight.setting.dto.AppSettingsUpdateRequest;
import com.theclimbingnight.setting.service.AppSettingService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** 직접 정한 문자열 PK라 저장·수정·삭제가 실제 DB에서 되는지 서비스까지 함께 본다. */
@DataJpaTest
@org.springframework.context.annotation.Import({com.theclimbingnight.common.config.ClockConfig.class,
		com.theclimbingnight.common.config.JpaAuditingConfig.class,
		AppSettingService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppSettingRepositoryTest {

	private static final String SOMOIM = "https://somoim.example/join";
	private static final String STORE = "https://store.example";

	@Autowired AppSettingService service;
	@Autowired AppSettingRepository repository;
	@Autowired EntityManager entityManager;

	private AppSettingsResponse update(String somoim, String store) {
		AppSettingsResponse response = service.update(new AppSettingsUpdateRequest(somoim, store));
		entityManager.flush();
		entityManager.clear();
		return response;
	}

	@Test
	void 설정이_없으면_null이다() {
		assertThat(service.get()).isEqualTo(new AppSettingsResponse(null, null));
	}

	@Test
	void 저장한_설정을_소문자_키로_다시_읽는다() {
		// when
		AppSettingsResponse response = update(SOMOIM, STORE);

		// then
		assertThat(response).isEqualTo(new AppSettingsResponse(SOMOIM, STORE));
		assertThat(service.get()).isEqualTo(response);
		assertThat(repository.findAll()).extracting("key")
				.containsExactlyInAnyOrder("somoim_join_url", "smartstore_url");
	}

	@Test
	void 다시_저장하면_행을_늘리지_않고_값을_바꾼다() {
		// given
		update(SOMOIM, STORE);

		// when
		update(SOMOIM + "/2", STORE);

		// then
		assertThat(service.get().somoimJoinUrl()).isEqualTo(SOMOIM + "/2");
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void null이면_그_설정만_지우고_이미_없어도_성공한다() {
		// given
		update(SOMOIM, STORE);

		// when
		update(null, STORE);
		update(null, STORE);

		// then
		assertThat(service.get()).isEqualTo(new AppSettingsResponse(null, STORE));
	}

	@Test
	void 최대_길이_500자를_저장한다() {
		// given
		String longest = "https://" + "a".repeat(492);

		// when
		update(longest, null);

		// then
		assertThat(service.get().somoimJoinUrl()).isEqualTo(longest);
	}
}
