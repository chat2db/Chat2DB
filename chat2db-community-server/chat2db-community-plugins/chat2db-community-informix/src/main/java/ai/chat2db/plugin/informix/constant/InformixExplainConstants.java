package ai.chat2db.plugin.informix.constant;

import java.util.Set;

public final class InformixExplainConstants {
    public static final String SESSION_ID_SQL = "SELECT FIRST 1 DBINFO('sessionid') FROM systables";
    public static final String CURRENT_PLAN_SQL = """
            SELECT * FROM sysmaster:syssqexplain
             WHERE sqx_sessionid = ? AND sqx_iscurrent = 'Y' AND sqx_ismain = 'Y'
            """;
    public static final String EXPLAIN_KEYWORD = "EXPLAIN";
    public static final String EXPLAIN_PREFIX = "EXPLAIN ";
    public static final Set<String> EXPLAINABLE_KEYWORDS = Set.of("SELECT", "WITH", "INSERT", "UPDATE", "DELETE", "MERGE");
    public static final String PLAN_TEXT_FIELD = "sqx_sqlstatementplan";
    public static final String ESTIMATED_COST_FIELD = "sqx_estcost";
    public static final String ESTIMATED_ROWS_FIELD = "sqx_estrows";
    public static final String PLAN_UNAVAILABLE = "PLAN UNAVAILABLE";

    private InformixExplainConstants() {
    }
}
