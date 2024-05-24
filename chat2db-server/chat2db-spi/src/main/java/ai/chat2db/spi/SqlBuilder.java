package ai.chat2db.spi;

import ai.chat2db.spi.model.*;

import java.util.List;

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
     * @param originSql
     * @param groupByList
     * @return
     */
    String buildGroupBySql(String originSql, List<String> groupByList);


    /**
     * generate sql based on results
     */
    String buildSqlByQuery(QueryResult queryResult);

    /**
     * DML SQL
     * @param table
     * @param type
     * @return
     */
    String getTableDmlSql(T table,String type);
}
