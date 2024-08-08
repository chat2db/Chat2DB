package ai.chat2db.server.web.api.controller.rdb.data.factory;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.rdb.data.DataExportStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:26
 */
@Component
public class DataExportFactory {

    public static final String BEAN_SUFFIX = "Exporter";
    private final Map<String, DataExportStrategy> exports;

    @Autowired
    public DataExportFactory(Map<String, DataExportStrategy> exports) {
        this.exports = exports;
    }

    public DataExportStrategy getExporter(String type) {
        DataExportStrategy dataExportStrategy = exports.get(type.toLowerCase() + BEAN_SUFFIX);
        if (Objects.isNull(dataExportStrategy)) {
            throw new ParamBusinessException(type);
        }
        return dataExportStrategy;
    }
}
