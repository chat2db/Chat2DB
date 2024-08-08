package ai.chat2db.plugin.oracle;

import ai.chat2db.plugin.oracle.builder.OracleSqlBuilder;
import ai.chat2db.plugin.oracle.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.type.OracleDefaultValueEnum;
import ai.chat2db.plugin.oracle.type.OracleIndexTypeEnum;
import ai.chat2db.plugin.oracle.value.OracleValueProcessor;
import ai.chat2db.server.tools.common.util.EasyStringUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import ai.chat2db.spi.util.SqlUtils;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class OracleMetaData extends DefaultMetaService implements MetaData {

    private static final String TABLE_DDL_SQL = "select dbms_metadata.get_ddl('TABLE','%s','%s') as sql from dual";
    private static final String TABLE_COMMENT_SQL = "select owner, table_name, comments from ALL_TAB_COMMENTS where OWNER = '%s'  and TABLE_NAME = '%s'";
    private static final String TABLE_COLUMN_COMMENT_SQL = """
                                                           SELECT owner, table_name, column_name, comments
                                                           FROM all_col_comments
                                                           WHERE  owner = '%s' and table_name = '%s' and comments is not null""";

    private List<String> systemSchemas = Arrays.asList("ANONYMOUS", "APEX_030200", "APEX_PUBLIC_USER", "APPQOSSYS", "BI", "CTXSYS", "DBSNMP", "DIP", "EXFSYS", "FLOWS_FILES", "HR", "IX", "MDDATA", "MDSYS", "MGMT_VIEW", "OE", "OLAPSYS", "ORACLE_OCM", "ORDDATA", "ORDPLUGINS", "ORDSYS", "OUTLN", "OWBSYS", "OWBSYS_AUDIT", "PM", "SCOTT", "SH", "SI_INFORMTN_SCHEMA", "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "SYS", "SYSMAN", "SYSTEM", "WMSYS", "XDB", "XS$NULL");

    private static final String PROCEDURE_LIST_DDL = """
                                                     SELECT OBJECT_NAME, OBJECT_TYPE
                                                     FROM ALL_OBJECTS
                                                     WHERE OBJECT_TYPE IN ('PROCEDURE')
                                                       AND OWNER = '%s'""";
    private static final String TABLE_INDEX_DDL_SQL = """
                                                      SELECT DBMS_METADATA.GET_DDL('INDEX', index_name, table_owner) AS ddl,
                                                      index_name AS INDEX_NAME
                                                      FROM all_indexes
                                                      WHERE table_owner = '%s' AND table_name = '%s'""";
    private static final String PU_INDEX_NAME_SQL = """
                                                    SELECT DISTINCT AC.INDEX_NAME
                                                    FROM ALL_CONSTRAINTS AC
                                                    WHERE  AC.OWNER = '%s' AND AC.TABLE_NAME = '%s'
                                                      AND AC.CONSTRAINT_TYPE IN ('P', 'U')""";

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        String sql = String.format(PROCEDURE_LIST_DDL, schemaName);
        ArrayList<Procedure> procedures = new ArrayList<>();
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setProcedureName(resultSet.getString("object_name"));
                procedures.add(procedure);
            }
        });
        return procedures;
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        // TODO: only_read user can not get ddl
        String sql = String.format(TABLE_DDL_SQL, tableName, schemaName);
        String tableCommentSql = String.format(TABLE_COMMENT_SQL, schemaName, tableName);
        String tableColumnCommentSql = String.format(TABLE_COLUMN_COMMENT_SQL, schemaName, tableName);
        String tableIndexSql = String.format(TABLE_INDEX_DDL_SQL, schemaName, tableName);
        String PUIndexSql = String.format(PU_INDEX_NAME_SQL, schemaName, tableName);
        StringBuilder ddlBuilder = new StringBuilder();
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            try {
                if (resultSet.next()) {
                    ddlBuilder.append(resultSet.getString("sql")).append(";");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        SQLExecutor.getInstance().execute(connection, tableCommentSql, resultSet -> {
            if (resultSet.next()) {
                String tableComment = resultSet.getString("comments");
                if (StringUtils.isNotBlank(tableComment)) {
                    ddlBuilder.append("\nCOMMENT ON TABLE ").append(SqlUtils.quoteObjectName(tableName)).append(" IS ")
                            .append(EasyStringUtils.escapeAndQuoteString(tableComment)).append(";");
                }
            }
        });
        SQLExecutor.getInstance().execute(connection, tableColumnCommentSql, resultSet -> {
            while (resultSet.next()) {
                String columnName = resultSet.getString("column_name");
                String columnComment = resultSet.getString("comments");
                if (StringUtils.isNotBlank(columnComment)) {
                    ddlBuilder.append("\nCOMMENT ON COLUMN ")
                            .append(SqlUtils.quoteObjectName(tableName)).append(".")
                            .append(SqlUtils.quoteObjectName(columnName)).append(" IS ")
                            .append(EasyStringUtils.escapeAndQuoteString(columnComment)).append(";");
                }
            }
        });
        List<String> indexNames = SQLExecutor.getInstance().execute(connection, PUIndexSql, resultSet -> {
            List<String> PUIndexNames = new ArrayList<>();
            while (resultSet.next()) {
                String indexName = resultSet.getString("index_name");
                if (StringUtils.isNotBlank(indexName)) {
                    PUIndexNames.add(indexName);
                }
            }
            return PUIndexNames;
        });
        SQLExecutor.getInstance().execute(connection, tableIndexSql, resultSet -> {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (CollectionUtils.isNotEmpty(indexNames) && indexNames.contains(indexName)) {
                    continue;
                }
                String ddl = resultSet.getString("ddl");
                if (StringUtils.isNotBlank(ddl)) {
                    ddlBuilder.append("\n").append(ddl).append(";");
                }
            }
        });
        return ddlBuilder.toString();

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
        List<TableColumn> tableColumns = super.columns(connection, databaseName, schemaName, tableName);
        if (CollectionUtils.isNotEmpty(tableColumns)) {
            Map<String, TableColumn> tableColumnMap = getTableColumns(connection, databaseName, schemaName, tableName);
            for (TableColumn tableColumn : tableColumns) {
                tableColumn.setColumnType(SqlUtils.removeDigits(tableColumn.getColumnType()));
                TableColumn column = tableColumnMap.get(tableColumn.getName());
                if (column != null) {
                    tableColumn.setUnit(column.getUnit());
                    tableColumn.setComment(column.getComment());
                    tableColumn.setDefaultValue(column.getDefaultValue());
                    tableColumn.setOrdinalPosition(column.getOrdinalPosition());
                    tableColumn.setNullable(column.getNullable());
                }
            }
        }
        return tableColumns;
    }

    private Map<String, TableColumn> getTableColumns(Connection connection, String databaseName, String schemaName, String tableName) {
        Map<String, TableColumn> tableColumns = new HashMap<>();
        String sql = String.format(SELECT_TAB_COLS, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn tableColumn = new TableColumn();
                tableColumn.setTableName(tableName);
                tableColumn.setSchemaName(schemaName);
                try {
                    //
                    // Fields of the LONG type cannot be retrieved using getObject. They need to be accessed using getCharacterStream, and must be read first in the sequence.
                    Reader reader = resultSet.getCharacterStream("DATA_DEFAULT");
                    if (reader != null) {
                        StringBuilder sb = new StringBuilder();
                        int charValue;
                        while ((charValue = reader.read()) != -1) {
                            sb.append((char) charValue);
                        }
                        tableColumn.setDefaultValue(sb.toString());
                    }
                } catch (Exception e) {
                    log.error("getDefaultValue error", e);
                }
                tableColumn.setName(resultSet.getString("COLUMN_NAME"));
                String dataType = resultSet.getString("DATA_TYPE");
                if (dataType.contains("(")) {
                    dataType = dataType.substring(0, dataType.indexOf("(")).trim();
                }
                tableColumn.setColumnType(dataType);
                Integer dataPrecision = resultSet.getInt("DATA_PRECISION");
                if (resultSet.getString("DATA_PRECISION") != null) {
                    tableColumn.setColumnSize(dataPrecision);
                } else {
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
                tableColumns.put(tableColumn.getName(), tableColumn);
            }
            return tableColumns;
        });

    }

    private static String ROUTINES_SQL
            = "SELECT LINE, TEXT "
            + "FROM ALL_SOURCE "
            + "WHERE TYPE = '%s' AND OWNER = '%s' AND NAME = '%s'"
            + "ORDER BY LINE";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {
        String sql = String.format(ROUTINES_SQL, "FUNCTION", schemaName, functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            StringBuilder bodyBuilder = new StringBuilder("CREATE OR REPLACE ");
            while (resultSet.next()) {
                bodyBuilder.append(resultSet.getString("TEXT")).append("\n");
            }
            String functionBody = bodyBuilder.toString().trim();
            if (!functionBody.endsWith("/")) {
                functionBody += "\n/";
            }
            function.setFunctionBody(functionBody);
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
                                                         String triggerName = resultSet.getString("TRIGGER_NAME");
                                                         Trigger trigger = new Trigger();
                                                         trigger.setTriggerName(triggerName == null ? "" : triggerName.trim());
                                                         trigger.setSchemaName(schemaName);
                                                         trigger.setDatabaseName(databaseName);
                                                         triggers.add(trigger);
                                                     }
                                                     return triggers;
                                                 });
    }

    private static final String TRIGGER_DDL_SQL = "SELECT DBMS_METADATA.GET_DDL('TRIGGER', '%s', '%s') as ddl FROM DUAL";

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {
        String sql = String.format(TRIGGER_DDL_SQL, triggerName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            while (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("ddl"));
            }
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", schemaName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            StringBuilder bodyBuilder = new StringBuilder("CREATE OR REPLACE ");
            while (resultSet.next()) {
                bodyBuilder.append(resultSet.getString("TEXT")).append("\n");
            }
            String procedureBody = bodyBuilder.toString().trim(); // 去掉最后的空白字符
            if (!procedureBody.endsWith("/")) {
                procedureBody += "\n/";
            }
            procedure.setProcedureBody(procedureBody);
            return procedure;
        });
    }


    private static String VIEW_DDL_SQL = "SELECT VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = '%s' AND VIEW_NAME = '%s'";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_DDL_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl("CREATE OR REPLACE VIEW " + viewName + " AS " + resultSet.getString("TEXT"));
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


    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }

    /**
     * @return
     */
    @Override
    public ValueProcessor getValueProcessor() {
        return new OracleValueProcessor();
    }
}
