package ai.chat2db.plugin.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import ai.chat2db.plugin.sqlite.builder.SqliteBuilder;
import ai.chat2db.plugin.sqlite.type.SqliteCollationEnum;
import ai.chat2db.plugin.sqlite.type.SqliteColumnTypeEnum;
import ai.chat2db.plugin.sqlite.type.SqliteDefaultValueEnum;
import ai.chat2db.plugin.sqlite.type.SqliteIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.TableMeta;
import ai.chat2db.spi.sql.SQLExecutor;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

public class SqliteMetaData extends DefaultMetaService implements MetaData {
    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + tableName + "'";
        return SQLExecutor.getInstance().execute(connection,sql, resultSet -> {
            try {
                if (resultSet.next()) {
                    return resultSet.getString("sql");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
    @Override
    public List<Database> databases(Connection connection) {
        return Lists.newArrayList(Database.builder().name("main").build());
    }

    @Override
    public List<Schema> schemas(Connection connection,String databaseName) {
        return Lists.newArrayList();
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new SqliteBuilder();
    }
    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(SqliteColumnTypeEnum.getTypes())
                .charsets(null)
                .collations(SqliteCollationEnum.getCollations())
                .indexTypes(SqliteIndexTypeEnum.getIndexTypes())
                .defaultValues(SqliteDefaultValueEnum.getDefaultValues())
                .build();
    }


    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }
}
