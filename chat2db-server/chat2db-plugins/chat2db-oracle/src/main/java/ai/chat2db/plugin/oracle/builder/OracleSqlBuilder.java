package ai.chat2db.plugin.oracle.builder;

import ai.chat2db.plugin.oracle.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.type.OracleIndexTypeEnum;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.util.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class OracleSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();

        script.append("CREATE TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" (").append("\n");

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            OracleColumnTypeEnum typeEnum = OracleColumnTypeEnum.getByType(column.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n);");

        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            OracleIndexTypeEnum oracleColumnTypeEnum = OracleIndexTypeEnum.getByType(tableIndex.getType());
            if(oracleColumnTypeEnum == null){
                continue;
            }
            script.append("\n").append("").append(oracleColumnTypeEnum.buildIndexScript(tableIndex)).append(";");
        }

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType()) || StringUtils.isBlank(column.getComment())) {
                continue;
            }
            script.append("\n").append(buildComment(column)).append(";");
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append("\n").append(buildTableComment(table)).append(";");
        }


        return script.toString();
    }

    private String buildTableComment(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("COMMENT ON TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" IS '").append(table.getComment()).append("'");
        return script.toString();
    }

    private String buildComment(TableColumn column) {
        StringBuilder script = new StringBuilder();
        script.append("COMMENT ON COLUMN ").append("\"").append(column.getSchemaName()).append("\".\"").append(column.getTableName()).append("\".\"").append(column.getName()).append("\" IS '").append(column.getComment()).append("'");
        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();

        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("ALTER TABLE ").append("\"").append(oldTable.getSchemaName()).append("\".\"").append(oldTable.getName()).append("\"");
            script.append(" ").append("RENAME TO ").append("\"").append(newTable.getName()).append("\"").append(";\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("").append(buildTableComment(newTable)).append(";\n");
        }


        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())) {
                OracleColumnTypeEnum typeEnum = OracleColumnTypeEnum.getByType(tableColumn.getColumnType());
                if(typeEnum == null){
                    continue;
                }
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
                if (StringUtils.isNotBlank(tableColumn.getComment())) {
                    script.append("\n").append(buildComment(tableColumn)).append(";\n");
                }
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                OracleIndexTypeEnum oracleIndexTypeEnum = OracleIndexTypeEnum.getByType(tableIndex.getType());
                if(oracleIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(oracleIndexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
            }
        }
        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
        }

        return script.toString();
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        int startRow = offset;
        int endRow = offset + pageSize;
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 120);
        if (startRow > 0) {
            sqlBuilder.append("SELECT * FROM ( ");
        }
        if (endRow > 0) {
            sqlBuilder.append(" SELECT TMP_PAGE.*, ROWNUM CAHT2DB_AUTO_ROW_ID FROM ( ");
        }
        sqlBuilder.append("\n");
        sqlBuilder.append(sql);
        sqlBuilder.append("\n");
        if (endRow > 0) {
            sqlBuilder.append(" ) TMP_PAGE WHERE ROWNUM <= ");
            sqlBuilder.append(endRow);
        }
        if (startRow > 0) {
            sqlBuilder.append(" ) WHERE CAHT2DB_AUTO_ROW_ID > ");
            sqlBuilder.append(startRow);
        }
        return sqlBuilder.toString();
    }

//    @Override
//    public String buildCreateSchemaSql(Schema schema){
//        StringBuilder sqlBuilder = new StringBuilder();
//        sqlBuilder.append("CREATE SCHEMA \""+schema.getName()+"\"");
//        if(StringUtils.isNotBlank(schema.getOwner())){
//            sqlBuilder.append(" AUTHORIZATION ").append(schema.getOwner());
//        }
//        if(StringUtils.isNotBlank(schema.getComment())){
//            sqlBuilder.append("; COMMENT ON SCHEMA \"").append(schema.getName()).append("\" IS '").append(schema.getComment()).append("';");
//        }
//        return sqlBuilder.toString();
//    }


    @Override
    protected void buildTableName(String databaseName, String schemaName, String tableName, StringBuilder script) {
        if (StringUtils.isNotBlank(databaseName)) {
            script.append(SqlUtils.quoteObjectName(databaseName)).append('.');
        }
        script.append(SqlUtils.quoteObjectName(tableName));
    }

    /**
     * @param columnList
     * @param script
     */
    @Override
    protected void buildColumns(List<String> columnList, StringBuilder script) {
        if (CollectionUtils.isNotEmpty(columnList)) {
            script.append(" (")
                    .append(columnList.stream().map(SqlUtils::quoteObjectName).collect(Collectors.joining(",")))
                    .append(") ");
        }
    }
}
