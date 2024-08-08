package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.*;
import ai.chat2db.server.domain.api.param.datasource.DatabaseCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseExportParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.spi.model.*;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

import java.sql.SQLException;

/**
 * Data source management services
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 September 23, 2022 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface DatabaseService {

    /**
     * Query all databases under the data source
     *
     * @param param
     * @return
     */
    ListResult<Database> queryAll(DatabaseQueryAllParam param);

    /**
     * Query the schema under a database
     * @param param
     * @return
     */
    ListResult<Schema> querySchema(SchemaQueryParam param);

    /**
     * query Database and Schema
     * @param param
     * @return
     */
    DataResult<MetaSchema> queryDatabaseSchema(MetaDataQueryParam param);



    /**
     * Delete database
     *
     * @param param
     * @return
     */
    ActionResult deleteDatabase(DatabaseCreateParam param);

    /**
     * create database
     *
     * @param param
     * @return
     */
    DataResult<Sql> createDatabase(Database param);

    /**
     * Modify database
     *
     * @return
     */
    ActionResult modifyDatabase( DatabaseCreateParam param) ;

    /**
     * Delete schema
     *
     * @param param
     * @return
     */
    ActionResult deleteSchema(SchemaOperationParam param) ;

    /**
     * Create schema
     *
     * @param schema
     * @return
     */
    DataResult<Sql> createSchema(Schema schema);

    /**
     * Modify schema
     *
     * @param request
     * @return
     */
    ActionResult modifySchema( SchemaOperationParam request);

    /**
     * Export database
     *
     * @param param
     * @return
     */
    String exportDatabase(DatabaseExportParam param) throws SQLException;
}
