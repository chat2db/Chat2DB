package ai.chat2db.plugin.xugudb;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.Plugin;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.util.FileUtils;

public class XUGUDBPlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"xugudb.json", DBConfig.class);

    }

    @Override
    public MetaData getMetaData() {
        return new XUGUDBMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new XUGUDBManage();
    }
}
