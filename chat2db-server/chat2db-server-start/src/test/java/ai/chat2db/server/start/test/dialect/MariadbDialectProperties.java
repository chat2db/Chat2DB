package ai.chat2db.server.start.test.dialect;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MariadbDialectProperties implements DialectProperties{
    @Override
    public String getDbType() {
        return "MARIADB";
    }

    @Override
    public String getUrl() {
        return "jdbc:mariadb://localhost:3303/";
    }

    @Override
    public String getErrorUrl() {
        return "jdbc:mariadb://error:3303/";
    }

    @Override
    public String getUsername() {
        return "root";
    }

    @Override
    public String getPassword() {
        return "ali_dbhub";
    }

    @Override
    public String getDatabaseName() {
        return null;
    }

    @Override
    public String getCrateTableSql(String tableName) {
        return null;
    }

    @Override
    public String getDropTableSql(String tableName) {
        return null;
    }

    @Override
    public String getInsertSql(String tableName, Date date, Long number, String string) {
        return null;
    }

    @Override
    public String getSelectSqlById(String tableName, Long id) {
        return null;
    }

    @Override
    public String getTableNotFoundSqlById(String tableName) {
        return null;
    }

    @Override
    public String toCase(String string) {
        return StringUtils.toRootLowerCase(string);
    }

    @Override
    public Integer getPort() {
        return 13303;
    }
}
