package ai.chat2db.plugin.h2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

import ai.chat2db.plugin.h2.builder.H2SqlBuilder;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.jdbc.DefaultSqlBuilder;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class H2Meta extends DefaultMetaService implements MetaData {


    private List<String> systemSchemas = Arrays.asList("INFORMATION_SCHEMA");
    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }
    @Override
    public String tableDDL(Connection connection, @NotEmpty String databaseName, String schemaName,
        @NotEmpty String tableName) {
        return getDDL(connection, databaseName, schemaName, tableName);
    }

    private String getDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        try {
            // Query table structure information
            ResultSet columns = connection.getMetaData().getColumns(databaseName, schemaName, tableName, null);
            List<String> columnDefinitions = new ArrayList<>();
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                String remarks = columns.getString("REMARKS");
                String defaultValue = columns.getString("COLUMN_DEF");
                String nullable = columns.getInt("NULLABLE") == ResultSetMetaData.columnNullable ? "NULL" : "NOT NULL";
                StringBuilder columnDefinition = new StringBuilder();
                columnDefinition.append(columnName).append(" ").append(columnType);
                if (columnSize != 0) {
                    columnDefinition.append("(").append(columnSize).append(")");
                }
                columnDefinition.append(" ").append(nullable);
                if (defaultValue != null) {
                    columnDefinition.append(" DEFAULT ").append(defaultValue);
                }
                if (remarks != null) {
                    columnDefinition.append(" COMMENT '").append(remarks).append("'");
                }
                columnDefinitions.add(columnDefinition.toString());
            }

            // Query table index information
            ResultSet indexes = connection.getMetaData().getIndexInfo(databaseName, schemaName, tableName, false,
                false);
            Map<String, List<String>> indexMap = new HashMap<>();
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                String columnName = indexes.getString("COLUMN_NAME");
                if (indexName != null) {
                    if (!indexMap.containsKey(indexName)) {
                        indexMap.put(indexName, new ArrayList<>());
                    }
                    indexMap.get(indexName).add(columnName);
                }
            }
            StringBuilder createTableDDL = new StringBuilder("CREATE TABLE ");
            createTableDDL.append(tableName).append(" (\n");
            createTableDDL.append(String.join(",\n", columnDefinitions));
            createTableDDL.append("\n);\n");
            // Output index information
            for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
                String indexName = entry.getKey();
                List<String> columnList = entry.getValue();
                String indexColumns = String.join(", ", columnList);
                String createIndexDDL = String.format("CREATE INDEX %s ON %s (%s);", indexName, tableName,
                    indexColumns);
                createTableDDL.append(createIndexDDL);
            }
            return createTableDDL.toString();

        } catch (Exception e) {
            log.error("Failed to get table DDL", e);
        }
        return "";
    }

    private static String ROUTINES_SQL
        =
        "SELECT SPECIFIC_NAME, ROUTINE_DEFINITION FROM information_schema.routines WHERE "
            + "routine_type = '%s' AND ROUTINE_SCHEMA ='%s'  AND "
            + "routine_name = '%s';";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
        String functionName) {

        String sql = String.format(ROUTINES_SQL, "FUNCTION", databaseName, functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                function.setFunctionBody(resultSet.getString("ROUTINE_DEFINITION"));
            }

            return function;
        });

    }

    private static String TRIGGER_SQL
        = "SELECT TRIGGER_NAME,JAVA_CLASS  FROM INFORMATION_SCHEMA.TRIGGERS where "
        + "TRIGGER_SCHEMA = '%s' AND TRIGGER_NAME = '%s';";

    private static String TRIGGER_SQL_LIST
        = "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS where TRIGGER_CATALOG = '%s' AND TRIGGER_SCHEMA = '%s';";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, databaseName,schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("TRIGGER_NAME"));
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

        String sql = String.format(TRIGGER_SQL, databaseName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("JAVA_CLASS"));
            }
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
        String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", databaseName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            if (resultSet.next()) {
                procedure.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                procedure.setProcedureBody(resultSet.getString("ROUTINE_DEFINITION"));
            }
            return procedure;
        });
    }

    private static String VIEW_SQL
        = "SELECT VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_CATALOG = '%s' AND TABLE_SCHEMA = '%s' "
        + "AND TABLE_NAME = '%s';";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, databaseName, schemaName, viewName);
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
    public SqlBuilder getSqlBuilder() {
        return new H2SqlBuilder();
    }


    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }

    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }
}
