package ai.chat2db.community.domain.core.impl.db;

import ai.chat2db.community.domain.api.config.DBConfig;
import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.sql.Sql;
import ai.chat2db.spi.DefaultMetaService;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.spi.ITableModificationValidator;
import ai.chat2db.community.tools.exception.BusinessException;
import java.sql.Connection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import ai.chat2db.spi.ISqlBuilder;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

class DbTableServiceImplTest {

    private static final String TEST_DB_TYPE = "ISSUE_1830_TEST";
    private static final String CREATE_EXAMPLE = "CREATE TABLE issue_1830 (id BIGINT)";
    private static final String ALTER_EXAMPLE = "ALTER TABLE issue_1830 ADD name VARCHAR(64)";

    private IPlugin previousPlugin;
    private DbTableServiceImpl tableService;

    @BeforeEach
    void setUp() {
        DBConfig dbConfig = new DBConfig();
        dbConfig.setDbType(TEST_DB_TYPE);
        dbConfig.setSimpleCreateTable(CREATE_EXAMPLE);
        dbConfig.setSimpleAlterTable(ALTER_EXAMPLE);

        ISqlBuilder sqlBuilder = new DefaultSqlBuilder() {
            @Override
            public String buildAlterTable(Table oldTable, Table newTable) {
                return EditStatusEnum.MODIFY.name().equals(newTable.getColumnList().get(0).getEditStatus())
                        ? "ALTER TABLE sample_table MODIFY COLUMN content VARCHAR(255) INVISIBLE"
                        : "";
            }
        };
        IPlugin plugin = new IPlugin() {
            @Override
            public DBConfig getDBConfig() {
                return dbConfig;
            }

            @Override
            public IDbMetaData getDbMetaData() {
                return new DefaultMetaService() {
                    @Override
                    public ISqlBuilder getSqlBuilder() {
                        return sqlBuilder;
                    }
                };
            }
        };

        previousPlugin = Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, plugin);
        Chat2DBContext.removeContext();
        tableService = new DbTableServiceImpl(null);
    }

    @AfterEach
    void tearDown() {
        Chat2DBContext.removeContext();
        if (previousPlugin == null) {
            Chat2DBContext.PLUGIN_MAP.remove(TEST_DB_TYPE);
        } else {
            Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, previousPlugin);
        }
    }

    @Test
    void createTableExampleUsesRequestedDatabaseTypeWithoutConnectionContext() {
        assertEquals(CREATE_EXAMPLE, tableService.createTableExample(TEST_DB_TYPE));
    }

    @Test
    void alterTableExampleUsesRequestedDatabaseTypeWithoutConnectionContext() {
        assertEquals(ALTER_EXAMPLE, tableService.alterTableExample(TEST_DB_TYPE));
    }

    @Test
    void columnVisibilityChangeKeepsModifyStatusWhenBuildingSql() {
        putTestContext();
        Table oldTable = tableWithColumn(true, null);
        Table newTable = tableWithColumn(false, EditStatusEnum.MODIFY.name());

        List<Sql> sqlList = tableService.buildSql(oldTable, newTable, TableBuilderConfig.defaultConfig());

        assertFalse(sqlList.get(0).getSql().isBlank());
    }

    @Test
    void dialectValidationRunsBeforeTheBuilderAndReceivesNormalizedOldColumns() {
        List<String> events = new ArrayList<>();
        putValidationPlugin(events, false);
        tableService.buildSql(tableWithColumn(true, null), tableWithColumn(false, "MODIFY"), TableBuilderConfig.defaultConfig());
        assertEquals(List.of("validate", "build"), events);
    }

    @Test
    void rejectedDialectValidationNeverInvokesTheBuilder() {
        List<String> events = new ArrayList<>();
        putValidationPlugin(events, true);
        assertThrows(BusinessException.class, () -> tableService.buildSql(
                tableWithColumn(true, null), tableWithColumn(false, "MODIFY"), TableBuilderConfig.defaultConfig()));
        assertEquals(List.of("validate"), events);
    }

    private void putValidationPlugin(List<String> events, boolean reject) {
        putTestContext();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isClosed")) return false;
                    if (method.getName().equals("close")) return null;
                    throw new AssertionError("Unexpected connection operation: " + method.getName());
                });
        Chat2DBContext.getConnectInfo().setConnection(connection);
        IPlugin original = Chat2DBContext.PLUGIN_MAP.get(TEST_DB_TYPE);
        Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, new IPlugin() {
            public DBConfig getDBConfig() { return original.getDBConfig(); }
            public ITableModificationValidator getTableModificationValidator() {
                return (actual, before, after) -> {
                    assertSame(connection, actual);
                    assertSame(before.getColumnList().get(0), after.getColumnList().get(0).getOldColumn());
                    events.add("validate");
                    if (reject) throw new BusinessException("test.modification.rejected");
                };
            }
            public IDbMetaData getDbMetaData() {
                return new DefaultMetaService() {
                    public ISqlBuilder getSqlBuilder() {
                        return new DefaultSqlBuilder() {
                            public String buildAlterTable(Table before, Table after) { events.add("build"); return "ALTER TABLE t"; }
                        };
                    }
                };
            }
        });
    }

    private static void putTestContext() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(TEST_DB_TYPE);
        DriverConfig driverConfig = new DriverConfig();
        driverConfig.setDbType(TEST_DB_TYPE);
        connectInfo.setDriverConfig(driverConfig);
        Chat2DBContext.putContext(connectInfo);
    }

    private static Table tableWithColumn(Boolean visible, String editStatus) {
        return Table.builder()
                .name("sample_table")
                .columnList(List.of(TableColumn.builder()
                        .name("content")
                        .oldName("content")
                        .columnType("VARCHAR")
                        .columnSize(255)
                        .nullable(0)
                        .visible(visible)
                        .editStatus(editStatus)
                        .build()))
                .indexList(List.of())
                .build();
    }
}
