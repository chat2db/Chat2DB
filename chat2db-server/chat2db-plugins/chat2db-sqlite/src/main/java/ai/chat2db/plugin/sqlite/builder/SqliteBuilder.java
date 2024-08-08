package ai.chat2db.plugin.sqlite.builder;

import ai.chat2db.plugin.sqlite.type.SqliteColumnTypeEnum;
import ai.chat2db.plugin.sqlite.type.SqliteIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import org.apache.commons.lang3.StringUtils;


public class SqliteBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
        script.append("\"").append(table.getDatabaseName()).append("\".\"").append(table.getName()).append("\"").append(" (").append("\n");

        // append column
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            SqliteColumnTypeEnum typeEnum = SqliteColumnTypeEnum.getByType(column.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }
        for (TableIndex tableIndex : table.getIndexList()) {
            if(SqliteIndexTypeEnum.PRIMARY_KEY.getName().equals( tableIndex.getType())) {
                SqliteIndexTypeEnum sqliteIndexTypeEnum = SqliteIndexTypeEnum.getByType(tableIndex.getType());
                if(sqliteIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(sqliteIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
            }
        }
        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n);");

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            if(!SqliteIndexTypeEnum.PRIMARY_KEY.getName().equals( tableIndex.getType())) {
                SqliteIndexTypeEnum sqliteIndexTypeEnum = SqliteIndexTypeEnum.getByType(tableIndex.getType());
                if(sqliteIndexTypeEnum == null){
                    continue;
                }
                script.append("\n").append("CREATE ").append(sqliteIndexTypeEnum.buildIndexScript(tableIndex)).append(";\n");
            }
        }
        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("ALTER TABLE ").append("\"").append(oldTable.getDatabaseName()).append("\".\"").append(oldTable.getName()).append("\"").append("\n");
            script.append("\t").append("RENAME TO ").append("\"").append(newTable.getName()).append("\"").append(";\n");
        }


        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType()) && StringUtils.isNotBlank(tableColumn.getName())) {
                script.append("ALTER TABLE ").append("\"").append(newTable.getDatabaseName()).append("\".\"").append(newTable.getName()).append("\"").append("\n");
                SqliteColumnTypeEnum typeEnum = SqliteColumnTypeEnum.getByType(tableColumn.getColumnType());
                if(typeEnum == null){
                    continue;
                }
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                // script.append("ALTER TABLE ").append("\"").append(newTable.getDatabaseName()).append("\".\"").append(newTable.getName()).append("\"").append("\n");
                SqliteIndexTypeEnum sqliteIndexTypeEnum = SqliteIndexTypeEnum.getByType(tableIndex.getType());
                if(sqliteIndexTypeEnum == null){
                    continue;
                }
                script.append("\t").append(sqliteIndexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
            }
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append(";");

        return script.toString();
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        return "select * from(" + sql + ") t LIMIT " + pageSize + " OFFSET " + offset + "";
    }
}
