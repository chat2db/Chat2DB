package ai.chat2db.plugin.hive.builder;

import ai.chat2db.plugin.hive.type.HiveColumnTypeEnum;
import ai.chat2db.plugin.hive.type.HiveIndexTypeEnum;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import cn.hutool.core.util.ArrayUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class HiveSqlBuilder extends DefaultSqlBuilder implements SqlBuilder<Table> {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
        if (StringUtils.isNotBlank(table.getDatabaseName())) {
            script.append("`").append(table.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(table.getName()).append("`").append(" (").append("\n");

        // append column
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(column.getColumnType());
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            HiveIndexTypeEnum hiveIndexTypeEnum = HiveIndexTypeEnum.getByType(tableIndex.getType());
            script.append("\t").append("").append(hiveIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n)");


        if (StringUtils.isNotBlank(table.getEngine())) {
            script.append(" ENGINE=").append(table.getEngine());
        }

        if (StringUtils.isNotBlank(table.getCharset())) {
            script.append(" DEFAULT CHARACTER SET=").append(table.getCharset());
        }

        if (StringUtils.isNotBlank(table.getCollate())) {
            script.append(" COLLATE=").append(table.getCollate());
        }

        if (table.getIncrementValue() != null) {
            script.append(" AUTO_INCREMENT=").append(table.getIncrementValue());
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(" COMMENT '").append(table.getComment()).append("'");
        }

        if (StringUtils.isNotBlank(table.getPartition())) {
            script.append(" \n").append(table.getPartition());
        }
        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        boolean isModify = false;
        script.append("ALTER TABLE ");
        if (StringUtils.isNotBlank(newTable.getDatabaseName())) {
            script.append("`").append(newTable.getDatabaseName()).append("`").append(".");
        }
        script.append("`").append(oldTable.getName()).append("`").append("\n");
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("\t").append("RENAME TO ").append("`").append(newTable.getName()).append("`").append(";\n");
            isModify = true;
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            if (isModify) {
                script.append("ALTER TABLE ");
                if (StringUtils.isNotBlank(newTable.getDatabaseName())) {
                    script.append("`").append(newTable.getDatabaseName()).append("`").append(".");
                }
                script.append("`").append(newTable.getName()).append("`").append("\n");
            }
            script.append("\t").append("SET TBLPROPERTIES ('comment' = ").append("'").append(newTable.getComment()).append("'),\n");
        }

        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType()) && StringUtils.isNotBlank(tableColumn.getName())) {
                HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(tableColumn.getColumnType());
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                HiveIndexTypeEnum hiveIndexTypeEnum = HiveIndexTypeEnum.getByType(tableIndex.getType());
                script.append("\t").append(hiveIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
            }
        }

        // append reorder column
        script.append(buildGenerateReorderColumnSql(oldTable, newTable));

        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
        }

        return script.toString();
    }


    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 14);
        sqlBuilder.append(sql);
        if (offset == 0) {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(pageSize);
        } else {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(offset);
            sqlBuilder.append(",");
            sqlBuilder.append(pageSize);
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE `" + database.getName() + "`");
        if (StringUtils.isNotBlank(database.getComment())) {
            sqlBuilder.append("\r\n COMMENT '").append(database.getComment()).append("'");

        }
        return sqlBuilder.toString();
    }

    public String buildGenerateReorderColumnSql(Table oldTable, Table newTable) {
        StringBuilder sql = new StringBuilder();
        int n = 0;
        // Create a map to store the index of each column in the old table's column list
        Map<String, Integer> oldColumnIndexMap = new HashMap<>();
        for (int i = 0; i < oldTable.getColumnList().size(); i++) {
            oldColumnIndexMap.put(oldTable.getColumnList().get(i).getName(), i);
        }
        String[] oldColumnArray = oldTable.getColumnList().stream().map(TableColumn::getName).toArray(String[]::new);
        String[] newColumnArray = newTable.getColumnList().stream().map(TableColumn::getName).toArray(String[]::new);

        buildSql(oldColumnArray, newColumnArray, sql, oldTable, newTable, n);

        return sql.toString();
    }

    private String[] buildSql(String[] originalArray, String[] targetArray, StringBuilder sql, Table oldTable, Table newTable, int n) {
        // 先完成首位移动
        if (!originalArray[0].equals(targetArray[0])) {
            int a = findIndex(originalArray, targetArray[0]);
            TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
            String[] newArray = moveElement(originalArray, a, 0);
            System.out.println(ArrayUtil.toString(newArray));
            sql.append(" MODIFY COLUMN ");
            HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(column.getColumnType());
            sql.append(typeEnum.buildColumn(column));
            sql.append(" FIRST;\n");
            n++;
            if (Arrays.equals(newArray, targetArray)) {
                return newArray;
            }
            String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
            if (Arrays.equals(resultArray, targetArray)) {
                return resultArray;
            }
        }

        // 在完成最后一位移动
        int max = originalArray.length - 1;
        if (!originalArray[max].equals(targetArray[max])) {
            int a = findIndex(originalArray, targetArray[max]);
            //System.out.println("Move " + originalArray[a] + " after " + (a > 0 ? originalArray[max] : "start"));
            TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
            String[] newArray = moveElement(originalArray, a, max);
            System.out.println(ArrayUtil.toString(newArray));
            if (n > 0) {
                sql.append("ALTER TABLE ");
                if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
                    sql.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
                }
                sql.append("`").append(oldTable.getName()).append("`").append("\n");
            }
            sql.append(" MODIFY COLUMN ");
            HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(column.getColumnType());
            sql.append(typeEnum.buildColumn(column));
            sql.append(" ");
            sql.append(" AFTER ");
            sql.append(oldTable.getColumnList().get(max).getName());
            sql.append(";\n");
            n++;
            if (Arrays.equals(newArray, targetArray)) {
                return newArray;
            }
            String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
            if (Arrays.equals(resultArray, targetArray)) {
                return resultArray;
            }
        }


        for (int i = 0; i < originalArray.length; i++) {
            int a = findIndex(targetArray, originalArray[i]);
            if (i != a && isMoveValid(originalArray, targetArray, i, a)) {
                // oldTable.getColumnList中查找name为a
                int finalI = i;
                TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[finalI])).findFirst().get();
                if (n > 0) {
                    sql.append("ALTER TABLE ");
                    if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
                        sql.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
                    }
                    sql.append("`").append(oldTable.getName()).append("`").append("\n");
                }
                sql.append(" MODIFY COLUMN ");
                HiveColumnTypeEnum typeEnum = HiveColumnTypeEnum.getByType(column.getColumnType());
                sql.append(typeEnum.buildColumn(column));
                sql.append(" ");
                sql.append(" AFTER ");
                if (i < a) {
                    sql.append(originalArray[a]);
                } else {
                    sql.append(originalArray[a - 1]);
                }

                sql.append(";\n");
                n++;
                String[] newArray = moveElement(originalArray, i, a);
                if (Arrays.equals(newArray, targetArray)) {
                    return newArray;
                }
                String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
                if (Arrays.equals(resultArray, targetArray)) {
                    return resultArray;
                }
            }
        }
        return null;
    }

    private static int findIndex(String[] array, String element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(element)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isMoveValid(String[] originalArray, String[] targetArray, int i, int a) {
        System.out.println("i : " + i + " a:" + a);
        return (i == 0 || a == 0 || !originalArray[i - 1].equals(targetArray[a - 1])) &&
                (i >= originalArray.length - 1 || a >= targetArray.length - 1 || !originalArray[i + 1].equals(targetArray[a + 1]));
    }

    private static String[] moveElement(String[] originalArray, int from, int to) {
        String[] newArray = new String[originalArray.length];
        System.arraycopy(originalArray, 0, newArray, 0, originalArray.length);
        String temp = newArray[from];
        if (from < to) {
            System.arraycopy(originalArray, from + 1, newArray, from, to - from);
        } else {
            System.arraycopy(originalArray, to, newArray, to + 1, from - to);
        }
        newArray[to] = temp;
        System.out.println(ArrayUtil.toString(newArray));
        return newArray;
    }

}
