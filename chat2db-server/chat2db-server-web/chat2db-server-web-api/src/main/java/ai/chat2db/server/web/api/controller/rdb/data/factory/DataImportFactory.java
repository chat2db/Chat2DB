package ai.chat2db.server.web.api.controller.rdb.data.factory;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.rdb.data.DataImportStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:07
 */
@Component
public class DataImportFactory {


    private static final String BEAN_SUFFIX = "Importer";
    private final Map<String, DataImportStrategy> imports;

    @Autowired
    public DataImportFactory(Map<String, DataImportStrategy> imports) {
        this.imports = imports;
    }

    public DataImportStrategy getImporter(String type) {
        DataImportStrategy dataImportStrategy = imports.get(type.toLowerCase() + BEAN_SUFFIX);
        if (Objects.isNull(dataImportStrategy)) {
            throw new ParamBusinessException(type);
        }
        return dataImportStrategy;
    }

}
