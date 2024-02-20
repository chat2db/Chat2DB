package ai.chat2db.plugin.postgresql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.postgresql.builder.PostgreSQLSqlBuilder;
import ai.chat2db.plugin.postgresql.type.*;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import static ai.chat2db.plugin.postgresql.consts.SQLConst.FUNCTION_SQL;
import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class PostgreSQLMetaData extends DefaultMetaService implements MetaData {

    private static final String SELECT_KEY_INDEX = "SELECT ccu.table_schema AS Foreign_schema_name, ccu.table_name AS Foreign_table_name, ccu.column_name AS Foreign_column_name, constraint_type AS Constraint_type, tc.CONSTRAINT_NAME AS Key_name, tc.TABLE_NAME, kcu.Column_name, tc.is_deferrable, tc.initially_deferred FROM information_schema.table_constraints AS tc JOIN information_schema.key_column_usage AS kcu ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name WHERE tc.TABLE_SCHEMA = '%s'  AND tc.TABLE_NAME = '%s';";


    private List<String> systemDatabases = Arrays.asList("postgres");
    @Override
    public List<Database> databases(Connection connection) {
        List<Database> list = SQLExecutor.getInstance().execute(connection, "SELECT datname FROM pg_database;", resultSet -> {
            List<Database> databases = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    String dbName = resultSet.getString("datname");
                    if ("template0".equals(dbName) || "template1".equals(dbName)) {
                        continue;
                    }
                    Database database = new Database();
                    database.setName(dbName);
                    databases.add(database);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return databases;
        });
        return sortDatabase(list, systemDatabases,connection);
    }

    private List<String> systemSchemas = Arrays.asList("pg_toast","pg_temp_1","pg_toast_temp_1","pg_catalog","information_schema");

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().execute(connection,
                "SELECT catalog_name, schema_name FROM information_schema.schemata;", resultSet -> {
                    List<Schema> databases = new ArrayList<>();
                    while (resultSet.next()) {
                        Schema schema = new Schema();
                        String name = resultSet.getString("schema_name");
                        String catalogName = resultSet.getString("catalog_name");
                        schema.setName(name);
                        schema.setDatabaseName(catalogName);
                        databases.add(schema);
                    }
                    return databases;
                });
        return SortUtils.sortSchema(schemas, systemSchemas);
    }


    private static final String SELECT_TABLE_INDEX = "SELECT tmp.INDISPRIMARY AS Index_primary, tmp.TABLE_SCHEM, tmp.TABLE_NAME, tmp.NON_UNIQUE, tmp.INDEX_QUALIFIER, tmp.INDEX_NAME AS Key_name, tmp.indisclustered, tmp.ORDINAL_POSITION AS Seq_in_index, TRIM ( BOTH '\"' FROM pg_get_indexdef ( tmp.CI_OID, tmp.ORDINAL_POSITION, FALSE ) ) AS Column_name,CASE  tmp.AM_NAME   WHEN 'btree' THEN CASE   tmp.I_INDOPTION [ tmp.ORDINAL_POSITION - 1 ] & 1 :: SMALLINT   WHEN 1 THEN  'D' ELSE'A'  END ELSE NULL  END AS Collation, tmp.CARDINALITY, tmp.PAGES, tmp.FILTER_CONDITION , tmp.AM_NAME AS Index_method, tmp.DESCRIPTION AS Index_comment FROM ( SELECT  n.nspname AS TABLE_SCHEM,  ct.relname AS TABLE_NAME,  NOT i.indisunique AS NON_UNIQUE, NULL AS INDEX_QUALIFIER,  ci.relname AS INDEX_NAME,i.INDISPRIMARY , i.indisclustered ,  ( information_schema._pg_expandarray ( i.indkey ) ).n AS ORDINAL_POSITION,  ci.reltuples AS CARDINALITY,   ci.relpages AS PAGES,  pg_get_expr ( i.indpred, i.indrelid ) AS FILTER_CONDITION,  ci.OID AS CI_OID, i.indoption AS I_INDOPTION,  am.amname AS AM_NAME , d.description  FROM   pg_class ct   JOIN pg_namespace n ON ( ct.relnamespace = n.OID )   JOIN pg_index i ON ( ct.OID = i.indrelid )   JOIN pg_class ci ON ( ci.OID = i.indexrelid )  JOIN pg_am am ON ( ci.relam = am.OID )      left outer join pg_description d on i.indexrelid = d.objoid  WHERE  n.nspname = '%s'   AND ct.relname = '%s'   ) AS tmp ;";
    private static String ROUTINES_SQL = "SELECT p.proname, p.prokind, pg_catalog.pg_get_functiondef(p.oid) as \"code\" FROM pg_catalog.pg_proc p where p.prokind = '%s' and p.proname='%s'";
    private static String TRIGGER_SQL
            = "SELECT n.nspname AS \"schema\", c.relname AS \"table_name\", t.tgname AS \"trigger_name\", t.tgenabled AS "
            + "\"enabled\", pg_get_triggerdef(t.oid) AS \"trigger_body\" FROM pg_trigger t JOIN pg_class c ON c.oid = t"
            + ".tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = '%s' AND t.tgname ='%s';";
    private static String TRIGGER_SQL_LIST
            = "SELECT n.nspname AS \"schema\", c.relname AS \"table_name\", t.tgname AS \"trigger_name\", t.tgenabled AS "
            + "\"enabled\", pg_get_triggerdef(t.oid) AS \"trigger_body\" FROM pg_trigger t JOIN pg_class c ON c.oid = t"
            + ".tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = '%s';";
    private static String VIEW_SQL
            = "SELECT schemaname, viewname, definition FROM pg_views WHERE schemaname = '%s' AND viewname = '%s';";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("trigger_name"));
                trigger.setSchemaName(schemaName);
                trigger.setDatabaseName(databaseName);
                triggers.add(trigger);
            }
            return triggers;
        });
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        SQLExecutor.getInstance().execute(connection, FUNCTION_SQL.replaceFirst("tableSchema", schemaName),
                resultSet -> null);
        String ddlSql = "select showcreatetable('" + schemaName + "','" + tableName + "') as sql";
        return SQLExecutor.getInstance().execute(connection, ddlSql, resultSet -> {
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
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "f", functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("code"));
            }
            return function;
        });

    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("definition"));
            }
            return table;
        });
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, schemaName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("trigger_body"));
            }

            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "p", procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            if (resultSet.next()) {
                procedure.setProcedureBody(resultSet.getString("code"));
            }
            return procedure;
        });
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {

        String constraintSql = String.format(SELECT_KEY_INDEX, schemaName, tableName);
        Map<String, String> constraintMap = new HashMap();
        LinkedHashMap<String, TableIndex> foreignMap = new LinkedHashMap();
        SQLExecutor.getInstance().execute(connection, constraintSql, resultSet -> {
            while (resultSet.next()) {
                String keyName = resultSet.getString("Key_name");
                String constraintType = resultSet.getString("Constraint_type");
                constraintMap.put(keyName, constraintType);
                if (StringUtils.equalsIgnoreCase(constraintType, PostgreSQLIndexTypeEnum.FOREIGN.getKeyword())) {
                    TableIndex tableIndex = foreignMap.get(keyName);
                    String columnName = resultSet.getString("Column_name");
                    if (tableIndex == null) {
                        tableIndex = new TableIndex();
                        tableIndex.setDatabaseName(databaseName);
                        tableIndex.setSchemaName(schemaName);
                        tableIndex.setTableName(tableName);
                        tableIndex.setName(keyName);
                        tableIndex.setForeignSchemaName(resultSet.getString("Foreign_schema_name"));
                        tableIndex.setForeignTableName(resultSet.getString("Foreign_table_name"));
                        tableIndex.setForeignColumnNamelist(Lists.newArrayList(columnName));
                        tableIndex.setType(PostgreSQLIndexTypeEnum.FOREIGN.getName());
                        foreignMap.put(keyName, tableIndex);
                    } else {
                        tableIndex.getForeignColumnNamelist().add(columnName);
                    }
                }
            }
            return null;
        });

        String sql = String.format(SELECT_TABLE_INDEX, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap(foreignMap);

            while (resultSet.next()) {
                String keyName = resultSet.getString("Key_name");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    if(columnList == null){
                        columnList = new ArrayList<>();
                        tableIndex.setColumnList(columnList);
                    }
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
                    index.setUnique(!StringUtils.equals("t", resultSet.getString("NON_UNIQUE")));
                    index.setMethod(resultSet.getString("Index_method"));
                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String constraintType = constraintMap.get(keyName);
                    if (StringUtils.equals("t", resultSet.getString("Index_primary"))) {
                        index.setType(PostgreSQLIndexTypeEnum.PRIMARY.getName());
                    } else if (StringUtils.equalsIgnoreCase(constraintType, PostgreSQLIndexTypeEnum.UNIQUE.getName())) {
                        index.setType(PostgreSQLIndexTypeEnum.UNIQUE.getName());
                    } else {
                        index.setType(PostgreSQLIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableColumn> columnList = super.columns(connection, databaseName, schemaName, tableName);

        EasyCollectionUtils.stream(columnList).forEach(v -> {
            if (StringUtils.equalsIgnoreCase(v.getColumnType(), "bpchar")) {
                v.setColumnType(PostgreSQLColumnTypeEnum.CHAR.getColumnType().getTypeName().toUpperCase());
            } else {
                v.setColumnType(v.getColumnType().toUpperCase());
            }
        });
        return columnList;
    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("Column_name"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("Seq_in_index"));
        tableIndexColumn.setCollation(resultSet.getString("Collation"));
        tableIndexColumn.setAscOrDesc(resultSet.getString("Collation"));
        return tableIndexColumn;
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new PostgreSQLSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(PostgreSQLColumnTypeEnum.getTypes())
                .charsets(PostgreSQLCharsetEnum.getCharsets())
                .collations(PostgreSQLCollationEnum.getCollations())
                .indexTypes(PostgreSQLIndexTypeEnum.getIndexTypes())
                .defaultValues(PostgreSQLDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }
}
