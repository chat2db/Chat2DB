package ai.chat2db.server.web.api.controller.rdb.data;

import ai.chat2db.server.domain.api.param.datasource.DatabaseExportDataParam;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public interface DataExportStrategy {


    void doExport(DatabaseExportDataParam databaseExportDataParam, File file) throws IOException, SQLException;
}
