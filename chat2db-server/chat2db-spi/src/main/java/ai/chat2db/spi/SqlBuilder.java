package ai.chat2db.spi;

import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.OrderBy;
import ai.chat2db.spi.model.QueryResult;
import ai.chat2db.spi.model.Schema;

import java.util.List;
import java.util.Map;

public interface SqlBuilder<T> {

    /**
     * Generate create table sql
     *
     * @param table
     * @return
     */
    String buildCreateTableSql(T table);


    /**
     * Generate modify table sql
     *
     * @param newTable
     * @param oldTable
     * @return
     */
    String buildModifyTaleSql(T oldTable, T newTable);


    /**
     * Generate page limit sql
     *
     * @param sql
     * @param offset
     * @param pageNo
     * @param pageSize
     * @return
     */
    String pageLimit(String sql, int offset, int pageNo, int pageSize);


    /**
     * Generate create database sql
     *
     * @param database
     * @return
     */
    String buildCreateDatabaseSql(Database database);


    /**
     * @param oldDatabase
     * @param newDatabase
     * @return
     */
    String buildModifyDatabaseSql(Database oldDatabase, Database newDatabase);


    /**
     * @param schemaName
     * @return
     */
    String buildCreateSchemaSql(Schema schemaName);


    /**
     * @param oldSchemaName
     * @param newSchemaName
     * @return
     */
    String buildModifySchemaSql(String oldSchemaName, String newSchemaName);

    /**
     * @param originSql
     * @param orderByList
     * @return
     */
    String buildOrderBySql(String originSql, List<OrderBy> orderByList);


    /**
     * generate sql based on results
     */
    String buildSqlByQuery(QueryResult queryResult);

    /**
     * DML SQL
     *
     * @param table
     * @param type
     * @return
     */
    String getTableDmlSql(T table, String type);

    /**
     *
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    String buildTableQuerySql(String databaseName, String schemaName, String tableName);



    String buildSingleInsertSql(String databaseName, String schemaName, String tableName, List<String> columnList, List<String> valueList);

    String buildMultiInsertSql(String databaseName, String schemaName, String tableName, List<String> columnList, List<List<String>> valueLists);

    String buildUpdateSql(String databaseName, String schemaName, String tableName, Map<String, String> row,Map<String,String> primaryKeyMap);
}
