package ai.chat2db.plugin.mysql.builder;

import ai.chat2db.plugin.mysql.type.MysqlColumnTypeEnum;
import ai.chat2db.plugin.mysql.type.MysqlIndexTypeEnum;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.model.TableIndex;
import ai.chat2db.spi.util.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class MysqlSqlBuilder extends DefaultSqlBuilder {
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
            MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
            if (typeEnum == null) {
                continue;
            }
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            MysqlIndexTypeEnum mysqlIndexTypeEnum = MysqlIndexTypeEnum.getByType(tableIndex.getType());
            if (mysqlIndexTypeEnum == null) {
                continue;
            }
            script.append("\t").append("").append(mysqlIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
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
            script.append(" COMMENT='").append(table.getComment()).append("'");
        }

        if (StringUtils.isNotBlank(table.getPartition())) {
            script.append(" \n").append(table.getPartition());
        }
        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("ALTER TABLE ");
        if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
            tableBuilder.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
        }
        tableBuilder.append("`").append(oldTable.getName()).append("`").append("\n");

        StringBuilder script = new StringBuilder();
        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("\t").append("RENAME TO ").append("`").append(newTable.getName()).append("`").append(",\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("\t").append("COMMENT=").append("'").append(newTable.getComment()).append("'").append(",\n");
        }
        if (oldTable.getIncrementValue() != newTable.getIncrementValue()) {
            script.append("\t").append("AUTO_INCREMENT=").append(newTable.getIncrementValue()).append(",\n");
        }

        // 判断新增字段
        List<TableColumn> addColumnList = new ArrayList<>();
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (tableColumn.getEditStatus() != null ? tableColumn.getEditStatus().equals("ADD") : false) {
                addColumnList.add(tableColumn);
            }
        }

        // 判断移动的字段
        List<TableColumn> moveColumnList = movedElements(oldTable.getColumnList(), newTable.getColumnList());

        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if ((StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType())
                    && StringUtils.isNotBlank(tableColumn.getName())) || moveColumnList.contains(tableColumn) || addColumnList.contains(tableColumn)) {
                MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(tableColumn.getColumnType());
                if (typeEnum == null) {
                    continue;
                }
                if (moveColumnList.contains(tableColumn) || addColumnList.contains(tableColumn)) {
                    script.append("\t").append(typeEnum.buildModifyColumn(tableColumn, true, findPrevious(tableColumn, newTable))).append(",\n");
                } else {
                    script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(",\n");
                }
            }
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                MysqlIndexTypeEnum mysqlIndexTypeEnum = MysqlIndexTypeEnum.getByType(tableIndex.getType());
                if (mysqlIndexTypeEnum == null) {
                    continue;
                }
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
            }
        }

        // append reorder column
        // script.append(buildGenerateReorderColumnSql(oldTable, newTable));

        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
            return tableBuilder.append(script).toString();
        } else {
            return StringUtils.EMPTY;
        }

    }

    private String findPrevious(TableColumn tableColumn, Table newTable) {
        int index = newTable.getColumnList().indexOf(tableColumn);
        if (index == 0) {
            return "-1";
        }
        // Find the previous column that is not deleted
        for (int i = index - 1; i >= 0; i--) {
            if (newTable.getColumnList().get(i).getEditStatus() == null || !newTable.getColumnList().get(i).getEditStatus().equals(EditStatus.DELETE.name())) {
                return newTable.getColumnList().get(i).getName();
            }
        }
        return "-1";
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
        if (StringUtils.isNotBlank(database.getCharset())) {
            sqlBuilder.append(" DEFAULT CHARACTER SET=").append(database.getCharset());
        }
        if (StringUtils.isNotBlank(database.getCollation())) {
            sqlBuilder.append(" COLLATE=").append(database.getCollation());
        }
        return sqlBuilder.toString();
    }

    public static List<TableColumn> movedElements(List<TableColumn> original, List<TableColumn> modified) {
        int[][] dp = new int[original.size() + 1][modified.size() + 1];

        // 构建DP表
        for (int i = 1; i <= original.size(); i++) {
            for (int j = 1; j <= modified.size(); j++) {
                if (original.get(i - 1).getName().equals(modified.get(j - 1).getOldName())) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // 追踪LCS，找出移动了位置的元素
        List<TableColumn> moved = new ArrayList<>();
        int i = original.size();
        int j = modified.size();
        while (i > 0 && j > 0) {
            if (original.get(i - 1).equals(modified.get(j - 1))) {
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                moved.add(original.get(i - 1));
                // modified List中找到original.get(i-1)的位置
//                System.out.println("Moved elements:"+ original.get(i-1).getName() + " after " + modified.indexOf(original.get(i-1)) );
                i--;
            } else {
                j--;
            }
        }

        // 这里添加原始列表中未被包含在LCS中的元素
        while (i > 0) {
            moved.add(original.get(i - 1));
            i--;
        }

        return moved;
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

        Set<String> oldColumnSet = new HashSet<>(Arrays.asList(oldColumnArray));
        Set<String> newColumnSet = new HashSet<>(Arrays.asList(newColumnArray));
        if (!oldColumnSet.equals(newColumnSet)) {
            return "";
        }

        buildSql(oldColumnArray, newColumnArray, sql, oldTable, newTable, n);

        return sql.toString();
    }

    private String[] buildSql(String[] originalArray, String[] targetArray, StringBuilder sql, Table oldTable, Table newTable, int n) {
        // Complete the first move first
        if (!originalArray[0].equals(targetArray[0])) {
            int a = findIndex(originalArray, targetArray[0]);
            TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
            String[] newArray = moveElement(originalArray, a, 0, targetArray, new AtomicInteger(0));
            sql.append(" MODIFY COLUMN ");
            MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
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

        // After completing the last move
        int max = originalArray.length - 1;
        if (!originalArray[max].equals(targetArray[max])) {
            int a = findIndex(originalArray, targetArray[max]);
            //System.out.println("Move " + originalArray[a] + " after " + (a > 0 ? originalArray[max] : "start"));
            TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
            String[] newArray = moveElement(originalArray, a, max, targetArray, new AtomicInteger(0));
            if (n > 0) {
                sql.append("ALTER TABLE ");
                if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
                    sql.append("`").append(oldTable.getDatabaseName()).append("`").append(".");
                }
                sql.append("`").append(oldTable.getName()).append("`").append("\n");
            }
            sql.append(" MODIFY COLUMN ");
            MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
            sql.append(typeEnum.buildColumn(column));
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
                // Find name a in oldTable.getColumnList
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
                MysqlColumnTypeEnum typeEnum = MysqlColumnTypeEnum.getByType(column.getColumnType());
                sql.append(typeEnum.buildColumn(column));
                sql.append(" AFTER ");
                AtomicInteger continuousDataCount = new AtomicInteger(0);
                String[] newArray = moveElement(originalArray, i, a, targetArray, continuousDataCount);
                if (i < a) {
                    sql.append(originalArray[a + continuousDataCount.get()]);
                } else {
                    sql.append(originalArray[a - 1]);
                }

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
        return ((i == 0 || a == 0 || !originalArray[i - 1].equals(targetArray[a - 1])) &&
                (i >= originalArray.length - 1 || a >= targetArray.length - 1 || !originalArray[i + 1].equals(targetArray[a + 1])))
                || (i > 0 && a > 0 && !originalArray[i - 1].equals(targetArray[a - 1]));
    }

    private static String[] moveElement(String[] originalArray, int from, int to, String[] targetArray, AtomicInteger continuousDataCount) {
        String[] newArray = new String[originalArray.length];
        System.arraycopy(originalArray, 0, newArray, 0, originalArray.length);
        String temp = newArray[from];
        // 是否有连续移动数据
        boolean isContinuousData = false;
        // 连续数据数量
        if (from < to) {
            for (int i = to; i < originalArray.length - 1; i++) {
                if (originalArray[i + 1].equals(targetArray[findIndex(targetArray, originalArray[i]) + 1])) {
                    continuousDataCount.set(continuousDataCount.incrementAndGet());
                } else {
                    break;
                }
            }
            if (continuousDataCount.get() > 0) {
                System.arraycopy(originalArray, from + 1, newArray, from, to - from + 1);
                isContinuousData = true;
            } else {
                System.arraycopy(originalArray, from + 1, newArray, from, to - from);
            }
        } else {
            System.arraycopy(originalArray, to, newArray, to + 1, from - to);
        }
        if (isContinuousData) {
            newArray[to + continuousDataCount.get()] = temp;
        } else {
            newArray[to] = temp;
        }
        return newArray;
    }


    @Override
    protected void buildTableName(String databaseName, String schemaName, String tableName, StringBuilder script) {
        if (StringUtils.isNotBlank(databaseName)) {
            script.append(SqlUtils.quoteObjectName(databaseName, "`")).append('.');
        }
        script.append(SqlUtils.quoteObjectName(tableName, "`"));
    }

    /**
     * @param columnList
     * @param script
     */
    @Override
    protected void buildColumns(List<String> columnList, StringBuilder script) {
        if (CollectionUtils.isNotEmpty(columnList)) {
            script.append(" (")
                    .append(columnList.stream().map(s -> SqlUtils.quoteObjectName(s, "`")).collect(Collectors.joining(",")))
                    .append(") ");
        }
    }

}
