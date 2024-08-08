package ai.chat2db.plugin.mysql;

import ai.chat2db.plugin.mysql.builder.MysqlSqlBuilder;
import ai.chat2db.plugin.mysql.type.*;
import ai.chat2db.plugin.mysql.value.MysqlValueProcessor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class MysqlMetaData extends DefaultMetaService implements MetaData {

    private List<String> systemDatabases = Arrays.asList("information_schema", "performance_schema", "mysql", "sys");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = SQLExecutor.getInstance().databases(connection);
        return sortDatabase(databases, systemDatabases, connection);
    }


    private static String TABLES_SQL
            = "SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE, VERSION, TABLE_ROWS, DATA_LENGTH, AUTO_INCREMENT, CREATE_TIME, UPDATE_TIME, TABLE_COLLATION, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA = '%s'";
    @Override
    public List<Table> tables(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName) {
        String sql = String.format(TABLES_SQL, databaseName);
        if(StringUtils.isNotBlank(tableName)){
            sql += " AND TABLE_NAME = '" + tableName + "'";
        }
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<Table> tables = new ArrayList<>();
            while (resultSet.next()) {
                Table table = new Table();
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(resultSet.getString("TABLE_NAME"));
                table.setEngine(resultSet.getString("ENGINE"));
                table.setRows(resultSet.getLong("TABLE_ROWS"));
                table.setDataLength(resultSet.getLong("DATA_LENGTH"));
                table.setCreateTime(resultSet.getString("CREATE_TIME"));
                table.setUpdateTime(resultSet.getString("UPDATE_TIME"));
                table.setCollate(resultSet.getString("TABLE_COLLATION"));
                table.setComment(resultSet.getString("TABLE_COMMENT"));
                tables.add(table);
            }
            return tables;
        });
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

    public static String format(String tableName) {
        return "`" + tableName + "`";
    }

    private static String ROUTINES_SQL
            =
            "SELECT SPECIFIC_NAME, ROUTINE_COMMENT, ROUTINE_DEFINITION FROM information_schema.routines WHERE "
                    + "routine_type = '%s' AND ROUTINE_SCHEMA ='%s'  AND "
                    + "routine_name = '%s';";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String functionInfoSql = String.format(ROUTINES_SQL, "FUNCTION", databaseName, functionName);
        Function function = SQLExecutor.getInstance().execute(connection, functionInfoSql, resultSet -> {
            Function f = new Function();
            f.setDatabaseName(databaseName);
            f.setSchemaName(schemaName);
            f.setFunctionName(functionName);
            if (resultSet.next()) {
                f.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                f.setRemarks(resultSet.getString("ROUTINE_COMMENT"));
            }
            return f;
        });
        String functionDDlSql = String.format("SHOW CREATE FUNCTION %s", functionName);
        SQLExecutor.getInstance().execute(connection, functionDDlSql, resultSet -> {
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("Create Function"));
            }
        });
        return function;

    }

    private static String TRIGGER_SQL
            = "SELECT TRIGGER_NAME,EVENT_MANIPULATION, ACTION_STATEMENT  FROM INFORMATION_SCHEMA.TRIGGERS where "
            + "TRIGGER_SCHEMA = '%s' AND TRIGGER_NAME = '%s';";

    private static String TRIGGER_SQL_LIST
            = "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS where TRIGGER_SCHEMA = '%s';";

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
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        String sql = "SHOW PROCEDURE STATUS WHERE Db = DATABASE()";
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            ArrayList<Procedure> procedures = new ArrayList<>();
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setProcedureName(resultSet.getString("Name"));
                procedures.add(procedure);
            }
            return procedures;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String routinesSql = String.format(ROUTINES_SQL, "PROCEDURE", databaseName, procedureName);
        String showCreateProcedureSql = "SHOW CREATE PROCEDURE " + procedureName;
        Procedure procedure = SQLExecutor.getInstance().execute(connection, routinesSql, resultSet -> {
            Procedure p = new Procedure();
            p.setDatabaseName(databaseName);
            p.setSchemaName(schemaName);
            p.setProcedureName(procedureName);
            if (resultSet.next()) {
                p.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                p.setRemarks(resultSet.getString("ROUTINE_COMMENT"));
            }
            return p;
        });
        SQLExecutor.getInstance().execute(connection, showCreateProcedureSql, resultSet -> {
            if (resultSet.next()) {
                procedure.setProcedureBody(resultSet.getString("Create Procedure"));
            }
        });
        return procedure;
    }

    private static String SELECT_TABLE_COLUMNS = "SELECT * FROM information_schema.COLUMNS  WHERE TABLE_SCHEMA =  '%s'  AND TABLE_NAME =  '%s'  order by ORDINAL_POSITION";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS, databaseName, tableName);
        List<TableColumn> tableColumns = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setOldName(resultSet.getString("COLUMN_NAME"));
                column.setName(resultSet.getString("COLUMN_NAME"));
                //column.setColumnType(resultSet.getString("COLUMN_TYPE"));
                column.setColumnType(resultSet.getString("DATA_TYPE").toUpperCase());
                //column.setDataType(resultSet.getInt("DATA_TYPE"));
                column.setDefaultValue(resultSet.getString("COLUMN_DEFAULT"));
                column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("COLUMN_COMMENT"));
                column.setPrimaryKey("PRI".equalsIgnoreCase(resultSet.getString("COLUMN_KEY")));
                column.setNullable("YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")) ? 1 : 0);
                column.setOrdinalPosition(resultSet.getInt("ORDINAL_POSITION"));
                column.setDecimalDigits(resultSet.getInt("NUMERIC_SCALE"));
                column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
                column.setCollationName(resultSet.getString("COLLATION_NAME"));
                setColumnSize(column, resultSet.getString("COLUMN_TYPE"));
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

    private static String VIEW_DDL_SQL = "show create view %s";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_DDL_SQL, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("Create View"));
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
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    columnList.add(getTableIndexColumn(resultSet));
                    columnList = columnList.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition))
                            .collect(Collectors.toList());
                    tableIndex.setColumnList(columnList);
                } else {
                    TableIndex index = new TableIndex();
                    index.setDatabaseName(databaseName);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(keyName);
                    index.setUnique(!resultSet.getBoolean("Non_unique"));
                    index.setType(resultSet.getString("Index_type"));
                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    if ("PRIMARY".equalsIgnoreCase(keyName)) {
                        index.setType(MysqlIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if (index.getUnique()) {
                        index.setType(MysqlIndexTypeEnum.UNIQUE.getName());
                    } else if ("SPATIAL".equalsIgnoreCase(index.getType())) {
                        index.setType(MysqlIndexTypeEnum.SPATIAL.getName());
                    } else if ("FULLTEXT".equalsIgnoreCase(index.getType())) {
                        index.setType(MysqlIndexTypeEnum.FULLTEXT.getName());
                    } else {
                        index.setType(MysqlIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("Column_name"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("Seq_in_index"));
        tableIndexColumn.setCollation(resultSet.getString("Collation"));
        tableIndexColumn.setCardinality(resultSet.getLong("Cardinality"));
        tableIndexColumn.setSubPart(resultSet.getLong("Sub_part"));
        String collation = resultSet.getString("Collation");
        if ("a".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else if ("d".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new MysqlSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(MysqlColumnTypeEnum.getTypes())
                .charsets(MysqlCharsetEnum.getCharsets())
                .collations(MysqlCollationEnum.getCollations())
                .indexTypes(MysqlIndexTypeEnum.getIndexTypes())
                .defaultValues(MysqlDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "`" + name + "`").collect(Collectors.joining("."));
    }

//    @Override
//    public ValueHandler getValueHandler() {
//        return new MysqlValueHandler();
//    }

    @Override
    public ValueProcessor getValueProcessor() {
        return new MysqlValueProcessor();
    }

    @Override
    public List<String> getSystemDatabases() {
        return systemDatabases;
    }

}
