package ai.chat2db.plugin.clickhouse;

import ai.chat2db.plugin.clickhouse.builder.ClickHouseSqlBuilder;
import ai.chat2db.plugin.clickhouse.type.ClickHouseColumnTypeEnum;
import ai.chat2db.plugin.clickhouse.type.ClickHouseEngineTypeEnum;
import ai.chat2db.plugin.clickhouse.type.ClickHouseIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
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

public class ClickHouseMetaData extends DefaultMetaService implements MetaData {


    private static String ROUTINES_SQL
            =
            "SELECT SPECIFIC_NAME, ROUTINE_COMMENT, ROUTINE_DEFINITION FROM information_schema.routines WHERE "
                    + "routine_type = '%s' AND ROUTINE_SCHEMA ='%s'  AND "
                    + "routine_name = '%s';";
    private static String TRIGGER_SQL
            = "SELECT TRIGGER_NAME,EVENT_MANIPULATION, ACTION_STATEMENT  FROM INFORMATION_SCHEMA.TRIGGERS where "
            + "TRIGGER_SCHEMA = '%s' AND TRIGGER_NAME = '%s';";
    private static String TRIGGER_SQL_LIST
            = "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS where TRIGGER_SCHEMA = '%s';";
    private static String SELECT_TABLE_COLUMNS = "select * from `system`.columns where table ='%s' and database='%s';";
    private static String VIEW_SQL
            = "SELECT create_table_query from system.`tables` WHERE `database`='%s' and name='%s'";
    private List<String> systemDatabases = Arrays.asList("information_schema", "system");
    public static final String FUNCTION_SQL = "SELECT name,create_query as ddl from system.functions where origin='SQLUserDefined'";

    public static String format(String tableName) {
        return "`" + tableName + "`";
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().execute(connection, FUNCTION_SQL, resultSet -> {
            List<Function> functions = new ArrayList<>();
            while (resultSet.next()) {
                Function function = new Function();
                function.setFunctionName(resultSet.getString("name"));
                functions.add(function);
            }
            return functions;
        });
    }

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> list = SQLExecutor.getInstance().execute(connection, "SELECT name FROM system.databases;;", resultSet -> {
            List<Database> databases = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    String dbName = resultSet.getString("name");
                    Database database = new Database();
                    database.setName(dbName);
                    databases.add(database);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return databases;
        });
        return sortDatabase(list, systemDatabases, connection);
    }

    @Override
    public String tableDDL(Connection connection, @NotEmpty String databaseName, String schemaName,
                           @NotEmpty String tableName) {
        String sql = "SHOW CREATE TABLE " + format(databaseName) + "."
                + format(tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                return resultSet.getString("Create Table");
            }
            return null;
        });
    }

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {
        return SQLExecutor.getInstance().execute(connection, FUNCTION_SQL, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
/*                function.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                function.setRemarks(resultSet.getString("ROUTINE_COMMENT"));*/
                function.setFunctionBody(resultSet.getString("ddl"));
            }
            return function;
        });

    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, databaseName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("TRIGGER_NAME"));
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

        String sql = String.format(TRIGGER_SQL, databaseName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("ACTION_STATEMENT"));
            }
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", databaseName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            if (resultSet.next()) {
                procedure.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                procedure.setRemarks(resultSet.getString("ROUTINE_COMMENT"));
                procedure.setProcedureBody(resultSet.getString("ROUTINE_DEFINITION"));
            }
            return procedure;
        });
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS, tableName, databaseName);
        List<TableColumn> tableColumns = new ArrayList<>();

        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setOldName(resultSet.getString("name"));
                column.setName(resultSet.getString("name"));
                String dataType = resultSet.getString("type");
                if (dataType.startsWith("Nullable(")) {
                    dataType = dataType.substring(9, dataType.length() - 1);
                    column.setNullable(1);
                }
                column.setColumnType(dataType);
                column.setDefaultValue(resultSet.getString("default_expression"));
//                column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("comment"));
                column.setOrdinalPosition(resultSet.getInt("position"));
                column.setDecimalDigits(resultSet.getInt("numeric_scale"));
                /*column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
                column.setCollationName(resultSet.getString("COLLATION_NAME"));*/
                setColumnSize(column, dataType);
                tableColumns.add(column);
            }
            return tableColumns;
        });
    }

    private void setColumnSize(TableColumn column, String columnType) {
        try {
            if (columnType.contains("(")) {
                String size = columnType.substring(columnType.indexOf("(") + 1, columnType.indexOf(")"));
                if ("SET".equalsIgnoreCase(column.getColumnType()) || "ENUM".equalsIgnoreCase(column.getColumnType())) {
                    column.setValue(size);
                } else {
                    if (size.contains(",")) {
                        String[] sizes = size.split(",");
                        if (StringUtils.isNotBlank(sizes[0])) {
                            column.setColumnSize(Integer.parseInt(sizes[0]));
                        }
                        if (StringUtils.isNotBlank(sizes[1])) {
                            column.setDecimalDigits(Integer.parseInt(sizes[1]));
                        }
                    } else {
                        column.setColumnSize(Integer.parseInt(size));
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, databaseName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("create_table_query"));
            }
            return table;
        });
    }


    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        StringBuilder queryBuf = new StringBuilder("SHOW INDEX FROM ");
        queryBuf.append("`").append(tableName).append("`");
        queryBuf.append(" FROM ");
        queryBuf.append("`").append(databaseName).append("`");
        return SQLExecutor.getInstance().execute(connection, queryBuf.toString(), resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("Key_name");

                TableIndex index = new TableIndex();
                index.setDatabaseName(databaseName);
                index.setSchemaName(schemaName);
                index.setTableName(tableName);
                index.setName(keyName);
                index.setUnique(!resultSet.getBoolean("Non_unique"));
                index.setType(resultSet.getString("Index_type"));
//                    index.setComment(resultSet.getString("Index_comment"));
                List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                tableIndexColumns.addAll(getTableIndexColumn(resultSet));
                index.setColumnList(tableIndexColumns);
                if ("PRIMARY".equalsIgnoreCase(keyName)) {
                    index.setType(ClickHouseIndexTypeEnum.PRIMARY.getName());
                }
                map.put(keyName, index);
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private List<TableIndexColumn> getTableIndexColumn(ResultSet resultSet) throws SQLException {
        List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
        String name = StringUtils.isBlank(resultSet.getString("column_name")) ? resultSet.getString("expression") : resultSet.getString("column_name");
        if (StringUtils.isNotBlank(name)) {
            String[] split = name.split(",");
            for (String columName : split) {
                TableIndexColumn tableIndexColumn = new TableIndexColumn();
                tableIndexColumn.setColumnName(columName);
                tableIndexColumn.setOrdinalPosition(resultSet.getShort("seq_in_index"));
                tableIndexColumn.setCollation(resultSet.getString("collation"));
                tableIndexColumn.setCardinality(resultSet.getLong("cardinality"));
                tableIndexColumn.setSubPart(resultSet.getLong("sub_part"));
                tableIndexColumns.add(tableIndexColumn);
            }
        }
        return tableIndexColumns;
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new ClickHouseSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(ClickHouseColumnTypeEnum.getTypes())
                .engineTypes(ClickHouseEngineTypeEnum.getTypes())
                .indexTypes(ClickHouseIndexTypeEnum.getIndexTypes())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names)
                .skip(1) // 跳过第一个名称
                .filter(StringUtils::isNotBlank)
                .map(name -> "`" + name + "`")
                .collect(Collectors.joining("."));
    }


    @Override
    public List<String> getSystemDatabases() {
        return systemDatabases;
    }


}
