package ai.chat2db.plugin.oscar.constant;

import static ai.chat2db.spi.constant.SQLConstants.PAGINATION_ROW_ID;

public final class OscarConstants {

    public static final String CONFIG_FILE = "oscar.json";
    public static final String CONNECT_DATABASE_ERROR_MESSAGE = "connectDatabase error";
    public static final String DEFAULT_SCHEMA = "SYSDBA";
    public static final String SET_SEARCH_PATH_SQL = "SET search_path TO ";

    public static final String EMPTY_STRING_TOKEN = "EMPTY_STRING";
    public static final String NULL_TOKEN = "NULL";

    public static final String VIEW_NAME_FORM_KEY = "viewName";
    public static final String USE_OR_REPLACE_FORM_KEY = "useOrReplace";
    public static final String COMMENT_FORM_KEY = "comment";
    public static final String VIEW_NAME_FORM_LABEL = "view name";
    public static final String USE_OR_REPLACE_FORM_LABEL = "use or replace";
    public static final String COMMENT_FORM_LABEL = "comment";
    public static final String VIEW_PREVIEW_BODY = "select * from table_name";
    public static final String UNDEFINED_OBJECT_NAME = "undefined";

    public static final String CATALOG_TABLE_OWNER = "TABLE_OWNER";
    public static final String CATALOG_TABLE_NAME = "TABLE_NAME";
    public static final String CATALOG_VIEW_NAME = "VIEW_NAME";
    public static final String CATALOG_TRIGGER_NAME = "TRIGGER_NAME";
    public static final String CATALOG_TRIGGER_TYPE = "TRIGGER_TYPE";
    public static final String CATALOG_TRIGGERING_EVENT = "TRIGGERING_EVENT";
    public static final String CATALOG_TRIGGER_BODY = "TRIGGER_BODY";
    public static final String CATALOG_SOURCE_TEXT = "TEXT";
    public static final String CATALOG_ASCENDING_SORT = "A";
    public static final String CATALOG_DESCENDING_SORT = "D";
    public static final String INDEX_ASCENDING_SORT_SQL = "ASC";
    public static final String INDEX_DESCENDING_SORT_SQL = "DESC";

    public static final String ROUTINES_SQL = """
            SELECT LINE, TEXT
            FROM ALL_SOURCE
            WHERE OWNER = '%s' AND NAME = '%s'
            ORDER BY LINE
            """;

    public static final String TRIGGER_LIST_SQL = """
            SELECT TRIGGER_NAME
            FROM ALL_TRIGGERS
            WHERE OWNER = '%s'
            """;

    public static final String TRIGGER_DETAIL_SQL = """
            SELECT TRIGGER_NAME, TRIGGER_TYPE, TRIGGERING_EVENT, TABLE_OWNER, TABLE_NAME, TRIGGER_BODY
            FROM ALL_TRIGGERS
            WHERE OWNER = '%s' AND TRIGGER_NAME = '%s'
            """;

    public static final String VIEW_DDL_SQL = """
            SELECT VIEW_NAME, TEXT
            FROM ALL_VIEWS
            WHERE OWNER = '%s' AND VIEW_NAME = '%s'
            """;

    public static final String PAGE_OUTER_SELECT_PREFIX = "SELECT * FROM ( ";
    public static final String PAGE_INNER_SELECT_PREFIX = " SELECT TMP_PAGE.*, ROWNUM " + PAGINATION_ROW_ID + " FROM ( ";
    public static final String PAGE_ROWNUM_FILTER_SQL = " ) TMP_PAGE WHERE ROWNUM <= ";
    public static final String PAGE_AUTO_ROW_ID_FILTER_SQL = " ) WHERE " + PAGINATION_ROW_ID + " > ";
    public static final String INTERVAL_DAY_TO_SECOND_SQL_TEMPLATE = "INTERVAL DAY(%d) TO SECOND(%d)";
    public static final String INTERVAL_YEAR_TO_MONTH_SQL_TEMPLATE = "INTERVAL YEAR(%d) TO MONTH";

    private OscarConstants() {
    }
}
