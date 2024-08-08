package ai.chat2db.spi.jdbc;

import ai.chat2db.server.tools.common.util.EasyStringUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import ai.chat2db.spi.enums.DmlType;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.util.SqlUtils;
import com.google.common.collect.Lists;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultSqlBuilder implements SqlBuilder<Table> {


    @Override
    public String buildTableQuerySql(String databaseName, String schemaName, String tableName) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT * FROM ");
        buildTableName(databaseName, schemaName, tableName, sqlBuilder);
        return sqlBuilder.toString();
    }

    @Override
    public String buildCreateTableSql(Table table) {
        return null;
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        return null;
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        return null;
    }

    public static String CREATE_DATABASE_SQL = "CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET %s COLLATE %s";

    @Override
    public String buildCreateDatabaseSql(Database database) {
        return null;
    }

    @Override
    public String buildModifyDatabaseSql(Database oldDatabase, Database newDatabase) {
        return null;
    }

    @Override
    public String buildCreateSchemaSql(Schema schema) {
        return null;
    }

    @Override
    public String buildModifySchemaSql(String oldSchemaName, String newSchemaName) {
        return null;
    }

    @Override
    public String buildOrderBySql(String originSql, List<OrderBy> orderByList) {
        if (CollectionUtils.isEmpty(orderByList)) {
            return originSql;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(originSql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) selectStatement.getSelectBody();

                // Create a new ORDER BY clause
                List<OrderByElement> orderByElements = new ArrayList<>();

                for (OrderBy orderBy : orderByList) {
                    OrderByElement orderByElement = new OrderByElement();
                    orderByElement.setExpression(CCJSqlParserUtil.parseExpression(orderBy.getColumnName()));
                    orderByElement.setAsc(orderBy.isAsc()); // Set to ascending order, use setAsc(false) to set to descending order
                    orderByElements.add(orderByElement);
                }
                // Replace the original ORDER BY clause
                plainSelect.setOrderByElements(orderByElements);
                // Output the modified SQL
                return plainSelect.toString();
            }
        } catch (Exception e) {
        }
        return originSql;
    }

    @Override
    public String buildSqlByQuery(QueryResult queryResult) {
        List<Header> headerList = queryResult.getHeaderList();
        List<ResultOperation> operations = queryResult.getOperations();
        String tableName = queryResult.getTableName();
        StringBuilder stringBuilder = new StringBuilder();
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String dbType = Chat2DBContext.getDBConfig().getDbType();
        List<String> keyColumns = getPrimaryColumns(headerList);
        for (int i = 0; i < operations.size(); i++) {
            ResultOperation operation = operations.get(i);
            List<String> row = operation.getDataList();
            List<String> odlRow = operation.getOldDataList();
            String sql = "";
            if ("UPDATE".equalsIgnoreCase(operation.getType())) {
                sql = getUpdateSql(tableName, headerList, row, odlRow, metaSchema, keyColumns, false);
                if("MYSQL".equalsIgnoreCase(dbType)){
                    sql = sql + " LIMIT 1";
                }
            } else if ("CREATE".equalsIgnoreCase(operation.getType())) {
                sql = getInsertSql(tableName, headerList, row, metaSchema);
            } else if ("DELETE".equalsIgnoreCase(operation.getType())) {
                sql = getDeleteSql(tableName, headerList, odlRow, metaSchema, keyColumns);
                if("MYSQL".equalsIgnoreCase(dbType)){
                    sql = sql + " LIMIT 1";
                }
            } else if ("UPDATE_COPY".equalsIgnoreCase(operation.getType())) {
                sql = getUpdateSql(tableName, headerList, row, row, metaSchema, keyColumns, true);
            }
            stringBuilder.append(sql + ";\n");
        }
        return stringBuilder.toString();
    }

    @Override
    public String getTableDmlSql(Table table, String type) {
        if (table == null || CollectionUtils.isEmpty(table.getColumnList()) || StringUtils.isBlank(type)) {
            return "";
        }
        if (DmlType.INSERT.name().equalsIgnoreCase(type)) {
            return getInsertSql(table.getName(), table.getColumnList());
        } else if (DmlType.UPDATE.name().equalsIgnoreCase(type)) {
            return getUpdateSql(table.getName(), table.getColumnList());
        } else if (DmlType.DELETE.name().equalsIgnoreCase(type)) {
            return getDeleteSql(table.getName(), table.getColumnList());
        } else if (DmlType.SELECT.name().equalsIgnoreCase(type)) {
            return getSelectSql(table.getName(), table.getColumnList());
        }
        return "";
    }

    private String getSelectSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("SELECT ");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(" FROM where").append(name);
        return script.toString();
    }

    private String getDeleteSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("DELETE FROM ").append(name)
                .append(" where ");
        return script.toString();
    }

    private String getUpdateSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("UPDATE ").append(name)
                .append(" set ");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(" = ")
                    .append(" ")
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(" where ");
        return script.toString();
    }

    private String getInsertSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("INSERT INTO ").append(name)
                .append(" (");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(") VALUES (");
        for (TableColumn column : columnList) {
            script.append(" ")
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();
    }

    /**
     * Generates the base part of the INSERT SQL statement.
     * Optionally includes column names if provided.
     *
     * @param databaseName
     * @param schemaName   Name of the database schema.
     * @param tableName    Name of the table to insert into.
     * @param columnList   Optional list of column names.
     * @return The base part of the INSERT SQL statement.
     */
    protected String buildBaseInsertSql(String databaseName, String schemaName, String tableName, List<String> columnList) {
        StringBuilder script = new StringBuilder();

        script.append("INSERT INTO ");

        buildTableName(databaseName, schemaName, tableName, script);

        buildColumns(columnList, script);

        script.append(" VALUES ");
        return script.toString();
    }

    protected void buildColumns(List<String> columnList, StringBuilder script) {
        if (CollectionUtils.isNotEmpty(columnList)) {
            script.append(" (")
                    .append(String.join(",", columnList))
                    .append(") ");
        }
    }

    protected void buildTableName(String databaseName, String schemaName, String tableName, StringBuilder script) {
        if (StringUtils.isNotBlank(databaseName)) {
            script.append(databaseName).append('.');
        }
        if (StringUtils.isNotBlank(schemaName)) {
            script.append(schemaName).append('.');
        }

        script.append(tableName);
    }

    /**
     * Generates a single INSERT SQL statement for one record.
     *
     * @param schemaName Name of the database schema.
     * @param tableName  Name of the table to insert into.
     * @param columnList Optional list of column names.
     * @param valueList  List of values to be inserted.
     * @return The complete INSERT SQL statement for a single record.
     */
    public String buildSingleInsertSql(String databaseName, String schemaName, String tableName, List<String> columnList, List<String> valueList) {
        String baseSql = buildBaseInsertSql(databaseName, schemaName, tableName, columnList);
        List<String> list = valueList.stream().map(EasyStringUtils::escapeLineString).toList();
        return baseSql + "(" + String.join(",", list) + ")";
    }

    /**
     * Generates a multi-row INSERT SQL statement.
     *
     * @param schemaName Name of the database schema.
     * @param tableName  Name of the table to insert into.
     * @param columnList Optional list of column names.
     * @param valueLists List of lists, each inner list represents values for a row.
     * @return The complete multi-row INSERT SQL statement.
     */
    public String buildMultiInsertSql(String databaseName, String schemaName, String tableName, List<String> columnList, List<List<String>> valueLists) {
        String baseSql = buildBaseInsertSql(databaseName, schemaName, tableName, columnList);
        String valuesPart = valueLists.stream()
                .map(values -> "(" + String.join(",", values.stream().map(EasyStringUtils::escapeLineString).toList()) + ")")
                .collect(Collectors.joining(",\n"));
        return baseSql + valuesPart;
    }


    @Override
    public String buildUpdateSql(String databaseName, String schemaName, String tableName, Map<String, String> row, Map<String, String> primaryKeyMap) {
        StringBuilder script = new StringBuilder();
        script.append("UPDATE ");
        buildTableName(databaseName, schemaName, tableName, script);

        script.append(" SET ");
        List<String> setClauses = row.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.toList());
        script.append(String.join(",", setClauses));

        if (MapUtils.isNotEmpty(primaryKeyMap)) {
            script.append(" WHERE ");
            List<String> whereClauses = primaryKeyMap.entrySet().stream()
                    .map(entry -> entry.getKey() + " = " + entry.getValue())
                    .collect(Collectors.toList());
            script.append(String.join(" AND ", whereClauses));
        }
        return script.toString();
    }

    private List<String> getPrimaryColumns(List<Header> headerList) {
        if (CollectionUtils.isEmpty(headerList)) {
            return Lists.newArrayList();
        }
        List<String> keyColumns = Lists.newArrayList();
        for (Header header : headerList) {
            if (header.getPrimaryKey() != null && header.getPrimaryKey()) {
                keyColumns.add(header.getName());
            }
        }
        return keyColumns;
    }

    private String getDeleteSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema,
                                List<String> keyColumns) {
        StringBuilder script = new StringBuilder();
        script.append("DELETE FROM ").append(tableName).append("");
        script.append(buildWhere(headerList, row, metaSchema, keyColumns));
        return script.toString();
    }

    private String buildWhere(List<Header> headerList, List<String> row, MetaData metaSchema, List<String> keyColumns) {
        StringBuilder script = new StringBuilder();
        script.append(" where ");
        if (CollectionUtils.isEmpty(keyColumns)) {
            for (int i = 1; i < row.size(); i++) {
                String oldValue = row.get(i);
                Header header = headerList.get(i);
                String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
                if (value == null) {
                    script.append(metaSchema.getMetaDataName(header.getName()))
                            .append(" is null and ");
                } else {
                    script.append(metaSchema.getMetaDataName(header.getName()))
                            .append(" = ")
                            .append(value)
                            .append(" and ");
                }
            }
        } else {
            for (int i = 1; i < row.size(); i++) {
                String oldValue = row.get(i);
                Header header = headerList.get(i);
                String columnName = header.getName();
                if (keyColumns.contains(columnName)) {
                    String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
                    if (value == null) {
                        script.append(metaSchema.getMetaDataName(columnName))
                                .append(" is null and ");
                    } else {
                        script.append(metaSchema.getMetaDataName(columnName))
                                .append(" = ")
                                .append(value)
                                .append(" and ");
                    }
                }
            }
        }
        script.delete(script.length() - 4, script.length());
        return script.toString();
    }

    private String getInsertSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema) {
        if (CollectionUtils.isEmpty(row) || ObjectUtils.allNull(row.toArray())) {
            return "";
        }
        StringBuilder script = new StringBuilder();
        script.append("INSERT INTO ").append(tableName)
                .append(" (");

        ValueProcessor valueProcessor = metaSchema.getValueProcessor();
        for (int i = 1; i < row.size(); i++) {
            Header header = headerList.get(i);
            //String newValue = row.get(i);
            //if (newValue != null) {
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(",");
            // }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(") VALUES (");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            //if (newValue != null) {
            Header header = headerList.get(i);
            SQLDataValue sqlDataValue =  new SQLDataValue();
            String value =  valueProcessor.getSqlValueString(sqlDataValue);
            script.append(value)
                    .append(",");
            //}
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();

    }

    private String getUpdateSql(String tableName, List<Header> headerList, List<String> row, List<String> odlRow,
                                MetaData metaSchema,
                                List<String> keyColumns, boolean copy) {
        StringBuilder script = new StringBuilder();
        if (CollectionUtils.isEmpty(row) || CollectionUtils.isEmpty(odlRow)) {
            return "";
        }
        script.append("UPDATE ").append(tableName).append(" set ");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            String oldValue = odlRow.get(i);
            if (StringUtils.equals(newValue, oldValue) && !copy) {
                continue;
            }
            Header header = headerList.get(i);
            String newSqlValue = SqlUtils.getSqlValue(newValue, header.getDataType());
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(" = ")
                    .append(newSqlValue)
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(buildWhere(headerList, odlRow, metaSchema, keyColumns));
        return script.toString();
    }
}
