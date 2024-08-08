package ai.chat2db.server.web.api.controller.rdb.data.csv;

import ai.chat2db.server.domain.api.enums.ExportFileSuffix;
import ai.chat2db.server.web.api.controller.rdb.data.BaseExcelExporter;
import com.alibaba.excel.support.ExcelTypeEnum;
import org.springframework.stereotype.Component;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:05
 */
@Component("csvExporter")
public class CsvDataExporter extends BaseExcelExporter {


    public CsvDataExporter() {
        this.contentType = "text/csv";
        this.suffix = ExportFileSuffix.CSV.getSuffix();
    }


    @Override
    protected ExcelTypeEnum getExcelType() {
        return ExcelTypeEnum.CSV;
    }
}
