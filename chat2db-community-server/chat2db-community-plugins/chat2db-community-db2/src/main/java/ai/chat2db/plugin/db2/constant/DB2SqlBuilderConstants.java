package ai.chat2db.plugin.db2.constant;

import ai.chat2db.spi.constant.SQLConstants;

import ai.chat2db.plugin.db2.enums.type.DB2ColumnTypeEnum;
import ai.chat2db.plugin.db2.enums.type.DB2IndexTypeEnum;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.model.request.PageLimitRequest;
import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import org.apache.commons.lang3.StringUtils;


import static ai.chat2db.spi.constant.SQLConstants.PAGINATION_ROW_ID;

public final class DB2SqlBuilderConstants {

    public static final String VALUE_DOUBLE_QUOTE_OPEN_PAREN = "\" (";
    public static final String VALUE_DOUBLE_QUOTE_IS_SINGLE_QUOTE = "\" IS '";
    public static final String SQL_CLOSE_PAREN_AS_TMP_PAGE_CLOSE_PAREN_TMP_PAGE_WHERE_CAHT2DB_AUTO_ROW_ID = "\n ) AS TMP_PAGE) TMP_PAGE WHERE " + PAGINATION_ROW_ID + " BETWEEN ";
    public static final String SQL_COMMENT_ON_SCHEMA_DOUBLE_QUOTE = "\nCOMMENT ON SCHEMA \"";
    public static final String SQL_EXPLAIN_PLAN_SET_QUERYNO_EQUAL_1_FOR = "EXPLAIN PLAN SET QUERYNO = 1 FOR ";
    public static final String SQL_ALTER_TABLE = "ALTER TABLE ";
    public static final String SQL_AND = " AND ";
    public static final String SQL_COMMENT_COLUMN = "COMMENT ON COLUMN ";
    public static final String SQL_COMMENT_TABLE = "COMMENT ON TABLE ";
    public static final String SQL_CREATE_SCHEMA = "CREATE SCHEMA \"";
    public static final String SQL_CREATE_TABLE = "CREATE TABLE ";
    public static final String SQL_RENAME = "RENAME TO ";
    public static final String SQL_SELECT_SELECT_TMP_PAGE_ROWNUMBER = "SELECT * FROM (SELECT TMP_PAGE.*,ROWNUMBER() OVER() AS " + PAGINATION_ROW_ID + " FROM ( \n";

    private DB2SqlBuilderConstants() {
    }
}
