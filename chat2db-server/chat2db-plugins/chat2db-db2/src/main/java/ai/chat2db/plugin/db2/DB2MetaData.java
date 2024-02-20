package ai.chat2db.plugin.db2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.db2.builder.DB2SqlBuilder;
import ai.chat2db.plugin.db2.type.DB2ColumnTypeEnum;
import ai.chat2db.plugin.db2.type.DB2DefaultValueEnum;
import ai.chat2db.plugin.db2.type.DB2IndexTypeEnum;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import ai.chat2db.spi.model.TableMeta;
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
            SQLExecutor.getInstance().execute(connection, functionSQL.replace("tableSchema", schemaName), resultSet -> null);
        } catch (Exception e) {
            //log.error("创建函数失败", e);
        }

        String ddlSql = "SELECT " + schemaName + ".ufn_GetCreateTableScript('" + schemaName + "', '" + tableName
                + "') AS sql";
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

}
