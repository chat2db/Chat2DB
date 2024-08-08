package ai.chat2db.server.start.test.dialect;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MongodbDialectProperties implements DialectProperties{
    @Override
    public String getDbType() {
        return "MONGODB";
    }

    @Override
    public String getUrl() {
        return "mongodb://localhost:27017/";
    }

    @Override
    public String getErrorUrl() {
        return "mongodb://error:27017/";
    }

    @Override
    public String getUsername() {
        return "test";
    }

    @Override
    public String getPassword() {
        return "test@123456";
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
        return 27017;
    }
}
