package ai.chat2db.spi;

import java.sql.Connection;
import java.util.List;

import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.spi.model.*;
import jakarta.validation.constraints.NotEmpty;

/**
 * Get database metadata information.
 *
 * @author jipengfei
 * @version : MetaData.java
 */
public interface MetaData {
    /**
     * Query all databases.
     *
     * @param connection
     * @return
     */
    List<Database> databases(Connection connection);

    /**
     * Querying all schemas under a database
     *
     * @param connection
     * @param databaseName
     * @return
     */
    List<Schema> schemas(Connection connection, String databaseName);

    /**
     * Querying DDL information
     *
     * @param connection
     * @param databaseName
     * @param tableName
     * @return
     */
    String tableDDL(Connection connection, @NotEmpty String databaseName, String schemaName,
                    @NotEmpty String tableName);

    /**
     * Querying all table under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    List<Table> tables(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName);

    /**
     * Querying all table name under a schema.
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    List<String> tableNames(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName);


    /**
     * Querying all table under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableNamePattern
     * @param pageNo
     * @param pageSize
     * @return
     */
    PageResult<Table> tables(Connection connection, String databaseName, String schemaName, String tableNamePattern, int pageNo, int pageSize);
    /**
     * Querying view information.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param viewName
     * @return
     */
    Table view(Connection connection, @NotEmpty String databaseName, String schemaName, String viewName);


    /** query view names
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<String> viewNames(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all views under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Table> views(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all functions under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Function> functions(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all triggers under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Trigger> triggers(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all procedures under a schema.
     *
     * @param connection
     * @param schemaName
     * @param databaseName
     * @return
     */
    List<Procedure> procedures(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all columns under a table.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    List<TableColumn> columns(Connection connection, @NotEmpty String databaseName, String schemaName,
                              @NotEmpty String tableName);

    /**
     * Querying all columns under a table.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param columnName
     * @return
     */
    List<TableColumn> columns(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName,
                              String columnName);

    /**
     * Querying all indexes under a table.
     *
     * @param connection
     * @param databaseName
     * @param databaseName
     * @return
     */
    List<TableIndex> indexes(Connection connection, @NotEmpty String databaseName, String schemaName,
                             @NotEmpty String tableName);

    /**
     * Querying function detail under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    Function function(Connection connection, @NotEmpty String databaseName, String schemaName, String functionName);

    /**
     * Querying  trigger  under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName, String triggerName);

    /**
     * Querying all procedures under a schema.
     *
     * @param connection
     * @param schemaName
     * @param databaseName
     * @param procedureName
     * @return
     */
    Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName, String procedureName);


    /**
     * @param connection
     * @return
     */
    List<Type> types(Connection connection);


    /**
     * Get sql builder.
     *
     * @return
     */
    SqlBuilder getSqlBuilder();


    /**
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    TableMeta getTableMeta(String databaseName, String schemaName, String tableName);


    /**
     * Get meta data name.
     *
     */
    String getMetaDataName(String ...names);


    /**
     * Get column builder.
     *
     */
    ValueProcessor getValueProcessor();


    /**
     * Get command executor.
     */
    CommandExecutor getCommandExecutor();

    /**
     * Get system databases.
     * @return
     */
    List<String> getSystemDatabases();

    /**
     * Get system schemas.
     * @return
     */
    List<String> getSystemSchemas();

}