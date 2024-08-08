package ai.chat2db.plugin.oracle.value.template;

/**
 * @author: zgq
 * @date: 2024年06月01日 13:35
 */
public class OracleDmlValueTemplate {

    public static final String DATE_TEMPLATE = "TO_DATE('%s', 'SYYYY-MM-DD HH24:MI:SS')";

    public static final String TIMESTAMP_TEMPLATE = "TO_TIMESTAMP('%s', 'SYYYY-MM-DD HH24:MI:SS.FF%d')";

    public static final String TIMESTAMP_TZ_TEMPLATE = "TO_TIMESTAMP_TZ('%s', 'SYYYY-MM-DD HH24:MI:SS.FF%d TZR')";
    public static final String TIMESTAMP_TZ_WITHOUT_NANOS_TEMPLATE = "TO_TIMESTAMP_TZ('%s', 'SYYYY-MM-DD HH24:MI:SS TZR')";

    public static final String INTERVAL_YEAR_TO_MONTH_TEMPLATE = "INTERVAL '%s' YEAR(%d) TO MONTH";
    public static final String INTERVAL_DAY_TO_SECOND_TEMPLATE = "INTERVAL '%s' DAY(%d) TO SECOND(%d)";

    public static final String XML_TEMPLATE = "XMLType('%s')";


    public static String wrapDate(String date) {
        return String.format(DATE_TEMPLATE, date);
    }

    public static String wrapTimestamp(String timestamp, int scale) {
        return String.format(TIMESTAMP_TEMPLATE, timestamp, scale);
    }

    public static String wrapTimestampTz(String timestamp, int scale) {
        return String.format(TIMESTAMP_TZ_TEMPLATE, timestamp, scale);
    }

    public static String wrapTimestampTzWithOutNanos(String timestamp) {
        return String.format(TIMESTAMP_TZ_WITHOUT_NANOS_TEMPLATE, timestamp);
    }

    public static String wrapIntervalYearToMonth(String year, int precision) {
        return String.format(INTERVAL_YEAR_TO_MONTH_TEMPLATE, year, precision);
    }

    public static String wrapIntervalDayToSecond(String day, int precision, int scale) {
        return String.format(INTERVAL_DAY_TO_SECOND_TEMPLATE, day, precision, scale);
    }

    public static String wrapXml(String xml) {
        return String.format(XML_TEMPLATE, xml);
    }

}
