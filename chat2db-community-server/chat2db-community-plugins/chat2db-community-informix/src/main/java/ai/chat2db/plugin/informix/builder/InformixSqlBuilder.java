package ai.chat2db.plugin.informix.builder;

import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.enums.plugin.IndexTypeEnum;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.plugin.informix.InformixExplainClient;
import org.apache.commons.lang3.StringUtils;

import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Set;

/** Informix table alterations and the EXPLAIN command handled by its executor. */
public class InformixSqlBuilder extends DefaultSqlBuilder {

    @Override
    public String buildExplain(String sql) {
        // Consumed by InformixCommandExecutor; never sent to JDBC as executable SQL.
        return InformixExplainClient.explainSql(sql) == null ? "EXPLAIN " + sql : sql;
    }

    @Override
    public String buildAlterTable(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            // Informix: RENAME TABLE old TO new (not ALTER TABLE old RENAME TO new)
            script.append("RENAME TABLE ").append(oldTable.getName()).append(" TO ")
                    .append(newTable.getName()).append(";\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append(generateTableCommentSQL(newTable.getName(), newTable.getComment())).append("\n");
        }
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())
                    && StringUtils.isNotBlank(tableColumn.getColumnType())
                    && StringUtils.isNotBlank(tableColumn.getName())) {
                if (EditStatusEnum.MODIFY.name().equals(tableColumn.getEditStatus())) {
                    InformixColumnConstraints.check(oldTable, tableColumn);
                }
                script.append(generateColumnAlterSQL(tableColumn, oldTable, newTable.getName())).append("\n");
            }
        }
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus())
                    && StringUtils.isNotBlank(tableIndex.getType())) {
                script.append(generateIndexAlterSQL(tableIndex)).append("\n");
            }
        }
        return script.toString();
    }

    private String generateColumnAlterSQL(TableColumn tableColumn, Table oldTable, String tableName) {
        if (EditStatusEnum.DELETE.name().equals(tableColumn.getEditStatus())) {
            return "ALTER TABLE " + tableColumn.getTableName() + " DROP COLUMN " + tableColumn.getName() + ";";
        }
        if (EditStatusEnum.ADD.name().equals(tableColumn.getEditStatus())) {
            return "ALTER TABLE " + tableColumn.getTableName() + " ADD COLUMN " + tableColumn.getName() + " "
                    + tableColumn.getColumnType() + ";";
        }
        if (EditStatusEnum.MODIFY.name().equals(tableColumn.getEditStatus())) {
            // Informix: ALTER TABLE t MODIFY (col type) (not MODIFY COLUMN col type)
            // Match the owner used by constraint inspection, even when another
            // schema has a table with the same name.
            String owner = StringUtils.defaultIfBlank(oldTable.getSchemaName(), tableColumn.getSchemaName());
            String target = StringUtils.isBlank(owner) ? tableName : owner + "." + tableName;
            return "ALTER TABLE " + target + " MODIFY (" + tableColumn.getName() + " "
                    + columnDefinition(tableColumn) + ");";
        }
        if (tableColumn.getComment() != null) {
            return "COMMENT ON COLUMN " + tableColumn.getTableName() + "." + tableColumn.getName()
                    + " IS '" + tableColumn.getComment().replace("'", "''") + "';";
        }
        return "";
    }

    static String columnDefinition(TableColumn column) {
        String type = column.getColumnType();
        String baseType = type.toUpperCase(Locale.ROOT);
        Integer size = column.getColumnSize();
        if (size != null && size > 0 && Set.of("CHAR", "CHARACTER", "NCHAR", "VARCHAR", "NVARCHAR", "LVARCHAR",
                "DECIMAL", "DEC", "NUMERIC", "MONEY").contains(baseType)) {
            type += "(" + size;
            if (Set.of("DECIMAL", "DEC", "NUMERIC", "MONEY").contains(baseType)
                    && column.getDecimalDigits() != null && column.getDecimalDigits() >= 0) {
                type += "," + column.getDecimalDigits();
            }
            type += ")";
        }
        StringBuilder definition = new StringBuilder(type);
        if (StringUtils.isNotBlank(column.getDefaultValue())) {
            definition.append(" DEFAULT ").append(column.getDefaultValue());
        }
        Integer nullable = column.getNullable();
        if (nullable == null && column.getOldColumn() != null) {
            nullable = column.getOldColumn().getNullable();
        }
        if (Integer.valueOf(DatabaseMetaData.columnNoNulls).equals(nullable)) {
            definition.append(" NOT NULL");
        }
        return definition.toString();
    }

    private String generateIndexAlterSQL(TableIndex tableIndex) {
        if (EditStatusEnum.DELETE.name().equals(tableIndex.getEditStatus())) {
            return "DROP INDEX " + tableIndex.getName() + ";";
        }
        if (EditStatusEnum.ADD.name().equals(tableIndex.getEditStatus())) {
            StringBuilder columnNames = new StringBuilder();
            for (TableIndexColumn column : tableIndex.getColumnList()) {
                if (columnNames.length() > 0) {
                    columnNames.append(", ");
                }
                columnNames.append(column.getColumnName());
            }
            boolean unique = IndexTypeEnum.UNIQUE.getName().equals(tableIndex.getType());
            return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + tableIndex.getName() + " ON "
                    + tableIndex.getTableName() + " (" + columnNames + ");";
        }
        return "";
    }

    private String generateTableCommentSQL(String tableName, String comment) {
        if (comment == null) {
            return "COMMENT ON TABLE " + tableName + " IS NULL;";
        }
        return "COMMENT ON TABLE " + tableName + " IS '" + comment.replace("'", "''") + "';";
    }
}
