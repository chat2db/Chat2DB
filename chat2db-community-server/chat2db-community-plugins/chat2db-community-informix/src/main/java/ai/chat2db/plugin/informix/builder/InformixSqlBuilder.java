package ai.chat2db.plugin.informix.builder;

import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.enums.plugin.IndexTypeEnum;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.informix.InformixMetaData;
import ai.chat2db.plugin.informix.parser.InformixSqlParser;
import ai.chat2db.plugin.informix.validation.InformixTableModificationValidator;
import ai.chat2db.spi.DefaultSqlBuilder;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.apache.commons.lang3.StringUtils;

import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Set;

import static ai.chat2db.plugin.informix.constant.InformixExplainConstants.EXPLAIN_PREFIX;
import static ai.chat2db.plugin.informix.constant.InformixSqlBuilderConstants.*;

/** Informix table alterations and the EXPLAIN command handled by its executor. */
public class InformixSqlBuilder extends DefaultSqlBuilder {

    private static final Set<String> LENGTH_TYPES = Set.of("CHAR", "CHARACTER", "NCHAR", "VARCHAR", "NVARCHAR",
            "LVARCHAR", "DECIMAL", "DEC", "NUMERIC", "MONEY");
    private static final Set<String> SCALE_TYPES = Set.of("DECIMAL", "DEC", "NUMERIC", "MONEY");

    @Override
    public String buildExplain(String sql) {
        // Consumed by InformixCommandExecutor; never sent to JDBC as executable SQL.
        return InformixSqlParser.extractExplainSql(sql) == null ? EXPLAIN_PREFIX + sql : sql;
    }

    @Override
    public String buildAlterTable(Table oldTable, Table newTable) {
        // Only MODIFY needs live constraint inspection; other DDL stays connection-free.
        if (newTable.getColumnList().stream()
                .anyMatch(column -> EditStatusEnum.MODIFY.name().equals(column.getEditStatus()))) {
            new InformixTableModificationValidator(new InformixMetaData())
                    .validate(Chat2DBContext.getConnection(), oldTable, newTable);
        }
        StringBuilder script = new StringBuilder();
        String tableName = qualifiedTable(oldTable.getSchemaName(), newTable.getName());
        if (!StringUtils.equals(oldTable.getName(), newTable.getName())) {
            // Informix: RENAME TABLE old TO new (not ALTER TABLE old RENAME TO new)
            script.append(RENAME_TABLE_SQL.formatted(qualifiedTable(oldTable.getSchemaName(), oldTable.getName()),
                    identifier(newTable.getName()))).append("\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append(generateTableCommentSQL(tableName, newTable.getComment())).append("\n");
        }
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())
                    && StringUtils.isNotBlank(tableColumn.getColumnType())
                    && StringUtils.isNotBlank(tableColumn.getName())) {
                script.append(generateColumnAlterSQL(tableColumn, oldTable, newTable.getName())).append("\n");
            }
        }
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus())
                    && StringUtils.isNotBlank(tableIndex.getType())) {
                script.append(generateIndexAlterSQL(tableIndex, oldTable.getSchemaName(), tableName)).append("\n");
            }
        }
        return script.toString();
    }

    private String generateColumnAlterSQL(TableColumn tableColumn, Table oldTable, String tableName) {
        String owner = StringUtils.defaultIfBlank(oldTable.getSchemaName(), tableColumn.getSchemaName());
        String target = qualifiedTable(owner, tableName);
        String columnName = identifier(tableColumn.getName());
        if (EditStatusEnum.DELETE.name().equals(tableColumn.getEditStatus())) {
            return DROP_COLUMN_SQL.formatted(target, columnName);
        }
        if (EditStatusEnum.ADD.name().equals(tableColumn.getEditStatus())) {
            return ADD_COLUMN_SQL.formatted(target, columnName, tableColumn.getColumnType());
        }
        if (EditStatusEnum.MODIFY.name().equals(tableColumn.getEditStatus())) {
            // Match the owner used by constraint inspection; setSchema() does not
            // change the default owner in Informix JDBC.
            String oldName = tableColumn.getOldColumn() == null
                    ? StringUtils.defaultIfBlank(tableColumn.getOldName(), tableColumn.getName())
                    : tableColumn.getOldColumn().getName();
            String rename = StringUtils.equals(oldName, tableColumn.getName()) ? ""
                    : RENAME_COLUMN_SQL.formatted(target, identifier(oldName), columnName) + "\n";
            return rename + MODIFY_COLUMN_SQL.formatted(target, columnName, columnDefinition(tableColumn));
        }
        if (tableColumn.getComment() != null) {
            return COMMENT_COLUMN_SQL.formatted(target, columnName, tableColumn.getComment().replace("'", "''"));
        }
        return "";
    }

    static String columnDefinition(TableColumn column) {
        String type = column.getColumnType().trim();
        String baseType = type.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (Set.of("CHARACTER VARYING", "CHAR VARYING").contains(baseType)) {
            type = "VARCHAR";
            baseType = type;
        }
        Integer size = column.getColumnSize();
        if (size != null && size > 0 && LENGTH_TYPES.contains(baseType)) {
            type += "(" + size;
            if (SCALE_TYPES.contains(baseType)
                    && column.getDecimalDigits() != null && column.getDecimalDigits() >= 0) {
                type += "," + column.getDecimalDigits();
            }
            type += ")";
        }
        StringBuilder definition = new StringBuilder(type);
        if (StringUtils.isNotBlank(column.getDefaultValue())) {
            definition.append(COLUMN_DEFAULT_SQL.formatted(column.getDefaultValue()));
        }
        Integer nullable = column.getNullable();
        if (nullable == null && column.getOldColumn() != null) {
            nullable = column.getOldColumn().getNullable();
        }
        if (Integer.valueOf(DatabaseMetaData.columnNoNulls).equals(nullable)) {
            definition.append(COLUMN_NOT_NULL_SQL);
        }
        return definition.toString();
    }

    private String generateIndexAlterSQL(TableIndex tableIndex, String owner, String tableName) {
        if (EditStatusEnum.DELETE.name().equals(tableIndex.getEditStatus())) {
            return DROP_INDEX_SQL.formatted(qualifiedTable(owner, tableIndex.getName()));
        }
        if (EditStatusEnum.ADD.name().equals(tableIndex.getEditStatus())) {
            StringBuilder columnNames = new StringBuilder();
            for (TableIndexColumn column : tableIndex.getColumnList()) {
                if (columnNames.length() > 0) {
                    columnNames.append(", ");
                }
                columnNames.append(identifier(column.getColumnName()));
            }
            boolean unique = IndexTypeEnum.UNIQUE.getName().equals(tableIndex.getType());
            String template = unique ? CREATE_UNIQUE_INDEX_SQL : CREATE_INDEX_SQL;
            return template.formatted(qualifiedTable(owner, tableIndex.getName()), tableName, columnNames);
        }
        return "";
    }

    private static String qualifiedTable(String owner, String name) {
        // Informix accepts a quoted owner string in ANSI and non-ANSI databases,
        // including connections without DELIMIDENT. It also preserves owner case.
        String prefix = StringUtils.isBlank(owner) ? "" : "'" + owner.replace("'", "''") + "'.";
        return prefix + identifier(name);
    }

    private static String identifier(String name) {
        // Ordinary names also work without DELIMIDENT. Names requiring delimiters
        // use the same DELIMIDENT connection setting required to create them.
        return name.matches("[A-Za-z_][A-Za-z0-9_$]*") ? name : "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private String generateTableCommentSQL(String tableName, String comment) {
        if (comment == null) {
            return CLEAR_TABLE_COMMENT_SQL.formatted(tableName);
        }
        return COMMENT_TABLE_SQL.formatted(tableName, comment.replace("'", "''"));
    }
}
