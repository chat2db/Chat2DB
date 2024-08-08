package ai.chat2db.spi.jdbc;

import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.spi.*;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SqlUtils;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jipengfei
 * @version : DefaultMetaService.java
 */
public class DefaultMetaService implements MetaData {
    @Override
    public List<Database> databases(Connection connection) {
        return SQLExecutor.getInstance().databases(connection);
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        if (StringUtils.isNotBlank(databaseName) && CollectionUtils.isNotEmpty(schemas)) {
            for (Schema schema : schemas) {
                if (StringUtils.isBlank(schema.getDatabaseName())) {
                    schema.setDatabaseName(databaseName);
                }
            }
        }
        return schemas;
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, new String[]{"TABLE", "SYSTEM TABLE"});
    }

    @Override
    public List<String> tableNames(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().tableNames(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, new String[]{"TABLE", "SYSTEM TABLE"});
    }

    @Override
    public PageResult<Table> tables(Connection connection, String databaseName, String schemaName, String tableNamePattern, int pageNo, int pageSize) {
        List<Table> tables = tables(connection, databaseName, schemaName, tableNamePattern);
        if (CollectionUtils.isEmpty(tables)) {
            return PageResult.of(tables, 0L, pageNo, pageSize);
        }
        List result = tables.stream().skip((pageNo - 1) * pageSize).limit(pageSize).collect(Collectors.toList());
        return PageResult.of(result, (long) tables.size(), pageNo, pageSize);
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        return null;
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, null, new String[]{"VIEW"});
    }

    @Override
    public List<String> viewNames(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().tableNames(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, null, new String[]{"VIEW"});
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = SQLExecutor.getInstance().functions(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);
        if (CollectionUtils.isEmpty(functions)) {
            return functions;
        }
        return functions.stream().filter(function -> StringUtils.isNotBlank(function.getFunctionName())).map(function -> {
            String functionName = function.getFunctionName();
            function.setFunctionName(functionName.trim());
            return function;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        return null;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = SQLExecutor.getInstance().procedures(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);

        if (CollectionUtils.isEmpty(procedures)) {
            return procedures;
        }
        return procedures.stream().filter(function -> StringUtils.isNotBlank(function.getProcedureName())).map(procedure -> {
            String procedureName = procedure.getProcedureName();
            procedure.setProcedureName(procedureName.trim());
            return procedure;
        }).collect(Collectors.toList());
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableColumn> columns = SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, null);
        if (CollectionUtils.isNotEmpty(columns)) {
            for (TableColumn column : columns) {
                String columnType = SqlUtils.removeDigits(column.getColumnType());
                column.setColumnType(columnType);
            }
        }
        return columns;
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName,
                                     String columnName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, columnName);
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().indexes(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName);
    }

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public Trigger trigger(Connection connection, String databaseName, String schemaName, String triggerName) {
        return null;
    }

    @Override
    public Procedure procedure(Connection connection, String databaseName, String schemaName, String procedureName) {
        return null;
    }

    @Override
    public List<Type> types(Connection connection) {
        return SQLExecutor.getInstance().types(connection);
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DefaultSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).collect(Collectors.joining("."));
    }

    @Override
    public ValueProcessor getValueProcessor() {
        return new DefaultValueProcessor();
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return SQLExecutor.getInstance();
    }

    @Override
    public List<String> getSystemDatabases() {
        return Lists.newArrayList();
    }

    @Override
    public List<String> getSystemSchemas() {
        return Lists.newArrayList();
    }
}