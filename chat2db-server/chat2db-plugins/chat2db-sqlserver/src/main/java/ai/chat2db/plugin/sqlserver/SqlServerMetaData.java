package ai.chat2db.plugin.sqlserver;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.sqlserver.builder.SqlServerSqlBuilder;
import ai.chat2db.plugin.sqlserver.type.SqlServerColumnTypeEnum;
import ai.chat2db.plugin.sqlserver.type.SqlServerDefaultValueEnum;
import ai.chat2db.plugin.sqlserver.type.SqlServerIndexTypeEnum;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class SqlServerMetaData extends DefaultMetaService implements MetaData {


    private List<String> systemDatabases = Arrays.asList("master", "model", "msdb", "tempdb");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = SQLExecutor.getInstance().databases(connection);
        return sortDatabase(databases, systemDatabases, connection);
    }

    private List<String> systemSchemas = Arrays.asList("guest", "INFORMATION_SCHEMA", "sys", "db_owner",
            "db_accessadmin", "db_securityadmin", "db_ddladmin", "db_backupoperator", "db_datareader", "db_datawriter",
            "db_denydatareader", "db_denydatawriter");

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private String functionSQL
            = "CREATE FUNCTION tableSchema.ufn_GetCreateTableScript( @schema_name NVARCHAR(128), @table_name NVARCHAR"
            + "(128)) RETURNS NVARCHAR(MAX) AS BEGIN DECLARE @CreateTableScript NVARCHAR(MAX); DECLARE @IndexScripts "
            + "NVARCHAR(MAX) = ''; DECLARE @ColumnDescriptions NVARCHAR(MAX) = N''; SELECT @CreateTableScript = CONCAT( "
            + "'CREATE TABLE [', s.name, '].[' , t.name, '] (', STUFF( ( SELECT ', [' + c.name + '] ' + tp.name + CASE "
            + "WHEN tp.name IN ('varchar', 'nvarchar', 'char', 'nchar') THEN '(' + IIF(c.max_length = -1, 'MAX', CAST(c"
            + ".max_length AS NVARCHAR(10))) + ')' WHEN tp.name IN ('decimal', 'numeric') THEN '(' + CAST(c.precision AS "
            + "NVARCHAR(10)) + ', ' + CAST(c.scale AS NVARCHAR(10)) + ')' ELSE '' END + ' ' + CASE WHEN c.is_nullable = 1"
            + " THEN 'NULL' ELSE 'NOT NULL' END FROM sys.columns c JOIN sys.types tp ON c.user_type_id = tp.user_type_id "
            + "WHERE c.object_id = t.object_id FOR XML PATH(''), TYPE ).value('/', 'nvarchar(max)'), 1, 1, ''), ');' ) "
            + "FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE t.name = @table_name AND s.name = "
            + "@schema_name; SELECT @IndexScripts = @IndexScripts + 'CREATE ' + CASE WHEN i.is_unique = 1 THEN 'UNIQUE ' "
            + "ELSE '' END + i.type_desc + ' INDEX [' + i.name + '] ON [' + s.name + '].[' + t.name + '] (' + STUFF( ( "
            + "SELECT ', [' + c.name + ']' + CASE WHEN ic.is_descending_key = 1 THEN ' DESC' ELSE ' ASC' END FROM sys"
            + ".index_columns ic JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id WHERE ic"
            + ".object_id = i.object_id AND ic.index_id = i.index_id ORDER BY ic.key_ordinal FOR XML PATH('') ), 1, 1, "
            + "'') + ')' + CASE WHEN i.has_filter = 1 THEN ' WHERE ' + i.filter_definition ELSE '' END + ';' + CHAR(13) +"
            + " CHAR(10) FROM sys.indexes i JOIN sys.tables t ON i.object_id = t.object_id JOIN sys.schemas s ON t"
            + ".schema_id = s.schema_id WHERE i.type > 0 AND t.name = @table_name AND s.name "
            + "= @schema_name; SELECT @ColumnDescriptions += 'EXEC sp_addextendedproperty @name=N''MS_Description'', "
            + "@value=N''' + CAST(p.value AS NVARCHAR(MAX)) + ''', @level0type=N''SCHEMA'', @level0name=N''' + "
            + "@schema_name + ''', @level1type=N''TABLE'', @level1name=N''' + @table_name + ''', @level2type=N''COLUMN'',"
            + " @level2name=N''' + c.name + ''';' + CHAR(13) + CHAR(10) FROM sys.extended_properties p JOIN sys.columns c"
            + " ON p.major_id = c.object_id AND p.minor_id = c.column_id JOIN sys.tables t ON c.object_id = t.object_id "
            + "JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE p.class = 1 AND t.name = @table_name AND s.name = "
            + "@schema_name; SET @CreateTableScript = @CreateTableScript + CHAR(13) + CHAR(10) + @IndexScripts + CHAR(13)"
            + " + CHAR(10)+ @ColumnDescriptions+ CHAR(10); RETURN @CreateTableScript; END";

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            SQLExecutor.getInstance().execute(connection, functionSQL.replace("tableSchema", schemaName),
                    resultSet -> null);
        } catch (Exception e) {
            //log.error("创建函数失败", e);
        }

        String ddlSql = "SELECT " + schemaName + ".ufn_GetCreateTableScript('" + schemaName + "', '" + tableName
                + "') AS sql";
        return SQLExecutor.getInstance().execute(connection, ddlSql, resultSet -> {
            if (resultSet.next()) {
                return resultSet.getString("sql");
            }
            return null;
        });
    }

    private static String SELECT_TABLES_SQL = "SELECT t.name AS TableName, mm.value as comment FROM sys.tables t LEFT JOIN(SELECT * from sys.extended_properties ep where ep.minor_id = 0 AND ep.name = 'MS_Description') mm ON t.object_id = mm.major_id WHERE t.schema_id= SCHEMA_ID('%S') ";

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Table> tables = new ArrayList<>();
        String sql = String.format(SELECT_TABLES_SQL, schemaName);
        if (StringUtils.isNotBlank(tableName)) {
            sql += " AND t.name = '" + tableName + "'";
        }
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Table table = new Table();
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(resultSet.getString("TableName"));
                table.setComment(resultSet.getString("comment"));
                tables.add(table);
            }
            return tables;
        });
    }

    private static final String SELECT_TABLE_COLUMNS = "SELECT c.name as COLUMN_NAME , c.is_nullable as IS_NULLABLE ,c.column_id as ORDINAL_POSITION,c.max_length as COLUMN_SIZE, c.scale as NUMERIC_SCALE, c.collation_name as COLLATION_NAME, ty.name as DATA_TYPE ,t.name, def.definition as COLUMN_DEFAULT, ep.value as COLUMN_COMMENT from sys.columns c LEFT JOIN sys.tables t on c.object_id=t.object_id LEFT JOIN sys.types ty ON c.user_type_id = ty.user_type_id LEFT JOIN sys.default_constraints def ON c.default_object_id = def.object_id LEFT JOIN sys.extended_properties ep ON t.object_id = ep.major_id AND c.column_id = ep.minor_id WHERE t.name ='%s' and t.schema_id=SCHEMA_ID('%s');";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS, tableName, schemaName);
        List<TableColumn> tableColumns = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setSchemaName(schemaName);
                column.setOldName(resultSet.getString("COLUMN_NAME"));
                column.setName(resultSet.getString("COLUMN_NAME"));
                //column.setColumnType(resultSet.getString("COLUMN_TYPE"));
                column.setColumnType(resultSet.getString("DATA_TYPE").toUpperCase());
                //column.setDataType(resultSet.getInt("DATA_TYPE"));
                column.setDefaultValue(resultSet.getString("COLUMN_DEFAULT"));
                //column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("COLUMN_COMMENT"));
                // column.setPrimaryKey("PRI".equalsIgnoreCase(resultSet.getString("COLUMN_KEY")));
                column.setNullable(resultSet.getInt("IS_NULLABLE"));
                column.setOrdinalPosition(resultSet.getInt("ORDINAL_POSITION"));
                column.setDecimalDigits(resultSet.getInt("NUMERIC_SCALE"));
                // column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
                column.setCollationName(resultSet.getString("COLLATION_NAME"));
                column.setColumnSize(resultSet.getInt("COLUMN_SIZE"));
                //setColumnSize(column, resultSet.getString("COLUMN_TYPE"));
                tableColumns.add(column);
            }
            return tableColumns;
        });
    }

    private static String ROUTINES_SQL
            = "SELECT type_desc, OBJECT_NAME(object_id) AS FunctionName, OBJECT_DEFINITION(object_id) AS "
            + "definition FROM sys.objects WHERE type_desc IN(%s) and name = '%s' ;";


    private static String OBJECT_SQL
            = "SELECT name FROM sys.objects WHERE type = '%s' and SCHEMA_ID = SCHEMA_ID('%s');";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "'SQL_SCALAR_FUNCTION', 'SQL_TABLE_VALUED_FUNCTION'", functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("definition"));
            }
            return function;
        });
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = new ArrayList<>();
        String sql = String.format(OBJECT_SQL, "FN", schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Function function = new Function();
                function.setDatabaseName(databaseName);
                function.setSchemaName(schemaName);
                function.setFunctionName(resultSet.getString("name"));
                functions.add(function);
            }
            return functions;
        });
    }

    private Function removeVersion(Function function) {
        String fullFunctionName = function.getFunctionName();
        if (!StringUtils.isEmpty(fullFunctionName)) {
            String[] parts = fullFunctionName.split(";");
            String functionName = parts[0];
            function.setFunctionName(functionName);
        }
        return function;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = new ArrayList<>();
        String sql = String.format(OBJECT_SQL, "P", schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setDatabaseName(databaseName);
                procedure.setSchemaName(schemaName);
                procedure.setProcedureName(resultSet.getString("name"));
                procedures.add(procedure);
            }
            return procedures;
        });
    }

    private Procedure removeVersion(Procedure procedure) {
        String fullProcedureName = procedure.getProcedureName();
        if (!StringUtils.isEmpty(fullProcedureName)) {
            String[] parts = fullProcedureName.split(";");
            String procedureName = parts[0];
            procedure.setProcedureName(procedureName);
        }
        return procedure;
    }

    private static String TRIGGER_SQL
            = "SELECT OBJECT_NAME(parent_obj) AS TableName, name AS triggerName, OBJECT_DEFINITION(id) AS "
            + "triggerDefinition, CASE WHEN status & 1 = 1 THEN 'Enabled' ELSE 'Disabled' END AS Status FROM sysobjects "
            + "WHERE xtype = 'TR' and name = '%s';";

    private static String TRIGGER_SQL_LIST
            = "SELECT OBJECT_NAME(parent_obj) AS TableName, name AS triggerName, OBJECT_DEFINITION(id) AS "
            + "triggerDefinition, CASE WHEN status & 1 = 1 THEN 'Enabled' ELSE 'Disabled' END AS Status FROM sysobjects "
            + "WHERE xtype = 'TR' ";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, TRIGGER_SQL_LIST, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("triggerName"));
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

        String sql = String.format(TRIGGER_SQL, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("triggerDefinition"));
            }
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "'SQL_STORED_PROCEDURE'", procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                    Procedure procedure = new Procedure();
                    procedure.setDatabaseName(databaseName);
                    procedure.setSchemaName(schemaName);
                    procedure.setProcedureName(procedureName);
                    if (resultSet.next()) {
                        procedure.setProcedureBody(resultSet.getString("definition"));
                    }
                    return procedure;
                }
        );
    }

    private static String VIEW_SQL
            = "SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = '%s' "
            + "AND TABLE_NAME = '%s';";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("VIEW_DEFINITION"));
            }
            return table;
        });
    }

    private static final String INDEX_SQL = "SELECT ic.key_ordinal AS COLUMN_POSITION, ic.is_descending_key as DESCEND , ind.name AS INDEX_NAME, ind.is_unique AS IS_UNIQUE, col.name AS COLUMN_NAME, ind.type_desc AS INDEX_TYPE, ind.is_primary_key AS IS_PRIMARY FROM sys.indexes ind INNER JOIN sys.index_columns ic ON ind.object_id = ic.object_id and ind.index_id = ic.index_id and ic.key_ordinal>0 INNER JOIN sys.columns col ON ic.object_id = col.object_id and ic.column_id = col.column_id INNER JOIN sys.tables t ON ind.object_id = t.object_id LEFT JOIN sys.key_constraints kc ON ind.object_id = kc.parent_object_id AND ind.index_id = kc.unique_index_id WHERE t.name = '%s' and t.schema_id= SCHEMA_ID('%s') ORDER BY t.name, ind.name, ind.index_id, ic.index_column_id";

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(INDEX_SQL, tableName,schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("INDEX_NAME");
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
                    int isunique = resultSet.getInt("IS_UNIQUE");
                    if(isunique ==1){
                        index.setUnique(true);
                    }else {
                        index.setUnique(false);
                    }
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String indexType = resultSet.getString("INDEX_TYPE");
                    if (resultSet.getBoolean("IS_PRIMARY")) {
                        index.setType(SqlServerIndexTypeEnum.PRIMARY_KEY.getName());
                    }else if("CLUSTERED".equalsIgnoreCase(indexType)){
                        if(index.getUnique()){
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_CLUSTERED.getName());
                        }else {
                            index.setType(SqlServerIndexTypeEnum.CLUSTERED.getName());
                        }
                    }else if("NONCLUSTERED".equalsIgnoreCase(indexType)){
                        if(index.getUnique()){
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_NONCLUSTERED.getName());
                        }else {
                            index.setType(SqlServerIndexTypeEnum.NONCLUSTERED.getName());
                        }
                    }else {
                        index.setType(indexType);
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLUMN_NAME"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("COLUMN_POSITION"));
        int collation = resultSet.getInt("DESCEND");
        if (collation == 1) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }


    @Override
    public SqlBuilder getSqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(SqlServerColumnTypeEnum.getTypes())
                .charsets(null)
                .collations(null)
                .indexTypes(SqlServerIndexTypeEnum.getIndexTypes())
                .defaultValues(SqlServerDefaultValueEnum.getDefaultValues())
                .build();
    }


    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "[" + name + "]").collect(Collectors.joining("."));
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return new SqlServerCommandExecutor();
    }
}
