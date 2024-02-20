package ai.chat2db.plugin.oracle;

import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.oracle.builder.OracleSqlBuilder;
import ai.chat2db.plugin.oracle.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.type.OracleDefaultValueEnum;
import ai.chat2db.plugin.oracle.type.OracleIndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

public class OracleMetaData extends DefaultMetaService implements MetaData {

    private static final String TABLE_DDL_SQL = "select dbms_metadata.get_ddl('TABLE','%s','%s') as sql from dual";

    private List<String> systemSchemas = Arrays.asList("ANONYMOUS","APEX_030200","APEX_PUBLIC_USER","APPQOSSYS","BI","CTXSYS","DBSNMP","DIP","EXFSYS","FLOWS_FILES","HR","IX","MDDATA","MDSYS","MGMT_VIEW","OE","OLAPSYS","ORACLE_OCM","ORDDATA","ORDPLUGINS","ORDSYS","OUTLN","OWBSYS","OWBSYS_AUDIT","PM","SCOTT","SH","SI_INFORMTN_SCHEMA","SPATIAL_CSW_ADMIN_USR","SPATIAL_WFS_ADMIN_USR","SYS","SYSMAN","SYSTEM","WMSYS","XDB","XS$NULL");


    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(TABLE_DDL_SQL, tableName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
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

    private static String SELECT_TABLE_SQL = "SELECT A.OWNER, A.TABLE_NAME, B.COMMENTS " +
            "FROM ALL_TABLES A LEFT JOIN ALL_TAB_COMMENTS B ON  A.OWNER = B.OWNER  AND A.TABLE_NAME = B.TABLE_NAME\n" +
            "where A.OWNER = '%s' ";

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_SQL, schemaName);
        if (StringUtils.isNotBlank(tableName)) {
            sql = sql + " and A.TABLE_NAME = '" + tableName + "'";
        }
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<Table> tables = new ArrayList<>();
            while (resultSet.next()) {
                Table table = new Table();
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(resultSet.getString("TABLE_NAME"));
                table.setComment(resultSet.getString("COMMENTS"));
                tables.add(table);
            }
            return tables;
        });
    }

    private static String SELECT_TAB_COLS = "SELECT atc.column_id , atc.column_name as COLUMN_NAME, atc.data_type as DATA_TYPE , atc.data_length as DATA_LENGTH , atc.data_type_mod , atc.nullable ,  atc.data_default as DATA_DEFAULT,  acc.comments ,  atc.DATA_PRECISION ,  atc.DATA_SCALE , atc.CHAR_USED  FROM  all_tab_columns atc, all_col_comments acc WHERE atc.owner = acc.owner AND atc.table_name = acc.table_name AND atc.column_name = acc.column_name AND atc.owner = '%s'  AND atc.table_name = '%s'  order by atc.column_id";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TAB_COLS, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<TableColumn> tableColumns = new ArrayList<>();
            while (resultSet.next()) {
                TableColumn tableColumn = new TableColumn();
                tableColumn.setTableName(tableName);
                tableColumn.setSchemaName(schemaName);
                try {
                    //
                    // Fields of the LONG type cannot be retrieved using getObject. They need to be accessed using getCharacterStream, and must be read first in the sequence.
                    Reader reader = resultSet.getCharacterStream("DATA_DEFAULT");
                    if(reader != null){
                        StringBuilder sb = new StringBuilder();
                        int charValue;
                        while ((charValue = reader.read()) != -1) {
                            sb.append((char) charValue);
                        }
                        tableColumn.setDefaultValue(sb.toString());
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                tableColumn.setName(resultSet.getString("COLUMN_NAME"));
                tableColumn.setColumnType(resultSet.getString("DATA_TYPE"));
                Integer dataPrecision = resultSet.getInt("DATA_PRECISION");
                if(dataPrecision!=null) {
                    tableColumn.setColumnSize(dataPrecision);
                }else {
                    tableColumn.setColumnSize(resultSet.getInt("DATA_LENGTH"));
                }
//                Object dataDefault = resultSet.getObject(7);
//                if(dataDefault!=null) {
//                    tableColumn.setDefaultValue(dataDefault.toString());
//                }




                tableColumn.setComment(resultSet.getString("COMMENTS"));
                tableColumn.setNullable("Y".equalsIgnoreCase(resultSet.getString("NULLABLE")) ? 1 : 0);
                tableColumn.setOrdinalPosition(resultSet.getInt("COLUMN_ID"));
                tableColumn.setDecimalDigits(resultSet.getInt("DATA_SCALE"));
                String charUsed = resultSet.getString("CHAR_USED");
                if ("B".equalsIgnoreCase(charUsed)) {
                    tableColumn.setUnit("BYTE");
                } else if ("C".equalsIgnoreCase(charUsed)) {
                    tableColumn.setUnit("CHAR");
                }
                tableColumns.add(tableColumn);
            }
            return tableColumns;
        });
    }

    private static String ROUTINES_SQL
            = "SELECT LINE, TEXT "
            + "FROM ALL_SOURCE "
            + "WHERE TYPE = '%s' AND NAME = '%s' "
            + "ORDER BY LINE";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "FUNCTION", functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            function.setFunctionBody(sb.toString());
            return function;

        });

    }

    private static String TRIGGER_SQL_LIST
            = "SELECT TRIGGER_NAME "
            + "FROM ALL_TRIGGERS WHERE OWNER = '%s'";

    private static String SELECT_PK_SQL = "select  acc.CONSTRAINT_NAME from  all_cons_columns acc,  all_constraints ac  where  acc.constraint_name = ac.constraint_name  and acc.owner = ac.owner  and acc.owner = '%s'  and ac.constraint_type = 'P'  and ac.table_name = '%s' ";

    private static String SELECT_TABLE_INDEX = "SELECT ai.index_name AS Key_name, aic.column_name AS Column_name, ai.index_type AS Index_type, ai.uniqueness AS Unique_name, aic.COLUMN_POSITION as Seq_in_index, aic.descend AS Collation, ex.COLUMN_EXPRESSION as COLUMN_EXPRESSION FROM all_ind_columns aic JOIN all_indexes ai ON aic.table_owner = ai.table_owner and aic.table_name = ai.table_name and aic.index_name = ai.index_name LEFT JOIN ALL_IND_EXPRESSIONS ex ON aic.table_owner = ex.table_owner and aic.table_name = ex.table_name and aic.index_name = ex.index_name where ai.table_owner = '%s' AND ai.table_name = '%s' ";


    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String pkSql = String.format(SELECT_PK_SQL, schemaName, tableName);
        Set<String> pkSet = new HashSet<>();
        SQLExecutor.getInstance().execute(connection, pkSql, resultSet -> {
                    while (resultSet.next()) {
                        pkSet.add(resultSet.getString("CONSTRAINT_NAME"));
                    }
                    return null;
                }
        );

        String sql = String.format(SELECT_TABLE_INDEX, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
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
                    index.setUnique("unique".equalsIgnoreCase(resultSet.getString("Unique_name")));
                    index.setType(resultSet.getString("Index_type"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    if (index.getUnique()) {
                        index.setType(OracleIndexTypeEnum.UNIQUE.getName());
                    } else if ("NORMAL".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.NORMAL.getName());
                    } else if ("BITMAP".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.BITMAP.getName());
                    } else if (StringUtils.isNotBlank(index.getType()) && index.getType().toUpperCase().contains("NORMAL")) {
                        index.setType(OracleIndexTypeEnum.NORMAL.getName());
                    }
                    if (pkSet.contains(keyName)) {
                        index.setType(OracleIndexTypeEnum.PRIMARY_KEY.getName());
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
        String expression = resultSet.getString("COLUMN_EXPRESSION");
        if (!StringUtils.isBlank(expression)) {
            tableIndexColumn.setColumnName(expression.replace("\"", ""));
        }
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("Seq_in_index"));
        tableIndexColumn.setCollation(resultSet.getString("Collation"));
        tableIndexColumn.setAscOrDesc(resultSet.getString("Collation"));
        return tableIndexColumn;
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(TRIGGER_SQL_LIST, schemaName),
                resultSet -> {
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

        String sql = String.format(ROUTINES_SQL, "TRIGGER", triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            trigger.setTriggerBody(resultSet.getString(sb.toString()));
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            procedure.setProcedureBody(sb.toString());
            return procedure;
        });
    }

    private static String VIEW_SQL
            = "SELECT VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = '%s' AND VIEW_NAME = '%s'";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("TEXT"));
            }
            return table;
        });
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new OracleSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(OracleColumnTypeEnum.getTypes())
                .charsets(Lists.newArrayList())
                .collations(Lists.newArrayList())
                .indexTypes(OracleIndexTypeEnum.getIndexTypes())
                .defaultValues(OracleDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }
}
