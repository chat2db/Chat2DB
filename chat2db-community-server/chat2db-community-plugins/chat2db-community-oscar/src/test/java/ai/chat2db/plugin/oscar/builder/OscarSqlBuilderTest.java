package ai.chat2db.plugin.oscar.builder;

import ai.chat2db.spi.model.request.PageLimitRequest;
import static ai.chat2db.spi.constant.SQLConstants.PAGINATION_ROW_ID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OscarSqlBuilderTest {

    @Test
    void shouldLimitFirstPageWithoutExposingSyntheticRowId() {
        OscarSqlBuilder builder = new OscarSqlBuilder();

        String sql = builder.buildPageLimit(PageLimitRequest.builder()
                .sql("SELECT ID, NAME FROM EMPLOYEE")
                .offset(0)
                .pageNo(1)
                .pageSize(10)
                .build());

        assertEquals("SELECT * FROM ( \nSELECT ID, NAME FROM EMPLOYEE\n ) TMP_PAGE WHERE ROWNUM <= 10", sql);
        assertFalse(sql.contains(PAGINATION_ROW_ID));
    }

    @Test
    void shouldApplyUpperAndLowerBoundsAfterFirstPage() {
        OscarSqlBuilder builder = new OscarSqlBuilder();

        PageLimitRequest request = PageLimitRequest.builder()
                .sql("SELECT ID, NAME FROM EMPLOYEE")
                .offset(10)
                .pageNo(2)
                .pageSize(10)
                .build();
        String sql = builder.buildPageLimit(request);
        String rowId = request.getPaginationRowId();
        org.junit.jupiter.api.Assertions.assertTrue(rowId.matches("CHAT2DB_AUTO_ROW_ID_[a-f0-9]{10}"));

        assertEquals("SELECT * FROM (  SELECT TMP_PAGE.*, ROWNUM " + rowId + " FROM ( \n"
                        + "SELECT ID, NAME FROM EMPLOYEE\n"
                        + " ) TMP_PAGE WHERE ROWNUM <= 20 ) WHERE " + rowId + " > 10",
                sql);
    }
}
