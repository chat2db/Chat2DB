package ai.chat2db.plugin.cockroachdb;

import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.plugin.cockroachdb.builder.CockroachDBSqlBuilder;
import ai.chat2db.spi.ISqlBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CockroachDBMetaDataTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void createDatabaseWithoutCommentUsesCockroachBareSyntax(String comment) {
        ISqlBuilder builder = new CockroachDBMetaData().getSqlBuilder();
        Database database = new Database();
        database.setName("analytics");
        database.setCharset("UTF8");
        database.setCollation("en_US.UTF-8");
        database.setComment(comment);

        String sql = builder.ddl().database().buildCreateDatabase(database);

        assertEquals("CREATE DATABASE \"analytics\"", sql);
        assertFalse(sql.contains("WITH"), sql);
        assertFalse(sql.contains("LC_CTYPE"), sql);
        assertFalse(sql.contains("LC_COLLATE"), sql);
    }

    @Test
    void createDatabasePreservesAndEscapesComment() {
        ISqlBuilder builder = new CockroachDBMetaData().getSqlBuilder();
        Database database = new Database();
        database.setName("report\"ing");
        database.setComment("team's report; --\n多行");

        assertEquals("CREATE DATABASE \"report\"\"ing\"; "
                        + "COMMENT ON DATABASE \"report\"\"ing\" IS 'team''s report; --\n多行';",
                builder.ddl().database().buildCreateDatabase(database));
    }

    @Test
    void metadataWiresCockroachSqlBuilder() {
        assertInstanceOf(CockroachDBSqlBuilder.class, new CockroachDBMetaData().getSqlBuilder());
    }
}
