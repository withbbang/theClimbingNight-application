package com.theclimbingnight.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** 잠금 뒤 일반 조회가 커밋된 행을 보도록 연결 격리 수준을 READ COMMITTED로 고정한다 (application.yml). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionIsolationTest {

	@Autowired
	DataSource dataSource;

	@Test
	void 커넥션_풀이_READ_COMMITTED로_설정된다() {
		// when, then (H2는 기본값이 READ COMMITTED라 연결만 보면 설정이 빠져도 통과하므로 풀 설정을 직접 본다)
		assertThat(dataSource).isInstanceOf(HikariDataSource.class);
		assertThat(((HikariDataSource) dataSource).getTransactionIsolation()).isEqualTo("TRANSACTION_READ_COMMITTED");
	}

	@Test
	void 연결은_READ_COMMITTED로_열린다() throws Exception {
		// when
		try (Connection connection = dataSource.getConnection()) {
			// then
			assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
		}
	}
}
