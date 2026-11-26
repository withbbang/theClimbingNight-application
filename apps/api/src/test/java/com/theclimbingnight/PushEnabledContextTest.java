package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.push.expo.ExpoPushClient;
import com.theclimbingnight.push.expo.PushGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 운영(prod)처럼 푸시를 켠 설정으로도 컨텍스트가 뜨는지 본다. 실제 발송은 하지 않는다. */
@SpringBootTest(properties = {"app.push.enabled=true", "app.push.access-token=test-token"})
class PushEnabledContextTest {

	@Autowired
	ApplicationContext context;

	@Test
	void 푸시를_켜도_컨텍스트가_뜬다() {
		// then
		assertThat(context.getBean(PushGateway.class)).isInstanceOf(ExpoPushClient.class);
	}

}
