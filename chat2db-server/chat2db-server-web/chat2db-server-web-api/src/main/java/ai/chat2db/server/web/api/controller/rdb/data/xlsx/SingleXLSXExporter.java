package ai.chat2db.server.web.api.controller.rdb.data.xlsx;

import ai.chat2db.server.domain.api.enums.ExportFileSuffix;
import ai.chat2db.server.web.api.controller.rdb.data.BaseSingleExcelExporter;
import com.alibaba.excel.support.ExcelTypeEnum;

/**
 * 功能描述
 *
 * @author: zgq
 * @date: 2024年04月26日 14:05
 */
public class SingleXLSXExporter extends BaseSingleExcelExporter {

    public SingleXLSXExporter() {
        suffix = ExportFileSuffix.EXCEL.getSuffix();
        contentType = "application/vnd.ms-excel";
    }
    @Override
    protected ExcelTypeEnum getExcelType() {
        return ExcelTypeEnum.XLSX;
    }
}
