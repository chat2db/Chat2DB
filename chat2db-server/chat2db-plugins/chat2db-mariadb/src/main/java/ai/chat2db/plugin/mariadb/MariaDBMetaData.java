package ai.chat2db.plugin.mariadb;


import ai.chat2db.plugin.mariadb.value.MariaDBValueProcessor;
import ai.chat2db.plugin.mysql.MysqlMetaData;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.ValueProcessor;

public class MariaDBMetaData extends MysqlMetaData implements MetaData {

    @Override
    public ValueProcessor getValueProcessor() {
        return new MariaDBValueProcessor();
    }
}
