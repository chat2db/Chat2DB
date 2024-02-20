package ai.chat2db.plugin.kingbase;

import ai.chat2db.plugin.kingbase.builder.KingBaseSqlBuilder;
import ai.chat2db.plugin.kingbase.type.KingBaseColumnTypeEnum;
import ai.chat2db.plugin.kingbase.type.KingBaseDefaultValueEnum;
import ai.chat2db.plugin.kingbase.type.KingBaseIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class KingBaseMetaData extends DefaultMetaService implements MetaData {


    private static final String SELECT_KEY_INDEX = "SELECT ccu.table_schema AS Foreign_schema_name, ccu.table_name AS Foreign_table_name, ccu.column_name AS Foreign_column_name, constraint_type AS Constraint_type, tc.CONSTRAINT_NAME AS Key_name, tc.TABLE_NAME, kcu.Column_name, tc.is_deferrable, tc.initially_deferred FROM information_schema.table_constraints AS tc JOIN information_schema.key_column_usage AS kcu ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name WHERE tc.TABLE_SCHEMA = '%s'  AND tc.TABLE_NAME = '%s';";


    private List<String> systemDatabases = Arrays.asList("SAMPLES", "SECURITY");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> list = SQLExecutor.getInstance().execute(connection, "SELECT datname FROM sys_database", resultSet -> {
            List<Database> databases = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    String dbName = resultSet.getString("datname");
                    if ("template0".equalsIgnoreCase(dbName) || "template1".equalsIgnoreCase(dbName) ||
                            "template2".equalsIgnoreCase(dbName)) {
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
        return sortDatabase(list, systemDatabases, connection);
    }

    private static final String SELECT_TABLE_INDEX = "SELECT tmp.INDISPRIMARY AS Index_primary, tmp.TABLE_SCHEM, tmp.TABLE_NAME, tmp.NON_UNIQUE, tmp.INDEX_QUALIFIER, tmp.INDEX_NAME AS Key_name, tmp.indisclustered, tmp.ORDINAL_POSITION AS Seq_in_index, trim(BOTH '\"' FROM sys_get_indexdef( tmp.CI_OID, tmp.ORDINAL_POSITION, FALSE) ) AS Column_name, CASE tmp.AM_NAME WHEN 'btree' THEN CASE tmp.I_INDOPTION [ tmp.ORDINAL_POSITION - 1 ] & 1 :: SMALLINT WHEN 1 THEN 'D' ELSE'A' END ELSE NULL END AS Collation, tmp.CARDINALITY, tmp.PAGES, tmp.FILTER_CONDITION , tmp.AM_NAME AS Index_method, tmp.DESCRIPTION AS Index_comment FROM ( SELECT n.nspname AS TABLE_SCHEM, ct.relname AS TABLE_NAME, NOT i.indisunique AS NON_UNIQUE, NULL AS INDEX_QUALIFIER, ci.relname AS INDEX_NAME, i.INDISPRIMARY , i.indisclustered , ( information_schema._sys_expandarray ( i.indkey ) ).n AS ORDINAL_POSITION, ci.reltuples AS CARDINALITY, ci.relpages AS PAGES, sys_get_expr ( i.indpred, i.indrelid ) AS FILTER_CONDITION, ci.OID AS CI_OID, i.indoption AS I_INDOPTION, am.amname AS AM_NAME , d.description FROM sys_class ct JOIN sys_namespace n ON ( ct.relnamespace = n.OID ) JOIN sys_index i ON ( ct.OID = i.indrelid ) JOIN sys_class ci ON ( ci.OID = i.indexrelid ) JOIN sys_am am ON ( ci.relam = am.OID ) left outer join sys_description d on i.indexrelid = d.objoid WHERE n.nspname = '%s' AND ct.relname = '%s' ) AS tmp";

    private static final String SELECT_TABLE_INDEX_8R6 = "SELECT tmp.INDISPRIMARY AS Index_primary, tmp.TABLE_SCHEM, tmp.TABLE_NAME, tmp.NON_UNIQUE, tmp.INDEX_QUALIFIER, tmp.INDEX_NAME AS Key_name, tmp.indisclustered, tmp.ORDINAL_POSITION AS Seq_in_index, trim(BOTH '\"' FROM sys_get_indexdef( tmp.CI_OID, tmp.ORDINAL_POSITION, FALSE) ) AS Column_name, CASE tmp.AM_NAME WHEN 'btree' THEN CASE tmp.I_INDOPTION [ tmp.ORDINAL_POSITION - 1 ] & 1 :: SMALLINT WHEN 1 THEN 'D' ELSE'A' END ELSE NULL END AS Collation, tmp.CARDINALITY, tmp.PAGES, tmp.FILTER_CONDITION , tmp.AM_NAME AS Index_method, tmp.DESCRIPTION AS Index_comment FROM ( SELECT n.nspname AS TABLE_SCHEM, ct.relname AS TABLE_NAME, NOT i.indisunique AS NON_UNIQUE, NULL AS INDEX_QUALIFIER, ci.relname AS INDEX_NAME, i.INDISPRIMARY , i.indisclustered , ( information_schema._pg_expandarray ( i.indkey ) ).n AS ORDINAL_POSITION, ci.reltuples AS CARDINALITY, ci.relpages AS PAGES, sys_get_expr ( i.indpred, i.indrelid ) AS FILTER_CONDITION, ci.OID AS CI_OID, i.indoption AS I_INDOPTION, am.amname AS AM_NAME , d.description FROM sys_class ct JOIN sys_namespace n ON ( ct.relnamespace = n.OID ) JOIN sys_index i ON ( ct.OID = i.indrelid ) JOIN sys_class ci ON ( ci.OID = i.indexrelid ) JOIN sys_am am ON ( ci.relam = am.OID ) left outer join sys_description d on i.indexrelid = d.objoid WHERE n.nspname = '%s' AND ct.relname = '%s' ) AS tmp";

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
                if (StringUtils.equalsIgnoreCase(constraintType, KingBaseIndexTypeEnum.FOREIGN.getKeyword())) {
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
                        tableIndex.setType(KingBaseIndexTypeEnum.FOREIGN.getName());
                        foreignMap.put(keyName, tableIndex);
                    } else {
                        tableIndex.getForeignColumnNamelist().add(columnName);
                    }
                }
            }
            return null;
        });
        String version = getDbVersion();
        String sql = String.format(SELECT_TABLE_INDEX, schemaName, tableName);
        if(version.startsWith("12.")|| version.startsWith("9.")) {
            sql = String.format(SELECT_TABLE_INDEX_8R6, schemaName, tableName);
        }
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap(foreignMap);

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
                    index.setUnique(!StringUtils.equals("t", resultSet.getString("NON_UNIQUE")));
                    index.setMethod(resultSet.getString("Index_method"));
                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String constraintType = constraintMap.get(keyName);
                    if (StringUtils.equals("t", resultSet.getString("Index_primary"))) {
                        index.setType(KingBaseIndexTypeEnum.PRIMARY.getName());
                    } else if (StringUtils.equalsIgnoreCase(constraintType, KingBaseIndexTypeEnum.UNIQUE.getName())) {
                        index.setType(KingBaseIndexTypeEnum.UNIQUE.getName());
                    } else {
                        index.setType(KingBaseIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });
    }

    private String getDbVersion(){
        String version = Chat2DBContext.getDbVersion();
        if(StringUtils.isNotBlank(version)){
            return version;
        }
        return "";
    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("Column_name"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("Seq_in_index"));
        tableIndexColumn.setCollation(resultSet.getString("Collation"));
        tableIndexColumn.setAscOrDesc(resultSet.getString("Collation"));
        return tableIndexColumn;
    }

    private static String ROUTINES_SQL = " SELECT p.proname, p.prokind, sys_catalog.sys_get_functiondef(p.oid) as \"code\" FROM sys_catalog.sys_proc p where p.proname='%s'";

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
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, procedureName);
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
    public SqlBuilder getSqlBuilder() {
        return new KingBaseSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(KingBaseColumnTypeEnum.getTypes())
                //.charsets(PostgreSQLCharsetEnum.getCharsets())
                //.collations(PostgreSQLCollationEnum.getCollations())
                .indexTypes(KingBaseIndexTypeEnum.getIndexTypes())
                .defaultValues(KingBaseDefaultValueEnum.getDefaultValues())
                .build();
    }
    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }
}
