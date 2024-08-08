package ai.chat2db.server.web.api.controller.rdb.data;

import ai.chat2db.server.domain.api.param.datasource.DatabaseExportDataParam;
import ai.chat2db.server.web.api.util.StringUtils;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:51
 */
@Slf4j
public abstract class BaseDataExporter implements DataExportStrategy {

    protected String contentType;
    protected String suffix;

    public static int BATCH_SIZE = 1000;

    @Override
    public void doExport(DatabaseExportDataParam databaseExportDataParam, File file) throws IOException, SQLException {
        List<String> tableNames = databaseExportDataParam.getTableNames();
        if (CollectionUtils.isEmpty(tableNames)) {
            throw new IllegalArgumentException("tableNames should not be null or empty");
        }
        try (Connection connection = Chat2DBContext.getConnection()) {
            if (tableNames.size() == 1) {
                String tableName = tableNames.get(0);
                if (StringUtils.isEmpty(tableName)) {
                    throw new IllegalArgumentException("tableName should not be null or empty");
                }
                singleExport(connection, databaseExportDataParam,file);
            } else {
                multiExport(databaseExportDataParam, connection, file);
            }
        }

    }


    private void multiExport(DatabaseExportDataParam databaseExportDataParam,
                             Connection connection, File file) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(file);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            List<String> tableNames = databaseExportDataParam.getTableNames();
            for (String tableName : tableNames) {
                String fileName = tableName + suffix;
                zipOutputStream.putNextEntry(new ZipEntry(fileName));
                try (ByteArrayOutputStream byteArrayOutputStream = multiExport(connection, databaseExportDataParam, tableName)) {
                    byteArrayOutputStream.writeTo(zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
        }
    }


    protected abstract void singleExport(Connection connectionInfo, DatabaseExportDataParam databaseExportDataParam, File file) throws IOException, SQLException;


    protected abstract ByteArrayOutputStream multiExport(Connection connection, DatabaseExportDataParam databaseExportDataParam, String tableName);
}
