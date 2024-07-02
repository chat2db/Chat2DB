package ai.chat2db.plugin.xugudb;

import ai.chat2db.plugin.xugudb.builder.XUGUDBSqlBuilder;
import ai.chat2db.plugin.xugudb.type.XUGUDBColumnTypeEnum;
import ai.chat2db.plugin.xugudb.type.XUGUDBDefaultValueEnum;
import ai.chat2db.plugin.xugudb.type.XUGUDBIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class XUGUDBMetaData extends DefaultMetaService implements MetaData {
    private List<String> systemDatabases = Arrays.asList("information_schema", "performance_schema", "sys");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = SQLExecutor.getInstance().databases(connection);
        return sortDatabase(databases, systemDatabases, connection);
    }

    private List<String> systemSchemas = Arrays.asList("CTISYS", "SYS", "SYSDBA", "SYSSSO", "SYSAUDITOR");

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private String format(String tableName) {
        return "\"" + tableName + "\"";
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = """
                SELECT
                    (SELECT dbms_metadata.get_ddl('%s.%s') FROM dual) AS ddl
                FROM dual;
                """;
        StringBuilder ddlBuilder = new StringBuilder();
        String tableDDLSql = String.format(sql, schemaName, tableName);
        SQLExecutor.getInstance().execute(connection, tableDDLSql, resultSet -> {
            if (resultSet.next()) {
                String ddl = resultSet.getString("ddl");
                ddlBuilder.append(ddl).append("\n");
            }
        });
        return ddlBuilder.toString();
    }

    private static String FUNCTIONS_SQL
            = "select p.PROC_NAME From ALL_PROCEDURES p LEFT JOIN DBA_DATABASES DB ON p.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON p.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and p.RET_TYPE is not null ";


    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = new ArrayList<>();
        String sql = String.format(FUNCTIONS_SQL, databaseName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Function function = new Function();
                function.setDatabaseName(databaseName);
                function.setSchemaName(schemaName);
                function.setFunctionName(resultSet.getString("PROC_NAME"));
                functions.add(function);
            }
            return functions;
        });
    }

    private static String ROUTINES_SQL
            = "select DB.DB_NAME, CH.SCHEMA_NAME, p.PROC_NAME, p.DEFINE From ALL_PROCEDURES p LEFT JOIN DBA_DATABASES DB ON p.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON p.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and p.PROC_NAME = '%s' and p.RET_TYPE is not null ";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, databaseName, schemaName, functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("DEFINE") + "\n");
            }
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            function.setFunctionBody(sb.toString());
            return function;

        });

    }

    private static String PROCEDURE_SQL
            = "select DB.DB_NAME, CH.SCHEMA_NAME, p.PROC_NAME, p.DEFINE From ALL_PROCEDURES p LEFT JOIN DBA_DATABASES DB ON p.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON p.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and p.PROC_NAME = '%s' and p.RET_TYPE is null ";

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(PROCEDURE_SQL, databaseName, schemaName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("DEFINE") + "\n");
            }
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            procedure.setProcedureBody(sb.toString());
            return procedure;
        });
    }

    private static String PROCEDURES_SQL
            = "select p.PROC_NAME From ALL_PROCEDURES p LEFT JOIN DBA_DATABASES DB ON p.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON p.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and p.RET_TYPE is null ";


    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = new ArrayList<>();
        String sql = String.format(PROCEDURES_SQL, databaseName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setDatabaseName(databaseName);
                procedure.setSchemaName(schemaName);
                procedure.setProcedureName(resultSet.getString("PROC_NAME"));
                procedures.add(procedure);
            }
            return procedures;
        });
    }


    private static String TRIGGER_SQL
            = "select t.trig_name,t.DEFINE from all_triggers t LEFT JOIN DBA_DATABASES DB ON t.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON t.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and t.trig_name = '%s'";

    private static String TRIGGER_SQL_LIST = "select t.trig_name from all_triggers t LEFT JOIN DBA_DATABASES DB ON t.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON t.SCHEMA_ID = CH.SCHEMA_ID where  DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s'";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, databaseName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("trig_name"));
                trigger.setSchemaName(schemaName);
                trigger.setDatabaseName(databaseName);
                triggers.add(trigger);
            }
            return triggers;
        });
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, databaseName, schemaName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("DEFINE"));
            }
            return trigger;
        });
    }

    private static String VIEW_SQL_LIST = "SELECT DB.DB_NAME, CH.SCHEMA_NAME, v.VIEW_NAME, v.DEFINE, v.OPTION,v.VALID, v.IS_SYS, v.COMMENTS FROM all_views v LEFT JOIN DBA_DATABASES DB ON v.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON v.SCHEMA_ID = CH.SCHEMA_ID where DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s'";

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        String sql = String.format(VIEW_SQL_LIST, databaseName, schemaName);
        List<Table> tables = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            while (resultSet.next()) {
                table.setName(resultSet.getString("VIEW_NAME"));
                table.setDdl(resultSet.getString("DEFINE"));
                tables.add(table);
            }
            return tables;
        });

    }

    private static String VIEW_SQL
            = " SELECT CH.SCHEMA_NAME, v.VIEW_NAME, v.DEFINE FROM ALL_VIEWS v LEFT JOIN DBA_DATABASES DB ON v.DB_ID = DB.DB_ID LEFT JOIN DBA_SCHEMAS CH ON v.SCHEMA_ID = CH.SCHEMA_ID where DB.DB_NAME = '%s' and CH.SCHEMA_NAME = '%s' and v.VIEW_NAME = '%s'";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, databaseName, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("DEFINE"));
            }
            return table;
        });
    }

    private static String INDEX_SQL = "SELECT i.INDEX_NAME, CASE i.INDEX_TYPE WHEN 0 THEN 'BTREE' WHEN 1 THEN 'RTREE' WHEN 2 THEN 'FULLTEXT' WHEN 3 THEN 'BITMAP' WHEN 4 THEN 'UNION' END AS INDEX_TYPE, i.IS_PRIMARY,"
            + " i.IS_UNIQUE, i.FIELD_NUM, REPLACE (KEYS, '\"', '') AS KEYS FROM ALL_INDEXES i LEFT JOIN ALL_TABLES T ON i.TABLE_ID = T.TABLE_ID LEFT JOIN ALL_SCHEMAS CH ON CH.USER_ID = T.USER_ID AND CH.DB_ID = i.DB_ID"
            + " where CH.SCHEMA_NAME = '%s' and T.TABLE_NAME = '%s'";

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(INDEX_SQL, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("INDEX_NAME");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    tableIndex.setColumnList(getTableIndexColumn(resultSet));
                } else {
                    TableIndex index = new TableIndex();
                    index.setDatabaseName(databaseName);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(keyName);
                    index.setUnique((resultSet.getBoolean("IS_UNIQUE")));
//                    index.setType(resultSet.getString("Index_type"));
//                    index.setComment(resultSet.getString("Index_comment"));
                    index.setColumnList(getTableIndexColumn(resultSet));
                    if (resultSet.getBoolean("IS_PRIMARY")) {
                        index.setType(XUGUDBIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if (index.getUnique()) {
                        index.setType(XUGUDBIndexTypeEnum.UNIQUE.getName());
                    } else if ("BTREE".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        index.setType(XUGUDBIndexTypeEnum.BTREE.getName());
                    } else {
                        index.setType(XUGUDBIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private List<TableIndexColumn> getTableIndexColumn(ResultSet resultSet) throws SQLException {
        List<TableIndexColumn> tableIndexColumnList = new ArrayList<>();
        String[] keys = resultSet.getString("KEYS").split(",");
        for (String key : keys) {
            TableIndexColumn tableIndexColumn = new TableIndexColumn();
            tableIndexColumn.setColumnName(key);
            // TODO 先临时赋值0
            tableIndexColumn.setOrdinalPosition((short) 0);
            tableIndexColumnList.add(tableIndexColumn);
        }
        return tableIndexColumnList;
    }


    private static String SELECT_TABLE_COLUMNS = "select c.* from ALL_COLUMNS c LEFT JOIN ALL_TABLES T ON c.TABLE_ID = T.TABLE_ID LEFT JOIN ALL_SCHEMAS CH ON CH.USER_ID = T.USER_ID AND CH.DB_ID = c.DB_ID where CH.SCHEMA_NAME = '%s' and T.TABLE_NAME = '%s' order by c.COL_NO";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS, schemaName, tableName);
        List<TableColumn> tableColumns = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setOldName(resultSet.getString("COL_NAME"));
                column.setName(resultSet.getString("COL_NAME"));
                //column.setColumnType(resultSet.getString("COLUMN_TYPE"));
                if (resultSet.getBoolean("VARYING")) {
                    if (resultSet.getString("TYPE_NAME").toUpperCase().equals(XUGUDBColumnTypeEnum.CHAR.name())) {
                        column.setColumnType("VAR" + resultSet.getString("TYPE_NAME").toUpperCase());
                    } else {
                        column.setColumnType(resultSet.getString("TYPE_NAME").toUpperCase());
                    }
                } else {
                    column.setColumnType(resultSet.getString("TYPE_NAME").toUpperCase());
                }

                //column.setDataType(resultSet.getInt("DATA_TYPE"));
                column.setDefaultValue(resultSet.getString("DEF_VAL"));
                //column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("COMMENTS"));
                // column.setPrimaryKey("PRI".equalsIgnoreCase(resultSet.getString("COLUMN_KEY")));
                column.setNullable(resultSet.getBoolean("NOT_NULL") ? 0 : 1);
                column.setOrdinalPosition(resultSet.getInt("SCALE"));
                // column.setDecimalDigits(resultSet.getInt("NUMERIC_SCALE"));
                //column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
                // column.setCollationName(resultSet.getString("COLLATION_NAME"));
                column.setColumnSize(resultSet.getInt("SCALE") == -1 ? null : resultSet.getInt("SCALE"));
                tableColumns.add(column);
            }
            return tableColumns;
        });
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new XUGUDBSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(XUGUDBColumnTypeEnum.getTypes())
                .charsets(Lists.newArrayList())
                .collations(Lists.newArrayList())
                .indexTypes(XUGUDBIndexTypeEnum.getIndexTypes())
                .defaultValues(XUGUDBDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        if (Arrays.stream(names).count() > 1) {
            return Arrays.stream(names).skip(1).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
        }
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }


    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }

}
