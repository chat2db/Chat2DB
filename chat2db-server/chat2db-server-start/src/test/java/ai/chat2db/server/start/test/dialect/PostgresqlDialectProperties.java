/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2022 All Rights Reserved.
 */
package ai.chat2db.server.start.test.dialect;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author jipengfei
 * @version : PgDialectProperties.java, v 0.1 December 13, 2022 21:48 jipengfei Exp $
 */
@Component
public class PostgresqlDialectProperties implements DialectProperties {

    @Override
    public String getDbType() {
        return "POSTGRESQL";
    }

    @Override
    public String getUrl() {
        return "jdbc:postgresql://localhost:5431/ali_dbhub_test";
    }

    @Override
    public String getErrorUrl() {
        return "jdbc:postgresql://error:5431/ali_dbhub";
    }

    @Override
    public String getUsername() {
        return "ali_dbhub";
    }

    @Override
    public String getPassword() {
        return "ali_dbhub";
    }

    @Override
    public String getDatabaseName() {
        return "ali_dbhub_test";
    }

    @Override
    public String getCrateTableSql(String tableName) {
        String sql = "CREATE TABLE " + tableName + "\n"
            + "(\n"
            + "    id     serial\n"
            + "        constraint " + tableName + "_pk primary key,\n"
            + "    date   timestamp,\n"
            + "    number int,\n"
            + "    string varchar(100) default 'DATA'\n"
            + ");\n";
        return sql;
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "drop table " + tableName + ";";
    }

    @Override
    public String getInsertSql(String tableName, Date date, Long number, String string) {
        return "INSERT INTO " + tableName + " (date,number,string) VALUES ('" + DateUtil.format(date,
            DatePattern.NORM_DATETIME_MS_FORMAT) + "','" + number + "','" + string + "');";
    }

    @Override
    public String getSelectSqlById(String tableName, Long id) {
        return "select *\n"
            + "from " + tableName + "\n"
            + "where id = '" + id + "';";
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
        return 5432;
    }
}