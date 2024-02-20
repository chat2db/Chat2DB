package ai.chat2db.spi.jdbc;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.ValueHandler;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
        if(StringUtils.isNotBlank(databaseName) && CollectionUtils.isNotEmpty(schemas)){
            for ( Schema schema : schemas) {
                if(StringUtils.isBlank(schema.getDatabaseName())){
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
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, new String[]{"TABLE","SYSTEM TABLE"});
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
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = SQLExecutor.getInstance().functions(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);
        if(CollectionUtils.isEmpty(functions)){
            return functions;
        }
        return functions.stream().filter(function -> StringUtils.isNotBlank(function.getFunctionName())).collect(Collectors.toList());
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        return null;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures =  SQLExecutor.getInstance().procedures(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);

        if(CollectionUtils.isEmpty(procedures)){
            return procedures;
        }
        return procedures.stream().filter(function -> StringUtils.isNotBlank(function.getProcedureName())).collect(Collectors.toList());
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, null);
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
    public ValueHandler getValueHandler() {
        return new DefaultValueHandler();
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return SQLExecutor.getInstance();
    }
}