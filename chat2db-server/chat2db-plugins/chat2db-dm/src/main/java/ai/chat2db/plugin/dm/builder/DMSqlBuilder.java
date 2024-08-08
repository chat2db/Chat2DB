package ai.chat2db.plugin.dm.builder;

import ai.chat2db.plugin.dm.type.DMColumnTypeEnum;
import ai.chat2db.plugin.dm.type.DMIndexTypeEnum;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class DMSqlBuilder  extends DefaultSqlBuilder {


    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();

        script.append("CREATE TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" (").append("\n");

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            DMColumnTypeEnum typeEnum = DMColumnTypeEnum.getByType(column.getColumnType());
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
            DMIndexTypeEnum indexTypeEnum = DMIndexTypeEnum.getByType(tableIndex.getType());
            if(indexTypeEnum == null){
                continue;
            }
            script.append("\n").append("").append(indexTypeEnum.buildIndexScript(tableIndex)).append(";");
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
            String editStatus = tableColumn.getEditStatus();
            if (StringUtils.isNotBlank(editStatus)) {
                DMColumnTypeEnum typeEnum = DMColumnTypeEnum.getByType(tableColumn.getColumnType());
                if(typeEnum == null){
                    continue;
                }
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
                if (StringUtils.isNotBlank(tableColumn.getComment())&&!Objects.equals(EditStatus.DELETE.toString(),editStatus)) {
                    script.append("\n").append(buildComment(tableColumn)).append(";\n");
                }
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                DMIndexTypeEnum mysqlIndexTypeEnum = DMIndexTypeEnum.getByType(tableIndex.getType());
                if(mysqlIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
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
        StringBuilder sqlStr = new StringBuilder(sql.length() + 17);
        sqlStr.append(sql);
        if (offset == 0) {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
        } else {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
            sqlStr.append(" OFFSET ");
            sqlStr.append(offset);
        }
        return sqlStr.toString();
    }

    @Override
    public String buildCreateSchemaSql(Schema schema) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE SCHEMA \""+schema.getName()+"\"");
        if(StringUtils.isNotBlank(schema.getOwner())){
            sqlBuilder.append(" AUTHORIZATION ").append(schema.getOwner());
        }

        return sqlBuilder.toString();
    }
}
