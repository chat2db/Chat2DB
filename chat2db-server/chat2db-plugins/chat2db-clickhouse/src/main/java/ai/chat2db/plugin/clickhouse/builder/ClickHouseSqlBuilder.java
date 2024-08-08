package ai.chat2db.plugin.clickhouse.builder;

import ai.chat2db.plugin.clickhouse.type.ClickHouseColumnTypeEnum;
import ai.chat2db.plugin.clickhouse.type.ClickHouseIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;


public class ClickHouseSqlBuilder extends DefaultSqlBuilder {
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
            ClickHouseColumnTypeEnum typeEnum = ClickHouseColumnTypeEnum.getByType(column.getColumnType());
            if (typeEnum != null){
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            ClickHouseIndexTypeEnum mysqlIndexTypeEnum = ClickHouseIndexTypeEnum.getByType(tableIndex.getType());
            if (!ClickHouseIndexTypeEnum.PRIMARY.equals(mysqlIndexTypeEnum) ) {
                script.append("\t").append("").append(mysqlIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
            }
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)");


        if (StringUtils.isNotBlank(table.getEngine())) {
            script.append(" ENGINE=").append(table.getEngine()).append("\n");
        }
        // append primary key
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            ClickHouseIndexTypeEnum mysqlIndexTypeEnum = ClickHouseIndexTypeEnum.getByType(tableIndex.getType());
            if (ClickHouseIndexTypeEnum.PRIMARY.equals(mysqlIndexTypeEnum) ) {
                script.append("\t").append("").append(mysqlIndexTypeEnum.buildIndexScript(tableIndex)).append("\n");
            }
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(" COMMENT '").append(table.getComment()).append("'");
        }

        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        script.append("ALTER TABLE ");
        if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
            script.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(oldTable.getName()).append("`").append("\n");

        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("\t").append("MODIFY COMMENT").append("'").append(newTable.getComment()).append("'").append(",\n");
        }

        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType()) && StringUtils.isNotBlank(tableColumn.getName())) {
                ClickHouseColumnTypeEnum typeEnum = ClickHouseColumnTypeEnum.getByType(tableColumn.getColumnType());
                if(typeEnum == null){
                    continue;
                }
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                ClickHouseIndexTypeEnum clickHouseIndexTypeEnum = ClickHouseIndexTypeEnum
                        .getByType(tableIndex.getType());
                if(clickHouseIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(clickHouseIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
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
        if(StringUtils.isNotBlank(database.getComment())){
            sqlBuilder.append(";ALTER DATABASE ").append(database.getName()).append(" COMMENT '").append(database.getComment()).append("';");
        }
        return sqlBuilder.toString();
    }

}
