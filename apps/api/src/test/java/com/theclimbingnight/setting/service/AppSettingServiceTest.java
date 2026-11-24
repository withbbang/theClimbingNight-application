package com.theclimbingnight.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.theclimbingnight.setting.dto.AppSettingsResponse;
import com.theclimbingnight.setting.dto.AppSettingsUpdateRequest;
import com.theclimbingnight.setting.entity.AppSetting;
import com.theclimbingnight.setting.entity.AppSettingKey;
import com.theclimbingnight.setting.repository.AppSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppSettingServiceTest {

	@Mock AppSettingRepository appSettingRepository;

	private AppSettingService service;

	@BeforeEach
	void setUp() {
		service = new AppSettingService(appSettingRepository);
	}

	@Test
	void 값이_없으면_null이다() {
		// given
		given(appSettingRepository.findById("somoim_join_url")).willReturn(Optional.empty());
		given(appSettingRepository.findById("smartstore_url"))
				.willReturn(Optional.of(AppSetting.create(AppSettingKey.SMARTSTORE_URL, "https://store.example")));

		// when
		AppSettingsResponse response = service.get();

		// then
		assertThat(response.somoimJoinUrl()).isNull();
		assertThat(response.smartstoreUrl()).isEqualTo("https://store.example");
	}

	@Test
	void null로_바꾸면_행을_지운다() {
		// given
		AppSetting smartstore = AppSetting.create(AppSettingKey.SMARTSTORE_URL, "https://old.example");
		given(appSettingRepository.findById("smartstore_url")).willReturn(Optional.of(smartstore));
		given(appSettingRepository.findById("somoim_join_url")).willReturn(Optional.empty());

		// when
		service.update(new AppSettingsUpdateRequest(null, "https://new.example"));

		// then
		verify(appSettingRepository).deleteById("somoim_join_url");
		assertThat(smartstore.getValue()).isEqualTo("https://new.example");
	}
}
