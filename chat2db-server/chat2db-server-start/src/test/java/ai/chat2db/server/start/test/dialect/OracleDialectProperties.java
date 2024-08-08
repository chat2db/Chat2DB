package ai.chat2db.server.start.test.dialect;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
@Component
public class OracleDialectProperties implements DialectProperties {

    @Override
    public String getDbType() {
        return "ORACLE";
    }

    @Override
    public String getUrl() {
        return "jdbc:oracle:thin:@localhost:1521:XE";
    }

    @Override
    public String getErrorUrl() {
        return "jdbc:oracle:thin:@localhost:1521:XE1";
    }

    @Override
    public String getUsername() {
        return "system";
    }

    @Override
    public String getPassword() {
        return "ali_dbhub";
    }

    @Override
    public String getDatabaseName() {
        return "TEST_USER";
    }

    @Override
    public String getCrateTableSql(String tableName) {
        return "CREATE TABLE TEST_USER." + tableName + " (\n" +
                "  id NUMBER PRIMARY KEY,\n" +
                "  created_date DATE,\n" +
                "  amount INT,\n" +
                "  string VARCHAR2(100)\n" +
                ");";
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "drop table " + tableName + ";";
    }

    @Override
    public String getInsertSql(String tableName, Date date, Long number, String string) {
        return "INSERT INTO TEST_USER." + tableName + " (date,number,string) VALUES ('" + DateUtil.format(date,
                DatePattern.NORM_DATETIME_MS_FORMAT) + "','" + number + "','" + string + "');";
    }

    @Override
    public String getSelectSqlById(String tableName, Long id) {
        return "select *\n\t"
                + "from " + tableName + "\n\t"
                + "where `id` = '" + id + "';";
    }

    @Override
    public String getTableNotFoundSqlById(String tableName) {
        return "select *\n"
                + "from " + tableName + "_notfound;";
    }

    @Override
    public String toCase(String string) {
        return StringUtils.toRootLowerCase(string);
    }

    @Override
    public Integer getPort() {
        return 11521;
    }
}
