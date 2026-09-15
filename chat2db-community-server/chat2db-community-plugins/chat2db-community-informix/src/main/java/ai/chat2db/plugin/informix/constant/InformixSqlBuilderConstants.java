package ai.chat2db.plugin.informix.constant;

public final class InformixSqlBuilderConstants {
    public static final String RENAME_TABLE_SQL = "RENAME TABLE %s TO %s;";
    public static final String RENAME_COLUMN_SQL = "RENAME COLUMN %s.%s TO %s;";
    public static final String ADD_COLUMN_SQL = "ALTER TABLE %s ADD COLUMN %s %s;";
    public static final String DROP_COLUMN_SQL = "ALTER TABLE %s DROP COLUMN %s;";
    public static final String MODIFY_COLUMN_SQL = "ALTER TABLE %s MODIFY (%s %s);";
    public static final String COMMENT_COLUMN_SQL = "COMMENT ON COLUMN %s.%s IS '%s';";
    public static final String COMMENT_TABLE_SQL = "COMMENT ON TABLE %s IS '%s';";
    public static final String CLEAR_TABLE_COMMENT_SQL = "COMMENT ON TABLE %s IS NULL;";
    public static final String CREATE_INDEX_SQL = "CREATE INDEX %s ON %s (%s);";
    public static final String CREATE_UNIQUE_INDEX_SQL = "CREATE UNIQUE INDEX %s ON %s (%s);";
    public static final String DROP_INDEX_SQL = "DROP INDEX %s;";
    public static final String COLUMN_DEFAULT_SQL = " DEFAULT %s";
    public static final String COLUMN_NOT_NULL_SQL = " NOT NULL";

    private InformixSqlBuilderConstants() {
    }
}
