package ai.chat2db.plugin.oracle.constant;

import ai.chat2db.spi.constant.SQLConstants;

import ai.chat2db.plugin.oracle.enums.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.enums.type.OracleIndexTypeEnum;
import ai.chat2db.plugin.oracle.util.OracleUtil;
import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.model.request.PageLimitRequest;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.spi.util.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;


import static ai.chat2db.spi.constant.SQLConstants.PAGINATION_ROW_ID;

public final class OracleSqlBuilderConstants {

    public static final String SQL_WHERE_ROWID_IN_OPEN_PAREN_SELECT_ROWID_FROM = " where rowid in (select rowid from ";
    public static final String VALUE_AND_ROWNUM_EQUAL_1_CLOSE_PAREN = " and rownum = 1)";
    public static final String VALUE_DOUBLE_QUOTE_IS_SINGLE_QUOTE = "\" IS '";
    public static final String SQL_CLOSE_PAREN_TMP_PAGE_WHERE_ROWNUM_EQUAL = " ) TMP_PAGE WHERE ROWNUM <= ";
    public static final String SQL_CLOSE_PAREN_WHERE_CAHT2DB_AUTO_ROW_ID = " ) WHERE " + PAGINATION_ROW_ID + " > ";
    public static final String UNDEFINED_KEYWORD = "undefined";
    public static final String SQL_SHARING_EQUAL = "SHARING = ";
    public static final String SQL_DEFAULT_COLLATE = "DEFAULT COLLATE ";
    public static final String SQL_CHECK_OPTION_CONSTRAINT = "CHECK OPTION CONSTRAINT";
    public static final String SQL_EXPLAIN_PLAN_FOR = "EXPLAIN PLAN FOR ";
    public static final String SQL_ALTER_TABLE = "ALTER TABLE ";
    public static final String SQL_COMMENT_COLUMN = "COMMENT ON COLUMN ";
    public static final String SQL_COMMENT_TABLE = "comment on table";
    public static final String SQL_COMMENT_TABLE_2 = "COMMENT ON TABLE ";
    public static final String SQL_CREATE = "CREATE ";
    public static final String SQL_CREATE_TABLE = "CREATE TABLE ";
    public static final String SQL_RENAME = "RENAME TO ";
    public static final String SQL_REPLACE = "OR REPLACE ";
    public static final String SQL_SELECT = "SELECT * FROM ( ";
    public static final String SQL_SELECT_TMP_PAGE_ROWNUM_CAHT2DB = " SELECT TMP_PAGE.*, ROWNUM " + PAGINATION_ROW_ID + " FROM ( ";

    private OracleSqlBuilderConstants() {
    }
}
