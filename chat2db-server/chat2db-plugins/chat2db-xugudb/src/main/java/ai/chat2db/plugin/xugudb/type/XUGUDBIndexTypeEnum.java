package ai.chat2db.plugin.xugudb.type;

import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.model.IndexType;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.model.TableIndexColumn;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public enum XUGUDBIndexTypeEnum {

    PRIMARY_KEY("Primary", "PRIMARY KEY"),

    NORMAL("Normal", "INDEX"),

    UNIQUE("Unique", "UNIQUE INDEX"),

    BTREE("BTREE", "btree");



    public IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType;
    }

    private IndexType indexType;


    public String getName() {
        return name;
    }

    private String name;


    public String getKeyword() {
        return keyword;
    }

    private String keyword;

    XUGUDBIndexTypeEnum(String name, String keyword) {
        this.name = name;
        this.keyword = keyword;
        this.indexType = new IndexType(name);
    }


    public static XUGUDBIndexTypeEnum getByType(String type) {
        for (XUGUDBIndexTypeEnum value : XUGUDBIndexTypeEnum.values()) {
            if (value.name.equalsIgnoreCase(type)) {
                return value;
            }
        }
        return null;
    }

    public String buildIndexScript(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        if (PRIMARY_KEY.equals(this)) {
            script.append("ALTER TABLE \"").append(tableIndex.getSchemaName()).append("\".\"").append(tableIndex.getTableName()).append("\" ADD PRIMARY KEY ").append(buildIndexColumn(tableIndex));
        } else {
            if (UNIQUE.equals(this)) {
                script.append("CREATE UNIQUE INDEX ");
            } else {
                script.append("CREATE INDEX ");
            }
            script.append(buildIndexName(tableIndex)).append(" ON \"").append(tableIndex.getSchemaName()).append("\".\"").append(tableIndex.getTableName()).append("\" ").append(buildIndexColumn(tableIndex));
        }
        return script.toString();
    }


    private String buildIndexColumn(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        script.append("(");
        for (TableIndexColumn column : tableIndex.getColumnList()) {
            if (StringUtils.isNotBlank(column.getColumnName())) {
                script.append("\"").append(column.getColumnName()).append("\"");
                if (!StringUtils.isBlank(column.getAscOrDesc()) && !PRIMARY_KEY.equals(this)) {
                    script.append(" ").append(column.getAscOrDesc());
                }
                script.append(",");
            }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();
    }

    private String buildIndexName(TableIndex tableIndex) {
        return "\"" + tableIndex.getSchemaName() + "\"." + "\"" + tableIndex.getName() + "\"";
    }

    public String buildModifyIndex(TableIndex tableIndex) {
        if (EditStatus.DELETE.name().equals(tableIndex.getEditStatus())) {
            return buildDropIndex(tableIndex);
        }
        if (EditStatus.MODIFY.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(buildDropIndex(tableIndex), ";\n", buildIndexScript(tableIndex));
        }
        if (EditStatus.ADD.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(buildIndexScript(tableIndex));
        }
        return "";
    }

    private String buildDropIndex(TableIndex tableIndex) {
        if (XUGUDBIndexTypeEnum.PRIMARY_KEY.getName().equals(tableIndex.getType())) {
            String tableName = "\"" + tableIndex.getSchemaName() + "\"." + "\"" + tableIndex.getTableName() + "\"";
            return StringUtils.join("ALTER TABLE ",tableName," DROP PRIMARY KEY");
        }
        StringBuilder script = new StringBuilder();
        script.append("DROP INDEX ");
        script.append(buildIndexName(tableIndex));

        return script.toString();
    }

    public static List<IndexType> getIndexTypes() {
        return Arrays.asList(XUGUDBIndexTypeEnum.values()).stream().map(XUGUDBIndexTypeEnum::getIndexType).collect(java.util.stream.Collectors.toList());
    }
}
