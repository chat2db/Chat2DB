package ai.chat2db.plugin.sqlserver;

import ai.chat2db.plugin.sqlserver.builder.SqlServerSqlBuilder;
import ai.chat2db.plugin.sqlserver.template.SQLTemplate;
import ai.chat2db.plugin.sqlserver.type.SqlServerColumnTypeEnum;
import ai.chat2db.plugin.sqlserver.type.SqlServerDefaultValueEnum;
import ai.chat2db.plugin.sqlserver.type.SqlServerIndexTypeEnum;
import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import jakarta.validation.constraints.NotEmpty;
import net.sf.jsqlparser.statement.ReferentialAction;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class SqlServerMetaData extends DefaultMetaService implements MetaData {


    private List<String> systemDatabases = Arrays.asList("master", "model", "msdb", "tempdb");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = SQLExecutor.getInstance().databases(connection);
        return sortDatabase(databases, systemDatabases, connection);
    }

    public static final String PK_UQ_CONSTRAINT_SQL = """
                                                      SELECT kc.name              AS CONSTRAINT_NAME,
                                                             c.name               AS COLUMN_NAME,
                                                             ic.is_descending_key AS IS_DESC,
                                                             kc.type              AS CONSTRAINT_TYPE,
                                                             i.type_desc          AS INDEX_TYPE
                                                      FROM sys.key_constraints kc
                                                               INNER JOIN sys.index_columns ic ON kc.parent_object_id = ic.object_id AND kc.unique_index_id = ic.index_id
                                                               INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                                                               INNER JOIN sys.indexes i ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                                                      WHERE kc.type IN ('PK', 'UQ')
                                                        AND kc.parent_object_id = OBJECT_ID('%s.%s');""";

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private static final String TABLE_COMMENT_SQL = """
                                                    SELECT
                                                        t.name AS TABLE_NAME,
                                                        p.value AS TABLE_COMMENT
                                                    FROM
                                                        sys.tables t
                                                    JOIN
                                                        sys.extended_properties p ON t.object_id = p.major_id
                                                    WHERE
                                                        p.minor_id = 0 AND p.class = 1 AND p.name = 'MS_Description'
                                                        AND t.name = '%s'
                                                        AND SCHEMA_NAME(t.schema_id) = '%s';""";
    private static final String SELECT_CONSTRAINT_COMMENT_SQL = """
                                                                SELECT ep.value AS 'CONSTRAINT_COMMENT',
                                                                       o.name   AS  'CONSTRAINT_NAME'
                                                                FROM sys.extended_properties AS ep
                                                                         JOIN
                                                                     sys.objects AS o ON ep.major_id = o.object_id
                                                                         JOIN
                                                                     sys.tables AS t ON o.parent_object_id = t.object_id
                                                                         JOIN
                                                                     sys.schemas AS s ON t.schema_id = s.schema_id
                                                                WHERE o.type in ('C', 'F', 'PK', 'UQ')
                                                                  AND s.name = '%s'
                                                                  AND t.name = '%s'
                                                                  AND ep.name = N'MS_Description';""";
    private static final String CHECK_CONSTRAINT_SQL = """
                                                       select
                                                       name       as CONSTRAINT_NAME,
                                                       definition as CONSTRAINT_DEFINITION
                                                       from sys.check_constraints
                                                       where parent_object_id = object_id('%s.%s');""";
    private static final String FOREIGN_KEY_SQL = """
                                                  SELECT
                                                      fk.name AS CONSTRAINT_NAME,
                                                      c.name AS COLUMN_NAME,
                                                      SCHEMA_NAME(ro.schema_id) + '.' + OBJECT_NAME(fk.referenced_object_id) AS REFERENCED_TABLE_NAME,
                                                      rc.name AS REFERENCED_COLUMN_NAME,
                                                      fk.delete_referential_action                                           as DELETE_ACTION,
                                                      fk.update_referential_action                                           as UPDATE_ACTION
                                                  FROM
                                                      sys.foreign_keys AS fk
                                                  INNER JOIN
                                                      sys.objects o ON fk.parent_object_id = o.object_id
                                                  INNER JOIN
                                                      sys.objects ro ON fk.referenced_object_id = ro.object_id
                                                  INNER JOIN
                                                      sys.foreign_key_columns AS fkc ON fk.object_id = fkc.constraint_object_id
                                                  INNER JOIN
                                                      sys.columns AS c ON fkc.parent_column_id = c.column_id AND fkc.parent_object_id = c.object_id
                                                  INNER JOIN
                                                      sys.columns AS rc ON fkc.referenced_column_id = rc.column_id AND fkc.referenced_object_id = rc.object_id
                                                  WHERE
                                                      SCHEMA_NAME(o.schema_id) = '%s'
                                                      and OBJECT_NAME(fk.parent_object_id) = '%s';""";
    private static final String PARTITION_DEF_SQL = """
                                                    SELECT
                                                        c.name AS PARTITION_COLUMN_NAME,
                                                        ps.name AS PARTITION_SCHEME_NAME
                                                    FROM
                                                        sys.tables AS t
                                                        JOIN sys.indexes AS i ON t.object_id = i.object_id
                                                        JOIN sys.index_columns AS ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                                                        JOIN sys.columns AS c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                                                        JOIN sys.partition_schemes AS ps ON i.data_space_id = ps.data_space_id
                                                        JOIN sys.partition_functions AS pf ON ps.function_id = pf.function_id
                                                    WHERE
                                                        t.schema_id = SCHEMA_ID('%s') AND
                                                         t.name = '%s' AND
                                                        ic.partition_ordinal > 0  -- partition_ordinal > 0 表示这个列是分区键""";
    private static final String SELECT_TABLE_COLUMNS = """
                                                       SELECT c.name            as COLUMN_NAME,
                                                               c.is_sparse      as IS_SPARSE,
                                                               c.is_nullable    as IS_NULLABLE,
                                                               c.column_id      as ORDINAL_POSITION,
                                                               c.max_length     as COLUMN_SIZE,
                                                               c.precision      as COLUMN_PRECISION,
                                                               c.scale          as NUMERIC_SCALE,
                                                               c.collation_name as COLLATION_NAME,
                                                               ty.name          as DATA_TYPE,
                                                               t.name,
                                                               def.definition   as COLUMN_DEFAULT,
                                                               ep.value         as COLUMN_COMMENT,
                                                               ident.seed_value      as SEED_VALUE,
                                                               ident.increment_value as INCREMENT_VALUE,
                                                               cc.definition    as COMPUTED_DEFINITION,
                                                               c.is_identity         as IS_IDENTITY,
                                                               cc.is_persisted  as IS_PERSISTED
                                                        from sys.columns c
                                                                 LEFT JOIN sys.tables t on c.object_id = t.object_id
                                                                 LEFT JOIN sys.types ty ON c.user_type_id = ty.user_type_id
                                                                 LEFT JOIN sys.default_constraints def ON c.default_object_id = def.object_id
                                                                 LEFT JOIN sys.extended_properties ep
                                                                           ON t.object_id = ep.major_id AND c.column_id = ep.minor_id and class_desc != 'INDEX'
                                                                 LEFT JOIN sys.computed_columns cc on cc.object_id = c.object_id and cc.column_id = c.column_id
                                                                 LEFT JOIN sys.identity_columns ident ON c.object_id = ident.object_id AND c.column_id = ident.column_id
                                                        WHERE t.name = '%s'  and t.schema_id = SCHEMA_ID('%s');""";
    private static final String INDEX_SQL = """
                                            SELECT ic.key_ordinal       AS COLUMN_POSITION,
                                                   ic.is_descending_key as DESCEND,
                                                   ind.name             AS INDEX_NAME,
                                                   ind.is_unique        AS IS_UNIQUE,
                                                   col.name             AS COLUMN_NAME,
                                                   ind.type_desc        AS INDEX_TYPE,
                                                   ind.is_primary_key   AS IS_PRIMARY,
                                                   ep.value             AS INDEX_COMMENT,
                                                   ind.is_unique_constraint AS IS_UNIQUE_CONSTRAINT
                                            FROM sys.indexes ind
                                                     INNER JOIN sys.index_columns ic
                                                                ON ind.object_id = ic.object_id and ind.index_id = ic.index_id and ic.key_ordinal > 0
                                                     INNER JOIN sys.columns col ON ic.object_id = col.object_id and ic.column_id = col.column_id
                                                     INNER JOIN sys.tables t ON ind.object_id = t.object_id
                                                     LEFT JOIN sys.key_constraints kc ON ind.object_id = kc.parent_object_id AND ind.index_id = kc.unique_index_id
                                                     LEFT JOIN sys.extended_properties ep ON ind.object_id = ep.major_id AND ind.index_id = ep.minor_id and ep.class_desc !='OBJECT_OR_COLUMN'
                                            WHERE t.name = '%s'
                                              and t.schema_id = SCHEMA_ID('%s')
                                            ORDER BY t.name, ind.name, ind.index_id, ic.index_column_id""";
    private List<String> systemSchemas = Arrays.asList("guest", "INFORMATION_SCHEMA", "sys", "db_owner",
                                                       "db_accessadmin", "db_securityadmin", "db_ddladmin", "db_backupoperator", "db_datareader", "db_datawriter",
                                                       "db_denydatareader", "db_denydatawriter");

    private String format(String objectName) {
        return "[" + objectName + "]";

    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        StringBuilder ddlBuilder = new StringBuilder(500);
        StringBuilder tempBuilder = new StringBuilder(100);
        List<String> tempList = new ArrayList<>();
        String formatSchemaName = format(schemaName);
        String formatTableName = format(tableName);
        ddlBuilder.append("CREATE TABLE").append(" ").append(formatSchemaName).append(".").append(formatTableName).append("\n");
        ddlBuilder.append("(\n");
        //build column
        List<TableColumn> tableColumnList = SQLExecutor.getInstance().execute(connection, String.format(SELECT_TABLE_COLUMNS, tableName, schemaName), resultSet -> {
            List<TableColumn> columns = new ArrayList<>();
            while (resultSet.next()) {
                TableColumn tableColumn = new TableColumn();
                tableColumn.setSchemaName(schemaName);
                tableColumn.setTableName(tableName);
                tableColumn.setName(resultSet.getString("COLUMN_NAME"));
                String computedDefinition = resultSet.getString("COMPUTED_DEFINITION");
                boolean isPersisted = resultSet.getBoolean("IS_PERSISTED");
                String dataType = resultSet.getString("DATA_TYPE").toUpperCase();
                boolean isIdentity = resultSet.getBoolean("IS_IDENTITY");
                int seedValue = resultSet.getInt("SEED_VALUE");
                int incrementValue = resultSet.getInt("INCREMENT_VALUE");
                //计算列
                if (StringUtils.isNotBlank(computedDefinition)) {
                    dataType = "AS " + computedDefinition;
                    if (isPersisted) {
                        dataType += " PERSISTED";
                    }
                    //自增列
                } else if (isIdentity) {
                    dataType += " identity";
                    if (seedValue != 1 && incrementValue != 1) {
                        dataType += " (" + seedValue + "," + incrementValue + ")";
                    }
                }
                tableColumn.setColumnType(dataType);
                tableColumn.setSparse(resultSet.getBoolean("IS_SPARSE"));
                tableColumn.setDefaultValue(resultSet.getString("COLUMN_DEFAULT"));
                tableColumn.setNullable(resultSet.getInt("IS_NULLABLE"));
//                tableColumn.setCollationName(resultSet.getString("COLLATION_NAME"));
                tableColumn.setComment(resultSet.getString("COLUMN_COMMENT"));
                configureColumnSize(resultSet, tableColumn);
                columns.add(tableColumn);
                SqlServerColumnTypeEnum typeEnum = SqlServerColumnTypeEnum.getByType(tableColumn.getColumnType());
                if (typeEnum == SqlServerColumnTypeEnum.OTHER) {
                    tempBuilder.append("\t").append(format(tableColumn.getName())).append(" ").append(tableColumn.getColumnType());
                } else {
                    tempBuilder.append("\t").append(typeEnum.buildCreateColumnSql(tableColumn));
                }
                tempList.add(tempBuilder.toString());
                tempBuilder.setLength(0);
            }
            ddlBuilder.append(String.join(",\n", tempList));
            tempList.clear();
            return columns;
        });
        //build PK constraint and UQ constraint
        Set<String> PKUQConstraintNameSet = SQLExecutor.getInstance().execute(connection, String.format(PK_UQ_CONSTRAINT_SQL, schemaName, tableName), resultSet -> {
            Map<String, List<String>> PKConstraintsMap = new HashMap<>(1);
            Map<String, List<String>> UQConstraintsMap = new HashMap<>(3);
            HashMap<String, String> clusteredMap = new HashMap<>(4);
            while (resultSet.next()) {
                String constraintName = resultSet.getString("CONSTRAINT_NAME");
                String columnName = resultSet.getString("COLUMN_NAME");
                boolean isDesc = resultSet.getBoolean("IS_DESC");
                String constraintType = resultSet.getString("CONSTRAINT_TYPE");
                String indexType = resultSet.getString("INDEX_TYPE");
                if (StringUtils.isNotBlank(indexType)) {
                    clusteredMap.computeIfAbsent(constraintName, k -> indexType);
                }
                // Append ASC or DESC based on IS_DESC
                if (isDesc) {
                    columnName += " desc";
                } else {
                    columnName += " asc";
                }

                // Determine which map to use based on CONSTRAINT_TYPE
                if ("PK".equals(constraintType)) {
                    // Primary Key constraint
                    PKConstraintsMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(columnName);
                } else if ("UQ".equals(constraintType)) {
                    // Unique constraint
                    UQConstraintsMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(columnName);
                }
            }
            if (MapUtils.isNotEmpty(PKConstraintsMap) || MapUtils.isNotEmpty(UQConstraintsMap)) {
                ddlBuilder.append(",\n");
                if (MapUtils.isNotEmpty(PKConstraintsMap)) {
                    PKConstraintsMap.forEach((key, value) -> {
                        tempBuilder.append("constraint ")
                                .append(key)
                                .append("\n")
                                .append("primary key ");
                        if (clusteredMap.containsKey(key)) {
                            tempBuilder.append(" ").append(clusteredMap.get(key).toLowerCase()).append(" ");
                        }
                        tempBuilder.append("(")
                                .append(String.join(" , ", value))
                                .append(")");
                        tempList.add(tempBuilder.toString());
                        tempBuilder.setLength(0);
                    });
                }
                if (MapUtils.isNotEmpty(UQConstraintsMap)) {
                    UQConstraintsMap.forEach((key, value) -> {
                        tempBuilder.append("constraint ")
                                .append(key)
                                .append("\n")
                                .append("unique ");
                        if (clusteredMap.containsKey(key)) {
                            tempBuilder.append(" ").append(clusteredMap.get(key).toLowerCase()).append(" ");
                        }
                        tempBuilder.append("(")
                                .append(String.join(" , ", value))
                                .append(")");
                        tempList.add(tempBuilder.toString());
                        tempBuilder.setLength(0);
                    });
                }
                ddlBuilder.append(String.join(",\n", tempList));
                tempList.clear();
            }
            Set<String> combinedKeySet = new HashSet<>();
            combinedKeySet.addAll(PKConstraintsMap.keySet());
            combinedKeySet.addAll(UQConstraintsMap.keySet());
            clusteredMap.clear();
            PKConstraintsMap.clear();
            UQConstraintsMap.clear();
            return combinedKeySet;
        });

        //build CHECK constraint
        SQLExecutor.getInstance().execute(connection, String.format(CHECK_CONSTRAINT_SQL, schemaName, tableName), resultSet -> {
            boolean isFirst = true;
            while (resultSet.next()) {

                String constraintName = resultSet.getString("CONSTRAINT_NAME");
                String constraintDefinition = resultSet.getString("CONSTRAINT_DEFINITION");
                if (StringUtils.isBlank(constraintDefinition)) {
                    continue;
                }
                if (isFirst) {
                    // Assuming ddlBuilder is used for constructing DDL statements
                    ddlBuilder.append(",\n");
                    isFirst = false;
                }
                tempBuilder.append("constraint ").append(constraintName).append("\n")
                        .append("check ").append(constraintDefinition);
                tempList.add(tempBuilder.toString());
                tempBuilder.setLength(0);
            }
            if (CollectionUtils.isNotEmpty(tempList)) {
                ddlBuilder.append(String.join(",\n", tempList));
                tempList.clear();
            }
        });

        SQLExecutor.getInstance().execute(connection, String.format(FOREIGN_KEY_SQL, schemaName, tableName), resultSet -> {
            HashMap<String, String> foreignMap = new HashMap<>();
            HashMap<String, List<String>> columnMap = new HashMap<>();
            HashMap<String, List<String>> referencedColumnMap = new HashMap<>();
            HashMap<String, List<String>> actionMap = new HashMap<>();
            while (resultSet.next()) {
                String constraintName = resultSet.getString("CONSTRAINT_NAME");
                String referencedTableName = resultSet.getString("REFERENCED_TABLE_NAME");
                foreignMap.computeIfAbsent(constraintName, k -> referencedTableName);
                String columnName = resultSet.getString("COLUMN_NAME");
                columnMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(columnName);
                String referencedColumnName = resultSet.getString("REFERENCED_COLUMN_NAME");
                referencedColumnMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(referencedColumnName);
                int updateAction = resultSet.getInt("UPDATE_ACTION");
                if (updateAction != 0) {
                    actionMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(buildReferentialAction(updateAction));
                }
                int deleteAction = resultSet.getInt("DELETE_ACTION");
                if (updateAction != 0) {
                    actionMap.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(buildReferentialAction(deleteAction));
                }
            }
            if (MapUtils.isNotEmpty(foreignMap)) {
                ddlBuilder.append(",\n");
                foreignMap.forEach((key, value) -> {
                    tempBuilder.append("constraint ").append(key).append("\n")
                            .append("foreign key (")
                            .append(String.join(" , ", columnMap.get(key)))
                            .append(")\n")
                            .append("references ")
                            .append(value)
                            .append(" (")
                            .append(String.join(" , ", referencedColumnMap.get(key)))
                            .append(")");
                    if (actionMap.containsKey(key)) {
                        for (int i = 0; i < actionMap.get(key).size(); i++) {
                            if (i == 0) {
                                tempBuilder.append(" on update ").append(actionMap.get(key).get(i));
                            } else if (i == 1) {
                                tempBuilder.append(" on delete ").append(actionMap.get(key).get(i));
                            } else {
                                break;
                            }
                        }
                    }
                    tempList.add(tempBuilder.toString());
                    tempBuilder.setLength(0);
                });
                ddlBuilder.append(String.join(",\n", tempList));
                tempList.clear();
                foreignMap.clear();
                columnMap.clear();
                referencedColumnMap.clear();
                actionMap.clear();
                foreignMap.clear();
            }

        });
        ddlBuilder.append("\n)\n");
        SQLExecutor.getInstance().execute(connection, String.format(PARTITION_DEF_SQL, schemaName, tableName), resultSet -> {
            if (resultSet.next()) {
                String partitionColumnName = resultSet.getString("PARTITION_COLUMN_NAME");
                String partitionSchemeName = resultSet.getString("PARTITION_SCHEME_NAME");
                if (StringUtils.isNotBlank(partitionSchemeName) && StringUtils.isNotBlank(partitionColumnName)) {
                    ddlBuilder.append(" on ")
                            .append(format(partitionSchemeName))
                            .append(" (")
                            .append(format(partitionColumnName))
                            .append(") ");
                }
            }
        });
        ddlBuilder.append("\ngo\n");


        List<TableIndex> indexList = SQLExecutor.getInstance().execute(connection, String.format(INDEX_SQL, tableName, schemaName), resultSet -> {
            HashMap<String, TableIndex> indexMap = new HashMap<>();
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                String columnName = resultSet.getString("COLUMN_NAME");
                String indexType = resultSet.getString("INDEX_TYPE");
                String ascOrDesc = resultSet.getBoolean("DESCEND") ? "DESC" : "ASC";
                String indexComment = resultSet.getString("INDEX_COMMENT");
                boolean isUnique = resultSet.getBoolean("IS_UNIQUE");
                //primary key constraint or unique constraint
                if (CollectionUtils.isNotEmpty(PKUQConstraintNameSet) && PKUQConstraintNameSet.contains(indexName)) {
                    continue;
                }
                TableIndex index = indexMap.get(indexName);
                if (Objects.isNull(index)) {
                    index = new TableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    index.setColumnList(new ArrayList<>());
                    boolean isNonClustered = Objects.equals(SqlServerIndexTypeEnum.NONCLUSTERED.name(), indexType);
                    if (isUnique) {
                        if (isNonClustered) {
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_NONCLUSTERED.getName());
                        } else {
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_CLUSTERED.getName());
                        }
                    } else {
                        index.setType(indexType);
                    }
                    index.setComment(indexComment);
                    indexMap.put(indexName, index);
                }
                TableIndexColumn tableIndexColumn = new TableIndexColumn();
                tableIndexColumn.setTableName(tableName);
                tableIndexColumn.setSchemaName(schemaName);
                tableIndexColumn.setColumnName(columnName);
                tableIndexColumn.setAscOrDesc(ascOrDesc);
                index.getColumnList().add(tableIndexColumn);
            }
            return new ArrayList<>(indexMap.values());
        });
        SQLExecutor.getInstance().execute(connection, String.format(TABLE_COMMENT_SQL, tableName, schemaName), resultSet -> {
            if (resultSet.next()) {
                String comment = resultSet.getString("TABLE_COMMENT");
                if (StringUtils.isNotBlank(comment)) {
                    ddlBuilder.append(SQLTemplate.buildTableComment(comment, schemaName, tableName));
                }
            }
        });

        for (TableColumn tableColumn : tableColumnList) {
            String comment = tableColumn.getComment();
            if (StringUtils.isNotBlank(comment)) {
                ddlBuilder.append(SQLTemplate.buildColumnComment(comment, schemaName, tableName, tableColumn.getName()));
            }
        }
        SQLExecutor.getInstance().execute(connection, String.format(SELECT_CONSTRAINT_COMMENT_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                String constraintName = resultSet.getString("CONSTRAINT_NAME");
                String comment = resultSet.getString("CONSTRAINT_COMMENT");
                if (StringUtils.isNotBlank(comment)) {
                    ddlBuilder.append("\t").append(SQLTemplate.buildConstraintComment(comment, schemaName, tableName, constraintName));
                }
            }
        });
        if (CollectionUtils.isNotEmpty(indexList)) {
            indexList.forEach(index -> {
                String type = index.getType();
                SqlServerIndexTypeEnum sqlServerIndexTypeEnum = SqlServerIndexTypeEnum.getByType(type);
                if (Objects.nonNull(sqlServerIndexTypeEnum)) {
                    ddlBuilder.append("\n").append(sqlServerIndexTypeEnum.buildIndexScript(index));
                    String comment = index.getComment();
                    if (StringUtils.isNotBlank(comment)) {
                        ddlBuilder.append("\t").append(SQLTemplate.buildIndexComment(comment, schemaName, tableName, index.getName()));
                    }
                }
            });
        }

        return ddlBuilder.toString();
    }

    private static String SELECT_TABLES_SQL = "SELECT t.name AS TableName, mm.value as comment FROM sys.tables t LEFT JOIN(SELECT * from sys.extended_properties ep where ep.minor_id = 0 AND ep.name = 'MS_Description') mm ON t.object_id = mm.major_id WHERE t.schema_id= SCHEMA_ID('%s')";

    private String buildReferentialAction(int actionCode) {
        switch (actionCode) {
            case 1 -> {
                return ReferentialAction.Action.CASCADE.toString().toLowerCase();
            }
            case 2 -> {
                return ReferentialAction.Action.SET_NULL.toString().toLowerCase();
            }
            case 3 -> {
                return ReferentialAction.Action.SET_DEFAULT.toString().toLowerCase();
            }
            default -> {
                return ReferentialAction.Action.NO_ACTION.toString().toLowerCase();
            }
        }

    }

    private void configureColumnSize(ResultSet columns, TableColumn tableColumn) throws SQLException {
        if (Arrays.asList(SqlServerColumnTypeEnum.FLOAT.name(),
                          SqlServerColumnTypeEnum.REAL.name())
                .contains(tableColumn.getColumnType())) {
            return;
        }
        int columnSize = columns.getInt("COLUMN_SIZE");
        int numericScale = columns.getInt("NUMERIC_SCALE");
        int columnPrecision = columns.getInt("COLUMN_PRECISION");
        // Adjust column size for Unicode types
        if (Arrays.asList(SqlServerColumnTypeEnum.NCHAR.name(),
                          SqlServerColumnTypeEnum.NVARCHAR.name())
                .contains(tableColumn.getColumnType())) {
            //default size
            if (columnSize == 2) {
                return;
            }
            //max size
            if (columnSize == -1) {
                tableColumn.setColumnSize(columnSize);
                return;
            }
            columnSize = columnSize / 2;
            tableColumn.setColumnSize(columnSize);
            return;
        }
        // Set column size based on data type
        if (Arrays.asList(SqlServerColumnTypeEnum.DATETIMEOFFSET.name(),
                          SqlServerColumnTypeEnum.TIME.name(), SqlServerColumnTypeEnum.DATETIME2.name())
                .contains(tableColumn.getColumnType())) {
            //default scale
            if (numericScale == 7) {
                return;
            }
            tableColumn.setColumnSize(numericScale);
            return;
        } else if (Arrays.asList(SqlServerColumnTypeEnum.DECIMAL.name(),
                                 SqlServerColumnTypeEnum.NUMERIC.name())
                .contains(tableColumn.getColumnType())) {
            tableColumn.setColumnSize(columnPrecision);
        } else {
            if (columnSize != 1) {
                tableColumn.setColumnSize(columnSize);
            }

        }
        tableColumn.setDecimalDigits(numericScale);
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS, tableName, schemaName);
        List<TableColumn> tableColumns = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setSchemaName(schemaName);
                column.setOldName(resultSet.getString("COLUMN_NAME"));
                column.setName(resultSet.getString("COLUMN_NAME"));
                //column.setColumnType(resultSet.getString("COLUMN_TYPE"));
                column.setColumnType(resultSet.getString("DATA_TYPE").toUpperCase());
                //column.setDataType(resultSet.getInt("DATA_TYPE"));
                column.setDefaultValue(resultSet.getString("COLUMN_DEFAULT"));
                //column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("COLUMN_COMMENT"));
                // column.setPrimaryKey("PRI".equalsIgnoreCase(resultSet.getString("COLUMN_KEY")));
                column.setNullable(resultSet.getInt("IS_NULLABLE"));
                column.setOrdinalPosition(resultSet.getInt("ORDINAL_POSITION"));
                column.setDecimalDigits(resultSet.getInt("NUMERIC_SCALE"));
                // column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
                column.setCollationName(resultSet.getString("COLLATION_NAME"));
                column.setColumnSize(resultSet.getInt("COLUMN_SIZE"));
                //setColumnSize(column, resultSet.getString("COLUMN_TYPE"));
                tableColumns.add(column);
            }
            return tableColumns;
        });
    }

    private static String ROUTINES_SQL
            = "SELECT type_desc, OBJECT_NAME(object_id) AS FunctionName, OBJECT_DEFINITION(object_id) AS "
            + "definition FROM sys.objects WHERE type_desc IN(%s) and name = '%s' ;";


    private static String OBJECT_SQL
            = "SELECT name FROM sys.objects WHERE type = '%s' and SCHEMA_ID = SCHEMA_ID('%s') order by name;";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "'SQL_SCALAR_FUNCTION', 'SQL_TABLE_VALUED_FUNCTION'", functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("definition"));
            }
            return function;
        });
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = new ArrayList<>();
        String sql = String.format(OBJECT_SQL, "FN", schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Function function = new Function();
                function.setDatabaseName(databaseName);
                function.setSchemaName(schemaName);
                function.setFunctionName(resultSet.getString("name"));
                functions.add(function);
            }
            return functions;
        });
    }

    private Function removeVersion(Function function) {
        String fullFunctionName = function.getFunctionName();
        if (!StringUtils.isEmpty(fullFunctionName)) {
            String[] parts = fullFunctionName.split(";");
            String functionName = parts[0];
            function.setFunctionName(functionName);
        }
        return function;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = new ArrayList<>();
        String sql = String.format(OBJECT_SQL, "P", schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setDatabaseName(databaseName);
                procedure.setSchemaName(schemaName);
                procedure.setProcedureName(resultSet.getString("name"));
                procedures.add(procedure);
            }
            return procedures;
        });
    }

    private Procedure removeVersion(Procedure procedure) {
        String fullProcedureName = procedure.getProcedureName();
        if (!StringUtils.isEmpty(fullProcedureName)) {
            String[] parts = fullProcedureName.split(";");
            String procedureName = parts[0];
            procedure.setProcedureName(procedureName);
        }
        return procedure;
    }

    private static String TRIGGER_SQL
            = "SELECT OBJECT_NAME(parent_obj) AS TableName, name AS triggerName, OBJECT_DEFINITION(id) AS "
            + "triggerDefinition, CASE WHEN status & 1 = 1 THEN 'Enabled' ELSE 'Disabled' END AS Status FROM sysobjects "
            + "WHERE xtype = 'TR' and name = '%s';";

    private static String TRIGGER_SQL_LIST
            = "SELECT OBJECT_NAME(parent_obj) AS TableName, name AS triggerName, OBJECT_DEFINITION(id) AS "
            + "triggerDefinition, CASE WHEN status & 1 = 1 THEN 'Enabled' ELSE 'Disabled' END AS Status FROM sysobjects "
            + "WHERE xtype = 'TR' order by name";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, TRIGGER_SQL_LIST, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("triggerName"));
                trigger.setSchemaName(schemaName);
                trigger.setDatabaseName(databaseName);
                triggers.add(trigger);
            }
            return triggers;
        });
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("triggerDefinition"));
            }
            return trigger;
        });
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Table> tables = new ArrayList<>();
        String sql = String.format(SELECT_TABLES_SQL, schemaName);
        if (StringUtils.isNotBlank(tableName)) {
            sql += " AND t.name = '" + tableName + "'";
        } else {
            sql += " ORDER BY t.name";
        }

        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Table table = new Table();
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(resultSet.getString("TableName"));
                table.setComment(resultSet.getString("comment"));
                tables.add(table);
            }
            return tables;
        });
    }

    private static String VIEW_SQL
            = "SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = '%s' "
            + "AND TABLE_NAME = '%s';";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("VIEW_DEFINITION"));
            }
            return table;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "'SQL_STORED_PROCEDURE'", procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                                                     Procedure procedure = new Procedure();
                                                     procedure.setDatabaseName(databaseName);
                                                     procedure.setSchemaName(schemaName);
                                                     procedure.setProcedureName(procedureName);
                                                     if (resultSet.next()) {
                                                         procedure.setProcedureBody(resultSet.getString("definition"));
                                                     }
                                                     return procedure;
                                                 }
        );
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(INDEX_SQL, tableName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("INDEX_NAME");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    columnList.add(getTableIndexColumn(resultSet));
                    columnList = columnList.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition))
                            .collect(Collectors.toList());
                    tableIndex.setColumnList(columnList);
                } else {
                    TableIndex index = new TableIndex();
                    index.setDatabaseName(databaseName);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(keyName);
                    int isunique = resultSet.getInt("IS_UNIQUE");
                    if (isunique == 1) {
                        index.setUnique(true);
                    } else {
                        index.setUnique(false);
                    }
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String indexType = resultSet.getString("INDEX_TYPE");
                    if (resultSet.getBoolean("IS_PRIMARY")) {
                        index.setType(SqlServerIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if ("CLUSTERED".equalsIgnoreCase(indexType)) {
                        if (index.getUnique()) {
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_CLUSTERED.getName());
                        } else {
                            index.setType(SqlServerIndexTypeEnum.CLUSTERED.getName());
                        }
                    } else if ("NONCLUSTERED".equalsIgnoreCase(indexType)) {
                        if (index.getUnique()) {
                            index.setType(SqlServerIndexTypeEnum.UNIQUE_NONCLUSTERED.getName());
                        } else {
                            index.setType(SqlServerIndexTypeEnum.NONCLUSTERED.getName());
                        }
                    } else {
                        index.setType(indexType);
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLUMN_NAME"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("COLUMN_POSITION"));
        int collation = resultSet.getInt("DESCEND");
        if (collation == 1) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }


    @Override
    public SqlBuilder getSqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(SqlServerColumnTypeEnum.getTypes())
                .charsets(null)
                .collations(null)
                .indexTypes(SqlServerIndexTypeEnum.getIndexTypes())
                .defaultValues(SqlServerDefaultValueEnum.getDefaultValues())
                .build();
    }


    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "[" + name + "]").collect(Collectors.joining("."));
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return new SqlServerCommandExecutor();
    }

    @Override
    public List<String> getSystemDatabases() {
        return systemDatabases;
    }

    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }

}
