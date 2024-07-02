package ai.chat2db.plugin.sqlserver.template;

import ai.chat2db.server.tools.common.util.EasyStringUtils;

/**
 * @author: zgq
 * @date: 2024年06月21日 21:11
 */
public class SQLTemplate {

    public static final String TABLE_COMMENT_TEMPLATE = "exec sp_addextendedproperty 'MS_Description',N'%s','SCHEMA',N'%s','TABLE',N'%s' \ngo\n";
    public static final String INDEX_COMMENT_TEMPLATE = "exec sp_addextendedproperty 'MS_Description',N'%s','SCHEMA',N'%s','TABLE',N'%s','INDEX',N'%s' \ngo\n";
    public static final String COLUMN_COMMENT_TEMPLATE = "exec sp_addextendedproperty 'MS_Description',N'%s','SCHEMA',N'%s','TABLE',N'%s','COLUMN',N'%s' \ngo\n";
    public static final String CONSTRAINT_COMMENT_TEMPLATE = "exec sp_addextendedproperty 'MS_Description',N'%s','SCHEMA',N'%s','TABLE',N'%s','CONSTRAINT',N'%s' \ngo\n";

    public static String buildTableComment(String tableComment, String schemaName, String tableName) {
        return String.format(TABLE_COMMENT_TEMPLATE, EasyStringUtils.escapeString(tableComment), schemaName, tableName);
    }

    public static String buildIndexComment(String indexComment, String schemaName, String tableName, String indexName) {
        return String.format(INDEX_COMMENT_TEMPLATE, EasyStringUtils.escapeString(indexComment), schemaName, tableName, indexName);
    }

    public static String buildColumnComment(String columnComment, String schemaName, String tableName, String columnName) {
        return String.format(COLUMN_COMMENT_TEMPLATE, EasyStringUtils.escapeString(columnComment), schemaName, tableName, columnName);
    }

    public static String buildConstraintComment(String constraintComment, String schemaName, String tableName, String constraintName) {
        return String.format(CONSTRAINT_COMMENT_TEMPLATE, EasyStringUtils.escapeString(constraintComment), schemaName, tableName, constraintName);
    }

}
