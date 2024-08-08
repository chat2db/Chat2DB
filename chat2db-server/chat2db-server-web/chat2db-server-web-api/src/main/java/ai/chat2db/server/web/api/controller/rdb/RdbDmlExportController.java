package ai.chat2db.server.web.api.controller.rdb;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueProcessor;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.SQLUtils.FormatOption;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement.ValuesClause;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;

import ai.chat2db.server.domain.api.enums.ExportSizeEnum;
import ai.chat2db.server.domain.api.enums.ExportTypeEnum;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.request.DataExportRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.SqlUtils;
import cn.hutool.core.date.DatePattern;
import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Export Database Exclusive
 *
 * @author Jiaju Zhuang
 */
@ConnectionInfoAspect
@RequestMapping("/api/rdb/dml")
@Controller
@Slf4j
public class RdbDmlExportController {

    /**
     * Format insert statement
     */
    private static final FormatOption INSERT_FORMAT_OPTION = new FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }

    /**
     * export data
     *
     * @param request
     * @return
     */
    @PostMapping("/export")
    public void export(@Valid @RequestBody DataExportRequest request, HttpServletResponse response) throws IOException {
        ExportSizeEnum exportSize = EasyEnumUtils.getEnum(ExportSizeEnum.class, request.getExportSize());
        ExportTypeEnum exportType = EasyEnumUtils.getEnum(ExportTypeEnum.class, request.getExportType());
        String sql;
        if (exportSize == ExportSizeEnum.CURRENT_PAGE) {
            sql = request.getSql();
        } else {
            sql = request.getOriginalSql();
        }
        if (StringUtils.isBlank(sql)) {
            throw new ParamBusinessException("exportSize");
        }
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String tableName;
        if (dbType != null) {
            SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
            if (!(sqlStatement instanceof SQLSelectStatement)) {
                throw new BusinessException("dataSource.sqlAnalysisError");
            }
            tableName = SqlUtils.getTableName(sql, dbType);
        } else {
            tableName = StringUtils.join(Lists.newArrayList(request.getDatabaseName(), request.getSchemaName()), "_");
        }

        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(
                        tableName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                        StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        if (exportType == ExportTypeEnum.CSV) {
            doExportCsv(sql, response, fileName);
        } else {
            doExportInsert(sql, response, fileName, dbType, tableName);
        }
    }

    private void doExportCsv(String sql, HttpServletResponse response, String fileName)
            throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".csv");

        ExcelWrapper excelWrapper = new ExcelWrapper();
        try {
            ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
            ExcelWriterBuilder excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                    .charset(StandardCharsets.UTF_8)
                    .excelType(ExcelTypeEnum.CSV);
            excelWrapper.setExcelWriterBuilder(excelWriterBuilder);
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, headerList -> {
                excelWriterBuilder.head(
                        EasyCollectionUtils.toList(headerList, header -> Lists.newArrayList(header.getName())));
                excelWrapper.setExcelWriter(excelWriterBuilder.build());
                excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());
            }, dataList -> {
                List<List<String>> writeDataList = Lists.newArrayList();
                writeDataList.add(dataList);
                excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
            }, jdbcDataValue -> valueProcessor.getJdbcValue(jdbcDataValue), false);
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }

    private void doExportInsert(String sql, HttpServletResponse response, String fileName, DbType dbType,
                                String tableName)
            throws IOException {
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".sql");
        ValueProcessor valueProcessor = Chat2DBContext.getMetaData().getValueProcessor();
        SqlBuilder sqlBuilder = Chat2DBContext.getMetaData().getSqlBuilder();
        try (PrintWriter printWriter = response.getWriter()) {
            List<String> headerColumns = Lists.newArrayList();
            InsertWrapper insertWrapper = new InsertWrapper();
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql,
                    headerList -> {
                        headerList.forEach(sqlIdentifierExpr -> headerColumns.add(sqlIdentifierExpr.getName()));
                    }
                    , dataList -> {
                        SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
                        sqlInsertStatement.setDbType(dbType);
                        sqlInsertStatement.setTableSource(new SQLExprTableSource(tableName));
                        sqlInsertStatement.getColumns().addAll(insertWrapper.getHeaderList());
                        ValuesClause valuesClause = new ValuesClause();
                        for (String s : dataList) {
                            valuesClause.addValue(s);
                        }
                        sqlInsertStatement.setValues(valuesClause);
                        String sqls = sqlBuilder.buildSingleInsertSql(null, null, tableName, headerColumns, dataList);
                        printWriter.println(sqls + ";");
                    }, jdbcDataValue -> valueProcessor.getJdbcSqlValueString(jdbcDataValue), false);
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsertWrapper {
        private List<SQLIdentifierExpr> headerList;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcelWrapper {
        private ExcelWriterBuilder excelWriterBuilder;
        private ExcelWriter excelWriter;
        private WriteSheet writeSheet;
    }

}
