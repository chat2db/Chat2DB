package ai.chat2db.plugin.hive.builder;

import ai.chat2db.plugin.hive.type.HiveColumnTypeEnum;
import ai.chat2db.plugin.hive.type.HiveIndexTypeEnum;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;


public class HiveSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
        if (StringUtils.isNotBlank(table.getDatabaseName())) {
            script.append("`").append(table.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(table.getName()).append("`").append(" (").append("\n");

        // append column
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(column.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            HiveIndexTypeEnum hiveIndexTypeEnum = HiveIndexTypeEnum.getByType(tableIndex.getType());
            if(hiveIndexTypeEnum == null){
                continue;
            }
            script.append("\t").append("").append(hiveIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)");


        if (StringUtils.isNotBlank(table.getEngine())) {
            script.append(" ENGINE=").append(table.getEngine());
        }

        if (StringUtils.isNotBlank(table.getCharset())) {
            script.append(" DEFAULT CHARACTER SET=").append(table.getCharset());
        }

        if (StringUtils.isNotBlank(table.getCollate())) {
            script.append(" COLLATE=").append(table.getCollate());
        }

        if (table.getIncrementValue() != null) {
            script.append(" AUTO_INCREMENT=").append(table.getIncrementValue());
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(" COMMENT '").append(table.getComment()).append("'");
        }

        if (StringUtils.isNotBlank(table.getPartition())) {
            script.append(" \n").append(table.getPartition());
        }
        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        boolean isModify = false;
        script.append("ALTER TABLE ");
        if (StringUtils.isNotBlank(newTable.getDatabaseName())) {
            script.append("`").append(newTable.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(oldTable.getName()).append("`").append("\n");
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("\t").append("RENAME TO ").append("`").append(newTable.getName()).append("`").append(";\n");
            isModify = true;
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            if (isModify) {
                script.append("ALTER TABLE ");
                if (StringUtils.isNotBlank(newTable.getDatabaseName())) {
                    script.append("`").append(newTable.getDatabaseName()).append("`").append(".");
                }
                script.append("`").append(newTable.getName()).append("`").append("\n");
            }
            script.append("\t").append("SET TBLPROPERTIES ('comment' = ").append("'").append(newTable.getComment()).append("'),\n");
        }

        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType()) && StringUtils.isNotBlank(tableColumn.getName())) {
                HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(tableColumn.getColumnType());
                if(typeEnum == null){
                    continue;
                }
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                HiveIndexTypeEnum hiveIndexTypeEnum = HiveIndexTypeEnum.getByType(tableIndex.getType());
                if(hiveIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(hiveIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
            }
        }

        // append reorder column
       // script.append(buildGenerateReorderColumnSql(oldTable, newTable));

        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
        }

        return script.toString();
    }


    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 14);
        sqlBuilder.append(sql);
        if (offset == 0) {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(pageSize);
        } else {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(offset);
            sqlBuilder.append(",");
            sqlBuilder.append(pageSize);
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE `" + database.getName() + "`");
        if (StringUtils.isNotBlank(database.getComment())) {
            sqlBuilder.append("\r\n COMMENT '").append(database.getComment()).append("'");

        }
        return sqlBuilder.toString();
    }

}
