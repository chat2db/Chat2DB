package ai.chat2db.plugin.db2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.db2.builder.DB2SqlBuilder;
import ai.chat2db.plugin.db2.constant.SQLConstant;
import ai.chat2db.plugin.db2.type.DB2ColumnTypeEnum;
import ai.chat2db.plugin.db2.type.DB2DefaultValueEnum;
import ai.chat2db.plugin.db2.type.DB2IndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

public class DB2MetaData extends DefaultMetaService implements MetaData {

    private List<String> systemSchemas = Arrays.asList("NULLID","SQLJ","SYSCAT","SYSFUN","SYSIBM","SYSIBMADM","SYSIBMINTERNAL","SYSIBMTS","SYSPROC","SYSPUBLIC","SYSSTAT","SYSTOOLS");
    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }


    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            SQLExecutor.getInstance().execute(connection, SQLConstant.TABLE_DDL_FUNCTION_SQL, resultSet -> null);
        } catch (Exception e) {
            //log.error("Failed to create function", e);
        }
        String ddlSql = String.format("select %s.GENERATE_TABLE_DDL('%s', '%s') as sql from %s;",schemaName,schemaName,tableName,tableName);
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
    public SqlBuilder getSqlBuilder() {
        return new DB2SqlBuilder();
    }

    private static String IDX_SQL = "SELECT i.INDNAME, i.UNIQUERULE, i.REMARKS, ic.COLNAME, ic.COLSEQ, ic.COLORDER FROM SYSCAT.INDEXES i JOIN SYSCAT.INDEXCOLUSE ic ON i.INDNAME = ic.INDNAME AND i.INDSCHEMA = ic.INDSCHEMA WHERE i.TABNAME = '%s' AND i.INDSCHEMA = '%s' ORDER BY i.INDNAME, ic.COLSEQ";

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(IDX_SQL, tableName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("INDNAME");
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
                    index.setComment(resultSet.getString("REMARKS"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String uniquerule = resultSet.getString("UNIQUERULE");
                    if("P".equalsIgnoreCase(uniquerule)) {
                        index.setType(DB2IndexTypeEnum.PRIMARY_KEY.getName());
                        index.setUnique(true);
                    }else if("U".equalsIgnoreCase(uniquerule)){
                        index.setType(DB2IndexTypeEnum.UNIQUE.getName());
                        index.setUnique(true);
                    }else {
                        index.setType(DB2IndexTypeEnum.NORMAL.getName());
                        index.setUnique(false);
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private static String VIEW_DDL_SQL="select TEXT from syscat.views where VIEWSCHEMA='%s' and VIEWNAME='%s';";
    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_DDL_SQL, schemaName, viewName);
        Table table = new Table();
        table.setDatabaseName(databaseName);
        table.setSchemaName(schemaName);
        table.setName(viewName);
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("TEXT")+";");
            }
        });
        return table;
    }

    private static String ROUTINE_DDL_SQL="select TEXT from syscat.routines where ROUTINESCHEMA='%s' and ROUTINENAME='%s' and ROUTINETYPE='%s';";

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        Function function = new Function();
       function.setDatabaseName(databaseName);
       function.setSchemaName(schemaName);
       function.setFunctionName(functionName);
        String sql = String.format(ROUTINE_DDL_SQL, schemaName, functionName,'F');
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("TEXT")+";");
            }
        });
        return function;
    }

    @Override
    public Procedure procedure(Connection connection, String databaseName, String schemaName, String procedureName) {
        Procedure procedure = new Procedure();
        procedure.setDatabaseName(databaseName);
        procedure.setSchemaName(schemaName);
        procedure.setProcedureName(procedureName);
        String sql = String.format(ROUTINE_DDL_SQL, schemaName, procedureName,'P');
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                procedure.setProcedureBody(resultSet.getString("TEXT")+";");
            }
        });
        return procedure;
    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLNAME"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("COLSEQ"));
//        tableIndexColumn.setCollation(resultSet.getString("Collation"));
//        tableIndexColumn.setCardinality(resultSet.getLong("Cardinality"));
//        tableIndexColumn.setSubPart(resultSet.getLong("Sub_part"));
        String collation = resultSet.getString("COLORDER");
        if ("A".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else if ("D".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(DB2ColumnTypeEnum.getTypes())
                .charsets(Lists.newArrayList())
                .collations(Lists.newArrayList())
                .indexTypes(DB2IndexTypeEnum.getIndexTypes())
                .defaultValues(DB2DefaultValueEnum.getDefaultValues())
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
}
