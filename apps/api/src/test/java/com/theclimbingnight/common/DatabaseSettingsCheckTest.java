package com.theclimbingnight.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseSettingsCheckTest {

	private DatabaseSettingsCheck checkReturning(String isolation) throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		Statement statement = mock(Statement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(dataSource.getConnection()).willReturn(connection);
		given(connection.createStatement()).willReturn(statement);
		given(statement.executeQuery("SELECT @@transaction_isolation")).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getString(1)).willReturn(isolation);
		return new DatabaseSettingsCheck(dataSource);
	}

	@Test
	void 격리_수준이_READ_COMMITTED면_통과한다() throws Exception {
		// given
		DatabaseSettingsCheck check = checkReturning("READ-COMMITTED");

		// when, then
		assertThatCode(() -> check.run(null)).doesNotThrowAnyException();
	}

	@Test
	void 격리_수준이_다르면_기동을_멈춘다() throws Exception {
		// given
		DatabaseSettingsCheck check = checkReturning("REPEATABLE-READ");

		// when, then
		assertThatThrownBy(() -> check.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("REPEATABLE-READ");
	}

}
