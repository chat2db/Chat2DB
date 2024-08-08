package ai.chat2db.plugin.kingbase.builder;

import ai.chat2db.plugin.kingbase.type.KingBaseColumnTypeEnum;
import ai.chat2db.plugin.kingbase.type.KingBaseIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class KingBaseSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
        script.append("\"").append(table.getName()).append("\"").append(" (").append(" ").append("\n");
        // append column
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(column.getColumnType());
            if(typeEnum ==null){
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }
        Map<Boolean, List<TableIndex>> tableIndexMap = table.getIndexList().stream()
                .collect(Collectors.partitioningBy(v -> KingBaseIndexTypeEnum.NORMAL.getName().equals(v.getType())));
        // append constraint key
        List<TableIndex> constraintList = tableIndexMap.get(Boolean.FALSE);
        if (CollectionUtils.isNotEmpty(constraintList)) {
            for (TableIndex index : constraintList) {
                if (StringUtils.isBlank(index.getName()) || StringUtils.isBlank(index.getType())) {
                    continue;
                }
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(index.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                script.append("\t").append("").append(indexTypeEnum.buildIndexScript(index));
                script.append(",\n");
            }

        }
        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)");
        if(StringUtils.isNotBlank(table.getTablespace())){
            script.append(" TABLESPACE \"").append(table.getTablespace()).append("\";");
        }else {
            script.append(" TABLESPACE \"SYS_DEFAULT\";");
        }
        // append index
        List<TableIndex> tableIndexList = tableIndexMap.get(Boolean.TRUE);
        for (TableIndex tableIndex : tableIndexList) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            script.append("\n");
            KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
            if(indexTypeEnum == null){
                continue;
            }
            script.append("").append(indexTypeEnum.buildIndexScript(tableIndex)).append(";");
        }

        // append comment
        if (StringUtils.isNotBlank(table.getComment())) {
            script.append("\n");
            script.append("COMMENT ON TABLE").append(" ").append("\"").append(table.getName()).append("\" IS '")
                    .append(table.getComment()).append("';\n");
        }
        List<TableColumn> tableColumnList = table.getColumnList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableColumn tableColumn : tableColumnList) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append(typeEnum.buildComment(tableColumn, typeEnum)).append("\n");
            ;
        }
        List<TableIndex> indexList = table.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableIndex index : indexList) {
            KingBaseIndexTypeEnum indexEnum = KingBaseIndexTypeEnum.getByType(index.getType());
            if(indexEnum == null){
                continue;
            }
            script.append(indexEnum.buildIndexComment(index)).append("\n");
        }

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("ALTER TABLE ").append("\"").append(oldTable.getName()).append("\"");
            script.append("\t").append("RENAME TO ").append("\"").append(newTable.getName()).append("\"").append(";\n");

        }
        newTable.setColumnList(newTable.getColumnList().stream().filter(v -> StringUtils.isNotBlank(v.getEditStatus())).toList());
        newTable.setIndexList(newTable.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getEditStatus())).toList());

        //update name
        List<TableColumn> columnNameList = newTable.getColumnList().stream().filter(v ->
                v.getOldName() != null && !StringUtils.equals(v.getOldName(), v.getName())).toList();
        for (TableColumn tableColumn : columnNameList) {
            script.append("ALTER TABLE ").append("\"").append(newTable.getName()).append("\" ").append("RENAME COLUMN \"")
                    .append(tableColumn.getOldName()).append("\" TO \"").append(tableColumn.getName()).append("\";\n");
        }

        Map<Boolean, List<TableIndex>> tableIndexMap = newTable.getIndexList().stream()
                .collect(Collectors.partitioningBy(v -> KingBaseIndexTypeEnum.NORMAL.getName().equals(v.getType())));
        StringBuilder scriptModify = new StringBuilder();
        Boolean modify = false;
        scriptModify.append("ALTER TABLE ").append("\"").append(newTable.getName()).append("\" \n");
        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            scriptModify.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
            modify = true;

        }

        // append modify constraint
        for (TableIndex tableIndex : tableIndexMap.get(Boolean.FALSE)) {
            if (StringUtils.isNotBlank(tableIndex.getType())) {
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                scriptModify.append("\t").append(indexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
                modify = true;
            }
        }

        if (BooleanUtils.isTrue(modify)) {
            script.append(scriptModify);
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";\n");
        }

        // append modify index
        for (TableIndex tableIndex : tableIndexMap.get(Boolean.TRUE)) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                KingBaseIndexTypeEnum indexTypeEnum = KingBaseIndexTypeEnum.getByType(tableIndex.getType());
                if(indexTypeEnum == null){
                    continue;
                }
                script.append(indexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
            }
        }

        // append comment
        if (!StringUtils.equals(oldTable.getComment(), newTable.getComment())) {
            script.append("\n");
            script.append("COMMENT ON TABLE").append(" ").append("\"").append(newTable.getName()).append("\" IS '")
                    .append(newTable.getComment()).append("';\n");
        }
        for (TableColumn tableColumn : newTable.getColumnList()) {
            KingBaseColumnTypeEnum typeEnum = KingBaseColumnTypeEnum.getByType(tableColumn.getColumnType());
            if(typeEnum == null){
                continue;
            }
            script.append(typeEnum.buildComment(tableColumn, typeEnum)).append("\n");
            ;
        }
        List<TableIndex> indexList = newTable.getIndexList().stream().filter(v -> StringUtils.isNotBlank(v.getComment())).toList();
        for (TableIndex index : indexList) {
            KingBaseIndexTypeEnum indexEnum = KingBaseIndexTypeEnum.getByType(index.getType());
            if(indexEnum == null){
                continue;
            }
            script.append(indexEnum.buildIndexComment(index)).append("\n");
        }

        return script.toString();
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlStr = new StringBuilder(sql.length() + 17);
        sqlStr.append(sql);
        if (offset == 0) {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
        } else {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
            sqlStr.append(" OFFSET ");
            sqlStr.append(offset);
        }
        return sqlStr.toString();
    }

    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE "+database.getName());
        String owner = database.getOwner();
        if (StringUtils.isBlank(owner)) {
            owner = "SYSTEM";
        }
        sqlBuilder.append(" WITH  OWNER = \"").append(owner).append("\"");
        if (StringUtils.isNotBlank(database.getCharset())) {
            sqlBuilder.append(" ENCODING  ").append(database.getCharset()).append("");
        }
        sqlBuilder.append(";\n");

        if (StringUtils.isNotBlank(database.getComment())) {
            sqlBuilder.append("COMMENT ON DATABASE ").append(database.getName()).append(" IS '").append(database.getComment()).append("';");
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateSchemaSql(Schema schema){
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE SCHEMA "+schema.getName()+"");
        String owner = schema.getOwner();
        if(StringUtils.isBlank(schema.getOwner())){
            owner = "SYSTEM";
        }
        sqlBuilder.append(" AUTHORIZATION \"").append(owner).append("\"");
        return sqlBuilder.toString();
    }

}
