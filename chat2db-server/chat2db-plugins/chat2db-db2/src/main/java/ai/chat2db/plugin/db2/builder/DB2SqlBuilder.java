package ai.chat2db.plugin.db2.builder;

import ai.chat2db.plugin.db2.type.DB2ColumnTypeEnum;
import ai.chat2db.plugin.db2.type.DB2IndexTypeEnum;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;

public class DB2SqlBuilder extends DefaultSqlBuilder {

    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();

        script.append("CREATE TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" (").append("\n");

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            DB2ColumnTypeEnum typeEnum = DB2ColumnTypeEnum.getByType(column.getColumnType());
            if (typeEnum == null) {
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
            DB2IndexTypeEnum indexTypeEnum = DB2IndexTypeEnum.getByType(tableIndex.getType());
            if (indexTypeEnum == null) {
                continue;
            }
            script.append("\n").append("").append(indexTypeEnum.buildIndexScript(tableIndex)).append(";");
            if(StringUtils.isNotBlank(tableIndex.getComment())){
                script.append("\n").append(indexTypeEnum.buildIndexComment(tableIndex)).append(";");
            }

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
                DB2ColumnTypeEnum typeEnum = DB2ColumnTypeEnum.getByType(tableColumn.getColumnType());
                if (typeEnum == null) {
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
                DB2IndexTypeEnum mysqlIndexTypeEnum = DB2IndexTypeEnum.getByType(tableIndex.getType());
                if (mysqlIndexTypeEnum == null) {
                    continue;
                }
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
                if(StringUtils.isNotBlank(tableIndex.getComment())) {
                    script.append("\n").append(mysqlIndexTypeEnum.buildIndexComment(tableIndex)).append(";\n");
                }

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
        int startRow = offset + 1;
        int endRow = offset + pageSize;
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 120);
        sqlBuilder.append("SELECT * FROM (SELECT TMP_PAGE.*,ROWNUMBER() OVER() AS CAHT2DB_AUTO_ROW_ID FROM ( \n");
        sqlBuilder.append(sql);
        sqlBuilder.append("\n ) AS TMP_PAGE) TMP_PAGE WHERE CAHT2DB_AUTO_ROW_ID BETWEEN ");
        sqlBuilder.append(startRow);
        sqlBuilder.append(" AND ");
        sqlBuilder.append(endRow);
        return sqlBuilder.toString();
    }

    @Override
    public String buildCreateSchemaSql(Schema schema) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE SCHEMA \"" + schema.getName() + "\";");

        if (StringUtils.isNotBlank(schema.getComment())) {
            sqlBuilder.append("\nCOMMENT ON SCHEMA \"").append(schema.getName()).append("\" IS '").append(schema.getComment()).append("';");
        }

        return sqlBuilder.toString();
    }



}
