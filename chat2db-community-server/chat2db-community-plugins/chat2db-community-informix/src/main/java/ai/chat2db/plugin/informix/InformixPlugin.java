package ai.chat2db.plugin.informix;


import ai.chat2db.spi.IDbManager;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.community.domain.api.config.DBConfig;
import ai.chat2db.spi.util.FileUtils;

public class InformixPlugin extends InformixSyntaxPlugin implements IPlugin {

    private DBConfig dbConfig;

    @Override
    public DBConfig getDBConfig() {
        if(dbConfig != null){
            return dbConfig;
        }
        dbConfig = FileUtils.readJsonValue(this.getClass(),"informix.json", DBConfig.class);
        return dbConfig;
    }

    @Override
    public IDbMetaData getDbMetaData() {
        return new InformixMetaData();
    }

    @Override
    public IDbManager getDbManager() {
        return new InformixDBManager();
    }
}
