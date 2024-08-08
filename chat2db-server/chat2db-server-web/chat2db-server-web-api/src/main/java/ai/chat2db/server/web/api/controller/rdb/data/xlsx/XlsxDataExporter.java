package ai.chat2db.server.web.api.controller.rdb.data.xlsx;

import ai.chat2db.server.domain.api.enums.ExportFileSuffix;
import ai.chat2db.server.web.api.controller.rdb.data.BaseExcelExporter;
import com.alibaba.excel.support.ExcelTypeEnum;
import org.springframework.stereotype.Component;

/**
 * @author: zgq
 * @date: 2024年06月04日 10:34
 */
@Component("xlsxExporter")
public class XlsxDataExporter extends BaseExcelExporter {

    public XlsxDataExporter() {
        this.suffix = ExportFileSuffix.EXCEL.getSuffix();
        this.contentType="application/vnd.ms-excel";
    }


    @Override
    protected ExcelTypeEnum getExcelType() {
        return ExcelTypeEnum.XLSX;
    }
}
