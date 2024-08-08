package ai.chat2db.plugin.sqlserver.builder;

import ai.chat2db.plugin.sqlserver.type.SqlServerColumnTypeEnum;
import ai.chat2db.plugin.sqlserver.type.SqlServerIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.apache.commons.lang3.StringUtils;

public class SqlServerSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();

        script.append("CREATE TABLE ").append("[").append(table.getSchemaName()).append("].[").append(table.getName()).append("] (").append("\n");

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            SqlServerColumnTypeEnum typeEnum = SqlServerColumnTypeEnum.getByType(column.getColumnType());
            if (typeEnum == null) {
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)\ngo\n");

        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            SqlServerIndexTypeEnum sqlServerIndexTypeEnum = SqlServerIndexTypeEnum.getByType(tableIndex.getType());
            if (sqlServerIndexTypeEnum == null) {
                continue;
            }
            script.append("\n").append(sqlServerIndexTypeEnum.buildIndexScript(tableIndex));
            if (StringUtils.isNotBlank(tableIndex.getComment())) {
                script.append("\n").append(buildIndexComment(tableIndex));
            }
        }

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType()) || StringUtils.isBlank(column.getComment())) {
                continue;
            }
            script.append("\n").append(buildColumnComment(column));
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append("\n").append(buildTableComment(table));
        }


        return script.toString();
    }

    private static String INDEX_COMMENT_SCRIPT = "exec sp_addextendedproperty 'MS_Description','%s','SCHEMA','%s','TABLE','%s','INDEX','%s' \ngo";


    private String buildIndexComment(TableIndex tableIndex) {
        return String.format(INDEX_COMMENT_SCRIPT, tableIndex.getComment(), tableIndex.getSchemaName(), tableIndex.getTableName(), tableIndex.getName());
    }

    private static String TABLE_COMMENT_SCRIPT = "exec sp_addextendedproperty 'MS_Description','%s','SCHEMA','%s','TABLE','%s' \ngo";


    private String buildTableComment(Table table) {
        return String.format(TABLE_COMMENT_SCRIPT, table.getComment(), table.getSchemaName(), table.getName());
    }

    private static String COLUMN_COMMENT_SCRIPT = "exec sp_addextendedproperty 'MS_Description','%s','SCHEMA','%s','TABLE','%s','COLUMN','%s' \ngo";

    private String buildColumnComment(TableColumn column) {
        return String.format(COLUMN_COMMENT_SCRIPT, column.getComment(), column.getSchemaName(), column.getTableName(), column.getName());
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();

        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append(buildRenameTable(oldTable, newTable));
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            if (oldTable.getComment() == null) {
                script.append("\n").append(buildTableComment(newTable));
            } else {
                script.append("\n").append(buildUpdateTableComment(newTable));
            }
        }


        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())) {
                SqlServerColumnTypeEnum typeEnum = SqlServerColumnTypeEnum.getByType(tableColumn.getColumnType());
                if (typeEnum == null) {
                    continue;
                }
                script.append(typeEnum.buildModifyColumn(tableColumn)).append("\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                SqlServerIndexTypeEnum mysqlIndexTypeEnum = SqlServerIndexTypeEnum.getByType(tableIndex.getType());
                if (mysqlIndexTypeEnum == null) {
                    continue;
                }
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append("\n");
                if (StringUtils.isNotBlank(tableIndex.getComment())) {
                    script.append("\n").append(buildIndexComment(tableIndex)).append("\ngo");
                }
            }
        }

        return script.toString();
    }

    private static String UPDATE_TABLE_COMMENT_SCRIPT = "exec sp_updateextendedproperty 'MS_Description','%s','SCHEMA','%s','TABLE','%s' \ngo";

    private String buildUpdateTableComment(Table newTable) {
        return String.format(UPDATE_TABLE_COMMENT_SCRIPT, newTable.getComment(), newTable.getSchemaName(), newTable.getName());
    }

    private static String RENAME_TABLE_SCRIPT = "exec sp_rename '%s','%s','OBJECT' \ngo";

    private String buildRenameTable(Table oldTable, Table newTable) {
        return String.format(RENAME_TABLE_SCRIPT, oldTable.getName(), newTable.getName());
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        String version = Chat2DBContext.getDbVersion();
        if (StringUtils.isNotBlank(version)) {
            String[] versions = version.split("\\.");
            if (versions.length > 0 && Integer.parseInt(versions[0]) >= 11) {
                StringBuilder sqlBuilder = new StringBuilder(sql.length() + 14);
                sqlBuilder.append(sql);
                if(!sql.toLowerCase().contains("order by")){
                    sqlBuilder.append("\n ORDER BY (SELECT NULL)");
                }
                sqlBuilder.append("\n OFFSET ");
                sqlBuilder.append(offset);
                sqlBuilder.append(" ROWS ");
                sqlBuilder.append(" FETCH NEXT ");
                sqlBuilder.append(pageSize);
                sqlBuilder.append(" ROWS ONLY");
                return sqlBuilder.toString();
            }
        }
        return "";
    }


    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE [" + database.getName() + "]");
        if (StringUtils.isNotBlank(database.getCollation())) {
            sqlBuilder.append(" COLLATE ").append(database.getCollation());
        }
        sqlBuilder.append("\ngo\n");
        if (StringUtils.isNotBlank(database.getComment())) {
            sqlBuilder.append("exec [" + database.getName() + "].sys. sp_addextendedproperty 'MS_Description','")
                    .append(database.getComment()).append("'").append("\ngo\n");
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateSchemaSql(Schema schema) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE SCHEMA [" + schema.getName() + "] \ngo\n");
        if (StringUtils.isNotBlank(schema.getComment())) {
            sqlBuilder.append("exec sp_addextendedproperty 'MS_Description','")
                    .append(schema.getComment()).append("'").append(",'SCHEMA'")
                    .append(",'").append(schema.getName()).append("'").append("\ngo\n");
        }
        return sqlBuilder.toString();
    }
}
