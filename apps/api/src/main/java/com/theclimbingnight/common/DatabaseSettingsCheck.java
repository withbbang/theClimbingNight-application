package com.theclimbingnight.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영 MySQL 연결이 READ COMMITTED인지 기동할 때 확인한다 (application.yml의 hikari 설정).
 * 다르면 잠금 뒤 조회가 낡은 스냅숏을 볼 수 있으므로 기동을 멈춘다.
 */
@Component
@Profile("prod")
public class DatabaseSettingsCheck implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DatabaseSettingsCheck.class);
	private static final String EXPECTED = "READ-COMMITTED";

	private final DataSource dataSource;

	public DatabaseSettingsCheck(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		String isolation;
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT @@transaction_isolation")) {
			isolation = resultSet.next() ? resultSet.getString(1) : null;
		}
		if (!EXPECTED.equals(isolation)) {
			throw new IllegalStateException("MySQL 격리 수준이 READ-COMMITTED가 아닙니다: " + isolation);
		}
		log.info("MySQL 격리 수준 확인: {}", isolation);
	}

}
