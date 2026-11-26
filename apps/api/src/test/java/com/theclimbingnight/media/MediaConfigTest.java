package com.theclimbingnight.media;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.theclimbingnight.media.storage.DisabledMediaStorage;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.R2MediaStorage;

class MediaConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(MediaConfig.class);

	private static final String[] ALL_KEYS = {
			"app.media.account-id=acc", "app.media.access-key-id=ak", "app.media.secret-access-key=sk",
			"app.media.incoming-bucket=media-in", "app.media.public-bucket=media-pub" };

	@Test
	void 키가_모두_있으면_R2_저장소가_뜬다() {
		runner.withPropertyValues(ALL_KEYS).run(ctx -> {
			assertThat(ctx).hasSingleBean(MediaStorage.class);
			assertThat(ctx.getBean(MediaStorage.class)).isInstanceOf(R2MediaStorage.class);
		});
	}

	@Test
	void 키_하나라도_비면_비활성_저장소가_뜬다() {
		for (int missing = 0; missing < ALL_KEYS.length; missing++) {
			final int skip = missing;
			String[] keys = java.util.stream.IntStream.range(0, ALL_KEYS.length)
					.filter(i -> i != skip).mapToObj(i -> ALL_KEYS[i]).toArray(String[]::new);
			runner.withPropertyValues(keys).run(ctx ->
					assertThat(ctx.getBean(MediaStorage.class)).isInstanceOf(DisabledMediaStorage.class));
		}
	}

	@Test
	void 설정에_enabled_true를_적어도_키가_비면_꺼져_있다() {
		runner.withPropertyValues("app.media.enabled=true").run(ctx -> {
			assertThat(ctx.getBean(MediaProperties.class).enabled()).isFalse();
			assertThat(ctx.getBean(MediaStorage.class)).isInstanceOf(DisabledMediaStorage.class);
		});
	}
}
