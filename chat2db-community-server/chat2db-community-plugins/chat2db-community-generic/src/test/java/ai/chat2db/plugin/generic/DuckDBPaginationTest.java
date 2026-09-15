package ai.chat2db.plugin.generic;

import ai.chat2db.community.domain.api.config.DBConfig;
import ai.chat2db.plugin.duckdb.builder.DuckDBSqlBuilder;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.ISqlBuilder;
import ai.chat2db.spi.model.request.PageLimitRequest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuckDBPaginationTest {
    @Test
    void onlyDuckDBUsesItsOwnPaginationBuilder() {
        GenericPlugin provider = new GenericPlugin();
        for (DBConfig config : provider.getDBConfigList()) {
            ISqlBuilder builder = provider.getPlugin(config).getDbMetaData().getSqlBuilder();
            if ("DUCKDB".equals(config.getDbType())) {
                assertInstanceOf(DuckDBSqlBuilder.class, builder);
            } else {
                assertEquals(DefaultSqlBuilder.class, builder.getClass(), config.getDbType());
                assertEquals("SELECT 1\n LIMIT 5,3", builder.dql().buildPageLimit(request("SELECT 1", 5, 3)),
                        config.getDbType());
            }
        }
    }

    @Test
    void duckDBExecutesAllPagesWithoutChangingTheDefaultBuilder() throws Exception {
        GenericPlugin provider = new GenericPlugin();
        DBConfig config = provider.getDBConfigList().stream()
                .filter(value -> "DUCKDB".equals(value.getDbType())).findFirst().orElseThrow();
        ISqlBuilder builder = provider.getPlugin(config).getDbMetaData().getSqlBuilder();
        String base = "SELECT range AS id FROM range(8) ORDER BY id";
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement statement = connection.createStatement()) {
                String oldSql = new DefaultSqlBuilder().buildPageLimit(request(base, 3, 3));
                assertThrows(SQLException.class, () -> statement.executeQuery(oldSql));
            }
            for (int offset : List.of(0, 3, 6, 9)) {
                List<Integer> actual = new ArrayList<>();
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery(builder.dql().buildPageLimit(request(base, offset, 3)))) {
                    while (rows.next()) {
                        actual.add(rows.getInt(1));
                    }
                }
                List<Integer> expected = new ArrayList<>();
                for (int i = offset; i < Math.min(offset + 3, 8); i++) {
                    expected.add(i);
                }
                assertEquals(expected, actual, "offset=" + offset);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(builder.dql().buildPageLimit(request(base, -1, 0)))) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
                assertFalse(rows.next());
            }
        }
    }

    private static PageLimitRequest request(String sql, int offset, int size) {
        return PageLimitRequest.builder().sql(sql).offset(offset).pageSize(size).build();
    }
}
