package ai.chat2db.plugin.postgresql;

import ai.chat2db.plugin.postgresql.builder.PostgreSQLSqlBuilder;
import ai.chat2db.plugin.postgresql.type.*;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.SortUtils;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static ai.chat2db.spi.util.SortUtils.sortDatabase;

public class PostgreSQLMetaData extends DefaultMetaService implements MetaData {

    private static final String SELECT_KEY_INDEX = "SELECT ccu.table_schema AS Foreign_schema_name, ccu.table_name AS Foreign_table_name, ccu.column_name AS Foreign_column_name, constraint_type AS Constraint_type, tc.CONSTRAINT_NAME AS Key_name, tc.TABLE_NAME, kcu.Column_name, tc.is_deferrable, tc.initially_deferred FROM information_schema.table_constraints AS tc JOIN information_schema.key_column_usage AS kcu ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name WHERE tc.TABLE_SCHEMA = '%s'  AND tc.TABLE_NAME = '%s';";


    private List<String> systemDatabases = Arrays.asList("postgres");

    @Override
    public List<Database> databases(Connection connection) {
        List<Database> list = SQLExecutor.getInstance().execute(connection, "SELECT datname FROM pg_database;", resultSet -> {
            List<Database> databases = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    String dbName = resultSet.getString("datname");
                    if ("template0".equals(dbName) || "template1".equals(dbName)) {
                        continue;
                    }
                    Database database = new Database();
                    database.setName(dbName);
                    databases.add(database);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return databases;
        });
        return sortDatabase(list, systemDatabases, connection);
    }

    private List<String> systemSchemas = Arrays.asList("pg_toast", "pg_temp_1", "pg_toast_temp_1", "pg_catalog", "information_schema");

/*    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().execute(connection,
                                                                 "SELECT catalog_name, schema_name FROM information_schema.schemata;", resultSet -> {
                    List<Schema> databases = new ArrayList<>();
                    while (resultSet.next()) {
                        Schema schema = new Schema();
                        String name = resultSet.getString("schema_name");
                        String catalogName = resultSet.getString("catalog_name");
                        schema.setName(name);
                        schema.setDatabaseName(catalogName);
                        databases.add(schema);
                    }
                    return databases;
                });
        return SortUtils.sortSchema(schemas, systemSchemas);
    }*/


    private static final String SELECT_TABLE_INDEX = "SELECT tmp.INDISPRIMARY AS Index_primary, tmp.TABLE_SCHEM, tmp.TABLE_NAME, tmp.NON_UNIQUE, tmp.INDEX_QUALIFIER, tmp.INDEX_NAME AS Key_name, tmp.indisclustered, tmp.ORDINAL_POSITION AS Seq_in_index, TRIM ( BOTH '\"' FROM pg_get_indexdef ( tmp.CI_OID, tmp.ORDINAL_POSITION, FALSE ) ) AS Column_name,CASE  tmp.AM_NAME   WHEN 'btree' THEN CASE   tmp.I_INDOPTION [ tmp.ORDINAL_POSITION - 1 ] & 1 :: SMALLINT   WHEN 1 THEN  'D' ELSE'A'  END ELSE NULL  END AS Collation, tmp.CARDINALITY, tmp.PAGES, tmp.FILTER_CONDITION , tmp.AM_NAME AS Index_method, tmp.DESCRIPTION AS Index_comment FROM ( SELECT  n.nspname AS TABLE_SCHEM,  ct.relname AS TABLE_NAME,  NOT i.indisunique AS NON_UNIQUE, NULL AS INDEX_QUALIFIER,  ci.relname AS INDEX_NAME,i.INDISPRIMARY , i.indisclustered ,  ( information_schema._pg_expandarray ( i.indkey ) ).n AS ORDINAL_POSITION,  ci.reltuples AS CARDINALITY,   ci.relpages AS PAGES,  pg_get_expr ( i.indpred, i.indrelid ) AS FILTER_CONDITION,  ci.OID AS CI_OID, i.indoption AS I_INDOPTION,  am.amname AS AM_NAME , d.description  FROM   pg_class ct   JOIN pg_namespace n ON ( ct.relnamespace = n.OID )   JOIN pg_index i ON ( ct.OID = i.indrelid )   JOIN pg_class ci ON ( ci.OID = i.indexrelid )  JOIN pg_am am ON ( ci.relam = am.OID )      left outer join pg_description d on i.indexrelid = d.objoid  WHERE  n.nspname = '%s'   AND ct.relname = '%s'   ) AS tmp ;";
    private static String ROUTINES_SQL = "SELECT p.proname, p.prokind, pg_catalog.pg_get_functiondef(p.oid) as \"code\" FROM pg_catalog.pg_proc p where p.prokind = '%s' and p.proname='%s'";
    private static String TRIGGER_SQL
            = "SELECT n.nspname AS \"schema\", c.relname AS \"table_name\", t.tgname AS \"trigger_name\", t.tgenabled AS "
            + "\"enabled\", pg_get_triggerdef(t.oid) AS \"trigger_body\" FROM pg_trigger t JOIN pg_class c ON c.oid = t"
            + ".tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = '%s' AND t.tgname ='%s';";
    private static String TRIGGER_SQL_LIST
            = "SELECT n.nspname AS \"schema\", c.relname AS \"table_name\", t.tgname AS \"trigger_name\", t.tgenabled AS "
            + "\"enabled\", pg_get_triggerdef(t.oid) AS \"trigger_body\" FROM pg_trigger t JOIN pg_class c ON c.oid = t"
            + ".tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = '%s';";
    private static String VIEW_SQL
            = "SELECT schemaname, viewname, definition FROM pg_views WHERE schemaname = '%s' AND viewname = '%s';";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("trigger_name"));
                trigger.setSchemaName(schemaName);
                trigger.setDatabaseName(databaseName);
                triggers.add(trigger);
            }
            return triggers;
        });
    }

    private static final String TABLE_SPACE_SQL = """
                                                  select tablespace
                                                  from pg_tables
                                                  where schemaname = ?
                                                    and tablename = ?
                                                    and tablespace is not null;""";

    private static final String PARTITIONED_CONDITION_SQL = """
                                                            SELECT pg_get_partkeydef(c1.oid) as partition_key
                                                            FROM pg_class c1
                                                                     JOIN pg_namespace n ON (n.oid = c1.relnamespace)
                                                                     LEFT JOIN pg_partitioned_table p ON (c1.oid = p.partrelid)
                                                            WHERE n.nspname = ?
                                                              and n.oid = c1.relnamespace
                                                              and c1.relname = ?
                                                              and c1.relkind = 'p'
                                                              and pg_get_partkeydef(c1.oid) IS NOT NULL
                                                              and pg_get_partkeydef(c1.oid) <> '';""";

    private static final String PARTITIONED_SUB_TABLE_SQL = """
                                                             SELECT
                                                             quote_ident(c2.relname) as PARENT_TABLE,
                                                             pg_get_expr(c1.relpartbound, c1.oid, true) as PARTITION_DEFINITION
                                                            from pg_class c1,
                                                                 pg_namespace n,
                                                                 pg_inherits i,
                                                                 pg_class c2
                                                            WHERE n.nspname = ?
                                                              and n.oid = c1.relnamespace
                                                              and c1.relname = ?
                                                              and c1.oid = i.inhrelid
                                                              and i.inhparent = c2.oid
                                                              and c1.relkind = 'r';""";

    private static final String LIST_PARTITIONED_SUB_TABLE_SQL = """
                                                                 WITH PartitionTables AS (
                                                                     SELECT
                                                                         child_ns.nspname AS child_schema,
                                                                         child.relname AS child_table
                                                                     FROM
                                                                         pg_inherits
                                                                     JOIN
                                                                         pg_class parent ON pg_inherits.inhparent = parent.oid
                                                                     JOIN
                                                                         pg_namespace ns ON parent.relnamespace = ns.oid
                                                                     JOIN
                                                                         pg_class child ON pg_inherits.inhrelid = child.oid
                                                                     JOIN
                                                                         pg_namespace child_ns ON child.relnamespace = child_ns.oid
                                                                     WHERE
                                                                         ns.nspname = ?
                                                                         AND parent.relname = ?
                                                                 )
                                                                 SELECT
                                                                     quote_ident(c2.relname) as parent_table,
                                                                     pg_get_expr(c1.relpartbound, c1.oid, true) as partition_definition,
                                                                     quote_ident(c1.relname)                    as sub_name,
                                                                     quote_ident(n.nspname)                     as schema_name
                                                                 FROM
                                                                     pg_class c1
                                                                 JOIN
                                                                     pg_namespace n ON n.oid = c1.relnamespace
                                                                 JOIN
                                                                     pg_inherits i ON c1.oid = i.inhrelid
                                                                 JOIN
                                                                     pg_class c2 ON i.inhparent = c2.oid
                                                                 JOIN
                                                                     PartitionTables pt ON pt.child_schema = n.nspname AND pt.child_table = c1.relname
                                                                 WHERE
                                                                     c1.relkind = 'r';""";

    private static final String CONSTRAINT_SQL = """
                                                 SELECT con.conname                   as CONSTRAINT_NAME,
                                                        con.contype                   as CONSTRAINT_TYPE,
                                                        CASE
                                                            WHEN con.contype = 'p' THEN 1 -- primary key constraint
                                                            WHEN con.contype = 'u' THEN 2 -- unique constraint
                                                            WHEN con.contype = 'f' THEN 3 -- foreign key constraint
                                                            WHEN con.contype = 'c' THEN 4
                                                            ELSE 5
                                                            END                       as type_rank,
                                                        pg_get_constraintdef(con.oid) as CONSTRAINT_DEFINITION
                                                 FROM pg_catalog.pg_constraint con
                                                          JOIN pg_catalog.pg_class rel ON rel.oid = con.conrelid
                                                          JOIN pg_catalog.pg_namespace nsp ON nsp.oid = connamespace
                                                 WHERE nsp.nspname = ?
                                                   AND rel.relname = ?
                                                   AND con.conparentid = 0
                                                 ORDER BY type_rank;""";
    private static final String INDEX_SQL = """
                                            SELECT INDEXDEF, INDEXNAME
                                            FROM pg_indexes
                                            WHERE (schemaname, tablename) = (?, ?)""";

    private static final String TABLE_COLUMN_COMMENT_SQL = """
                                                           SELECT quote_ident(c.relname)       AS table_name,
                                                                  CASE
                                                                      WHEN c.relkind IN ('r', 'p') and a.attname is not null THEN 'COLUMN'
                                                                      WHEN c.relkind IN ('r', 'p') THEN 'TABLE'
                                                                      END                      AS object_type,
                                                                  quote_literal(d.description) AS comment,
                                                                  quote_ident(n.nspname)       AS schema_name,
                                                                  CASE
                                                                      WHEN a.attname IS NOT NULL THEN quote_ident(a.attname)
                                                                      END                      AS column_name
                                                           FROM pg_class c
                                                                    JOIN
                                                                pg_namespace n ON (n.oid = c.relnamespace)
                                                                    LEFT JOIN
                                                                pg_description d ON (c.oid = d.objoid)
                                                                    LEFT JOIN
                                                                pg_attribute a ON (c.oid = a.attrelid AND a.attnum > 0 AND a.attnum = d.objsubid)
                                                           WHERE d.description IS NOT NULL
                                                             AND d.description <> ''
                                                             AND n.nspname = ?
                                                             AND c.relname = ?
                                                           ORDER BY 2 desc;""";

    private static final String COLUMN_SQL = """                                         
                                             SELECT quote_ident(c.column_name) as column_name ,
                                                    c.data_type,
                                                    c.udt_name,
                                                    quote_ident(c.udt_schema) as udt_schema,
                                                    c.character_maximum_length,
                                                    c.is_nullable,
                                                    c.column_default,
                                                    c.numeric_precision,
                                                    c.numeric_scale,
                                                    c.datetime_precision,
                                                    c.is_identity,
                                                    c.identity_start,
                                                    c.identity_increment,
                                                    c.identity_maximum,
                                                    c.identity_minimum,
                                                    c.identity_cycle,
                                                    c.identity_generation,
                                                    c.is_generated,
                                                    c.generation_expression,
                                                    c.identity_increment
                                             FROM information_schema.columns c
                                             WHERE (table_schema, table_name) = (?, ?)
                                             ORDER BY ordinal_position;""";


    private static final String TABLE_INDEX_COMMENT_SQL = """
                                                          SELECT quote_ident(n.nspname)                           as schema_name,
                                                                 quote_ident(t.relname)                           AS table_name,
                                                                 quote_ident(i.relname)                           AS index_name,
                                                                 quote_literal(pg_catalog.obj_description(i.oid)) AS index_comment,
                                                                 i.oid
                                                          FROM pg_class t
                                                                   INNER JOIN pg_index idx ON t.oid = idx.indrelid
                                                                   INNER JOIN pg_class i ON i.oid = idx.indexrelid
                                                                   INNER JOIN pg_catalog.pg_namespace n ON i.relnamespace = n.oid
                                                          WHERE n.nspname = ?
                                                            AND t.relname = ?
                                                            AND pg_catalog.obj_description(i.oid) IS NOT NULL
                                                            AND pg_catalog.obj_description(i.oid) <> '';""";

    private static final String TABLE_SEQUENCES_COMMENT_SQL = """
                                                              SELECT
                                                                     quote_ident(seq.relname)                           AS sequence_name,
                                                                     quote_literal(pg_catalog.obj_description(seq.oid)) AS sequence_comment
                                                              FROM pg_catalog.pg_class seq
                                                                       JOIN
                                                                   pg_catalog.pg_namespace seq_ns ON seq.relnamespace = seq_ns.oid
                                                                       JOIN
                                                                   pg_catalog.pg_depend dep ON dep.objid = seq.oid
                                                                       JOIN
                                                                   pg_catalog.pg_class tbl ON dep.refobjid = tbl.oid
                                                                       JOIN
                                                                   pg_catalog.pg_namespace tbl_ns ON tbl.relnamespace = tbl_ns.oid
                                                              WHERE seq.relkind = 'S'
                                                                AND seq_ns.nspname = ?
                                                                AND tbl_ns.nspname = ?
                                                                AND tbl.relname = ?
                                                                AND pg_catalog.obj_description(seq.oid) is not null
                                                                AND pg_catalog.obj_description(seq.oid) <> '';""";

    private static final String NORMAL_SUB_TABLE_SQL = """
                                                       -- 获取继承关系信息，包括父表和子表的模式、名称及OID
                                                       WITH inheritance_info AS (
                                                           SELECT p_ns.nspname AS parent_schema,
                                                                  p.relname    AS parent_table,
                                                                  c_ns.nspname AS child_schema,
                                                                  c.relname    AS child_table,
                                                                  p.oid        AS parent_oid,
                                                                  c.oid        AS child_oid
                                                           FROM pg_inherits
                                                                    JOIN pg_class p ON pg_inherits.inhparent = p.oid
                                                                    JOIN pg_class c ON pg_inherits.inhrelid = c.oid
                                                                    JOIN pg_namespace p_ns ON p.relnamespace = p_ns.oid
                                                                    JOIN pg_namespace c_ns ON c.relnamespace = c_ns.oid
                                                           WHERE c_ns.nspname = ? -- 替换为实际的子表模式名
                                                             AND c.relname = ? -- 替换为实际的子表名
                                                       ),
                                                       -- 获取子表中不包含在父表中的字段
                                                            unique_child_columns AS (
                                                                SELECT att.attname AS child_column,
                                                                       ii.child_table,
                                                                       ii.parent_table,
                                                                       ii.parent_schema
                                                                FROM pg_attribute att
                                                                         JOIN pg_class cls ON att.attrelid = cls.oid
                                                                         JOIN inheritance_info ii ON cls.oid = ii.child_oid
                                                                         LEFT JOIN pg_attribute p_att ON att.attname = p_att.attname
                                                                    AND p_att.attrelid = ii.parent_oid
                                                                WHERE att.attnum > 0
                                                                  AND NOT att.attisdropped
                                                                  AND p_att.attname IS NULL -- 排除父表中已有的字段
                                                            )
                                                       -- 返回子表自定义字段名、子表名、父表名及父表模式名
                                                       SELECT
                                                              quote_ident(child_column) as child_column,
                                                              quote_ident(parent_table) as parent_table,
                                                              quote_ident(parent_schema) as parent_schema
                                                       FROM unique_child_columns
                                                       where parent_table is not null
                                                         and parent_table <> ''
                                                       ORDER BY child_table, child_column; -- 按子表名和字段名排序""";

    private static final String TABLE_OPTION_SQL = """
                                                   select reloptions as table_options
                                                   from pg_class c
                                                            join pg_namespace n on c.relnamespace = n.oid
                                                   where nspname = ?
                                                     and relname = ?;""";

    private String format(String objectName) {
        int singleQuoteCount = StringUtils.countMatches(objectName, '\'');
        if (singleQuoteCount % 2 != 0) {
            return '"' + objectName + '"';
        } else {
            return objectName;
        }
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        StringBuilder ddlBuilder = new StringBuilder();
        String formatSchemaName = format(schemaName);
        String formatTableName = format(tableName);
        ddlBuilder.append("create table ").append(formatSchemaName).append(".").append(formatTableName);
        String options = SQLExecutor.getInstance().preExecute(connection, TABLE_OPTION_SQL, new String[]{schemaName, tableName}, resultSet -> {
            if (resultSet.next()) {
                StringBuilder optionBuilder = new StringBuilder();
                String tableOptions = resultSet.getString("table_options");
                if (StringUtils.isNotBlank(tableOptions)) {
                    tableOptions = tableOptions.replace("{", "(").replace("}", ")");
                    return optionBuilder.append(" with ").append(tableOptions).toString();
                }
            }
            return null;
        });
        String tablespace = SQLExecutor.getInstance().preExecute(connection, TABLE_SPACE_SQL, new String[]{schemaName, tableName}, resultSet -> {
            StringBuilder tableSpaceBuilder = new StringBuilder();
            tableSpaceBuilder.append(" tablespace ");
            if (resultSet.next()) {
                tableSpaceBuilder.append(resultSet.getString("tablespace"));
            } else {
                tableSpaceBuilder.append("pg_default");
            }
            tableSpaceBuilder.append(";\n");
            return tableSpaceBuilder.toString();
        });
        Boolean subTable = SQLExecutor.getInstance().preExecute(connection, PARTITIONED_SUB_TABLE_SQL, new String[]{schemaName, tableName}, resultSet -> {
            boolean isSub = false;
            if (resultSet.next()) {
                String parentTableName = resultSet.getString("PARENT_TABLE");
                String partitionDefinition = resultSet.getString("PARTITION_DEFINITION");
                if (StringUtils.isNotBlank(parentTableName) && StringUtils.isNotBlank(partitionDefinition)) {
                    ddlBuilder.append("\n").append(" partition of ").append(parentTableName).append("\n")
                            .append(partitionDefinition);
                    isSub = true;
                }
            }
            return isSub;
        });
        if (subTable) {
            if (StringUtils.isNotBlank(options)) {
                ddlBuilder.append(options);
            }
            return ddlBuilder.append("\n").append(tablespace).toString();
        }
        ddlBuilder.append("\n(\n");
        ArrayList<String> childTableInfo = SQLExecutor.getInstance().preExecute(connection, NORMAL_SUB_TABLE_SQL, new String[]{schemaName, tableName}, resultSet -> {
            ArrayList<String> subColumnSet = new ArrayList<>(2);
            boolean isFirst = true;
            while (resultSet.next()) {
                if (isFirst) {
                    //set 集合的前两位存储父表的schema以及名称
                    String parentSchema = resultSet.getString("parent_schema");
                    String parentTableName = resultSet.getString("parent_table");
                    subColumnSet.add(parentSchema);
                    subColumnSet.add(parentTableName);
                    isFirst = false;
                }
                String childColumnName = resultSet.getString("child_column");
                if (StringUtils.isNotBlank(childColumnName)) {
                    subColumnSet.add(childColumnName);
                }
            }
            return subColumnSet;
        });
        Long columnCount = SQLExecutor.getInstance().preExecute(connection, COLUMN_SQL, new String[]{schemaName, tableName}, resultSet -> {
            long total = 0;
            while (resultSet.next()) {
                total++;
                String columnName = resultSet.getString("column_name");
                // childTableInfo 前两位是父表信息
                if (childTableInfo.size() > 2) {
                    //检查该字段在不在子表字段列表，如果不在就是父表字段，所以需要跳过
                    if (!childTableInfo.contains(columnName)) {
                        continue;
                    }
                }
                String dataType = resultSet.getString("data_type");
                String columnDefault = resultSet.getString("column_default");
                String udtName = resultSet.getString("udt_name");
                String udtSchema = resultSet.getString("udt_schema");
                String identityGeneration = resultSet.getString("identity_generation");
                boolean isNullable = "YES".equals(resultSet.getString("is_nullable"));
                boolean isIdentity = "YES".equals(resultSet.getString("is_identity"));
                boolean isSerial = false;
                int identityIncrement = resultSet.getInt("identity_increment");
                int identityStart = resultSet.getInt("identity_start");
                int characterMaximumLength = resultSet.getInt("character_maximum_length");
                int numericPrecision = resultSet.getInt("numeric_precision");
                int numericScale = resultSet.getInt("numeric_scale");
                int datetimePrecision = resultSet.getInt("datetime_precision");
                ddlBuilder.append("\t").append(columnName).append("  ").append("\t");

                if (PostgreSQLColumnTypeEnum.CHARACTERVARYING.getColumnType().getTypeName().toLowerCase().equals(dataType)) {
                    ddlBuilder.append(PostgreSQLColumnTypeEnum.VARCHAR.name().toLowerCase());
                    if (characterMaximumLength >= 1) {
                        ddlBuilder.append("(").append(characterMaximumLength).append(")");
                    }
                } else if ("ARRAY".equals(dataType)) {
                    if (udtName.contains(PostgreSQLColumnTypeEnum.INT4.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.INTEGER.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.INT2.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.SMALLINT.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.INT8.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.BIGINT.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.VARBIT.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.BITVARYING.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.VARCHAR.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.CHARACTERVARYING.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.JSON.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.JSON.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.JSONB.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.JSONB.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.JSONPATH.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.JSONPATH.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.TEXT.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.TEXT.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.BPCHAR.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.CHAR.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.BIT.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.BIT.getColumnType().getTypeName().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.TIME.name().toLowerCase())) {
                        ddlBuilder.append("time without time zone").append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.TIMESTAMP.name().toLowerCase())) {
                        ddlBuilder.append("timestamp without time zone").append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.TIMETZ.name().toLowerCase())) {
                        ddlBuilder.append("time with time zone").append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.TIMESTAMPTZ.name().toLowerCase())) {
                        ddlBuilder.append("timestamp with time zone").append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.PATH.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.PATH.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.POINT.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.POINT.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.LINE.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.LINE.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.BOX.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.BOX.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.LSEG.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.LSEG.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.POLYGON.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.POLYGON.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.CIRCLE.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.CIRCLE.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.CIDR.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.CIDR.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.INET.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.INET.name().toLowerCase()).append("[]");
                    } else if (udtName.substring(1).equals(PostgreSQLColumnTypeEnum.MACADDR.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.MACADDR.name().toLowerCase()).append("[]");
                    } else if (udtName.contains("macaddr8")) {
                        ddlBuilder.append("macaddr8").append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.XML.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.XML.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.TSQUERY.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.TSQUERY.name().toLowerCase()).append("[]");
                    } else if (udtName.contains(PostgreSQLColumnTypeEnum.TSVECTOR.name().toLowerCase())) {
                        ddlBuilder.append(PostgreSQLColumnTypeEnum.TSVECTOR.name().toLowerCase()).append("[]");
                    } else {
                        ddlBuilder.append(dataType);
                    }
                } else if (PostgreSQLColumnTypeEnum.BIT.name().toLowerCase().equals(dataType)) {
                    ddlBuilder.append(udtName);
                    if (characterMaximumLength > 0 && characterMaximumLength != 1) {
                        ddlBuilder.append("(").append(characterMaximumLength).append(")");
                    }
                } else if (PostgreSQLColumnTypeEnum.BITVARYING.getColumnType().getTypeName().toLowerCase().equals(dataType)) {
                    ddlBuilder.append(PostgreSQLColumnTypeEnum.BITVARYING.getColumnType().getTypeName().toLowerCase());
                    if (characterMaximumLength > 0) {
                        ddlBuilder.append("(").append(characterMaximumLength).append(")");
                    }
                } else if ("USER-DEFINED".equals(dataType)) {
                    dataType = udtSchema + "." + format(udtName);
                    ddlBuilder.append(dataType);

                } else if (PostgreSQLColumnTypeEnum.TIMETZ.getColumnType().getTypeName().toLowerCase().equals(udtName)) {
                    if (datetimePrecision >= 0 && datetimePrecision != 6) {
                        dataType = "time" + "(" + datetimePrecision + ")" + " with time zone";
                    }
                    ddlBuilder.append(dataType);

                } else if (PostgreSQLColumnTypeEnum.TIMESTAMPTZ.getColumnType().getTypeName().toLowerCase().equals(udtName)) {
                    if (datetimePrecision >= 0 && datetimePrecision != 6) {
                        dataType = "timestamp" + "(" + datetimePrecision + ")" + " with time zone";
                    }
                    ddlBuilder.append(dataType);
                } else if (PostgreSQLColumnTypeEnum.TIMESTAMP.name().toLowerCase().equals(udtName)) {
                    ddlBuilder.append(udtName);
                    if (datetimePrecision >= 0 && datetimePrecision != 6) {
                        ddlBuilder.append("(").append(datetimePrecision).append(")");
                    }
                } else if (PostgreSQLColumnTypeEnum.INTERVAL.name().toLowerCase().equals(udtName)) {
                    ddlBuilder.append(udtName);
                    if (datetimePrecision >= 0 && datetimePrecision != 6) {
                        ddlBuilder.append("(").append(datetimePrecision).append(")");
                    }
                } else if (PostgreSQLColumnTypeEnum.TIME.name().toLowerCase().equals(udtName)) {
                    ddlBuilder.append(udtName);
                    if (datetimePrecision >= 0 && datetimePrecision != 6) {
                        ddlBuilder.append("(").append(datetimePrecision).append(")");
                    }
                } else if (PostgreSQLColumnTypeEnum.CHARACTER.name().toLowerCase().equals(dataType)) {
                    ddlBuilder.append(PostgreSQLColumnTypeEnum.CHAR.name().toLowerCase());
                    if (characterMaximumLength > 1) {
                        ddlBuilder.append("(").append(characterMaximumLength).append(")");
                    }
                } else if (PostgreSQLColumnTypeEnum.NUMERIC.name().toLowerCase().equals(dataType)) {
                    ddlBuilder.append(dataType);
                    if (numericPrecision > 0) {
                        ddlBuilder.append("(").append(numericPrecision);
                        if (numericScale != 0) {
                            ddlBuilder.append(",").append(numericScale);
                        }
                        ddlBuilder.append(")");
                    }
                } else if (isSerial && StringUtils.isNotBlank(columnDefault) && columnDefault.contains("nextval")) {
                    if (PostgreSQLColumnTypeEnum.INT8.name().toLowerCase().equals(udtName)) {
                        dataType = PostgreSQLColumnTypeEnum.BIGSERIAL.name().toLowerCase();
                    } else if (PostgreSQLColumnTypeEnum.INT2.name().toLowerCase().equals(udtName)) {
                        dataType = PostgreSQLColumnTypeEnum.SMALLSERIAL.name().toLowerCase();
                    } else if (PostgreSQLColumnTypeEnum.INT4.name().toLowerCase().equals(udtName)) {
                        dataType = PostgreSQLColumnTypeEnum.SERIAL.name().toLowerCase();
                    }
                    ddlBuilder.append(dataType);
                } else {
                    ddlBuilder.append(dataType);
                    if (isIdentity) {
                        ddlBuilder.append(" generated ").append(identityGeneration.toLowerCase()).append(" as identity");
                        if (!(identityStart == 1 && identityIncrement == 1)) {
                            ddlBuilder.append(" (start with ").append(identityStart).append(" increment by ").append(identityIncrement).append(")");
                        }
                    }
                }
                if (StringUtils.isNotBlank(columnDefault) && !isIdentity && !isSerial) {
                    ddlBuilder.append(" default ").append(columnDefault);
                }
                if (!isNullable && !isIdentity && !isSerial) {
                    ddlBuilder.append(" not null");
                }

                if (!resultSet.isLast()) {
                    ddlBuilder.append(",\n");
                }
            }
            return total;
        });

        if (columnCount == 0) {
            ddlBuilder.append(")").append(tablespace);
            return ddlBuilder.toString();
        }

        HashSet<String> constraints = SQLExecutor.getInstance().preExecute(connection, CONSTRAINT_SQL, new String[]{schemaName, tableName}, resultSet -> {
            HashSet<String> constraintNameSet = new HashSet<>();
            boolean isFirst = true;
            while (resultSet.next()) {
                String constraintDefinition = resultSet.getString("CONSTRAINT_DEFINITION");
                String constraintName = resultSet.getString("CONSTRAINT_NAME");
                if (StringUtils.isNotBlank(constraintName) && StringUtils.isNotBlank(constraintName)) {
                    constraintNameSet.add(constraintName);
                    if (isFirst) {
                        ddlBuilder.append(",\n");
                        isFirst = false;
                    }
                    ddlBuilder.append("\t").append("constraint ").append(constraintName).append(" ").append(constraintDefinition);
                    if (resultSet.isLast()) {
                        ddlBuilder.append("\n");
                    } else {
                        ddlBuilder.append(",\n");
                    }
                }
            }
            return constraintNameSet;

        });
        ddlBuilder.append("\n)");

        Boolean isPartitionedTable = SQLExecutor.getInstance().preExecute(connection, PARTITIONED_CONDITION_SQL, new String[]{schemaName, tableName}, resultSet -> {
            boolean isPartitioned = false;
            if (resultSet.next()) {
                ddlBuilder.append("partition by ").append(resultSet.getString("partition_key")).append(";");
                isPartitioned = true;
                ddlBuilder.append("\n");
            }
            return isPartitioned;
        });
        if (isPartitionedTable) {
            SQLExecutor.getInstance().preExecute(connection, LIST_PARTITIONED_SUB_TABLE_SQL, new String[]{schemaName, tableName}, resultSet -> {
                while (resultSet.next()) {
                    String subName = resultSet.getString("sub_name");
                    String schema_name = resultSet.getString("schema_name");
                    String parentTableName = resultSet.getString("PARENT_TABLE");
                    String partitionDefinition = resultSet.getString("PARTITION_DEFINITION");
                    if (StringUtils.isNotBlank(parentTableName) && StringUtils.isNotBlank(partitionDefinition)) {
                        ddlBuilder.append("create table ").append(schema_name).append(".").append(subName).append("\n")
                                .append("partition of ").append(parentTableName).append("\n")
                                .append(partitionDefinition).append(";\n");
                    }
                }

            });
            //证明这个是子表
        } else if (childTableInfo.size() >= 2) {
            String parentSchema = childTableInfo.get(0);
            String parentTableName = childTableInfo.get(1);
            ddlBuilder.append(" ").append(" inherits ").append("(").append(parentSchema).append(".").append(parentTableName).append(")").append("\n");
            if (StringUtils.isNotBlank(options)) {
                ddlBuilder.append(" ").append(options).append("\n");
            }
            ddlBuilder.append(tablespace);
        } else {
            ddlBuilder.append(tablespace);
        }
        SQLExecutor.getInstance().preExecute(connection, INDEX_SQL, new String[]{schemaName, tableName}, resultSet -> {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEXNAME");
                if (StringUtils.isNotBlank(indexName) && constraints.contains(indexName)) {
                    continue;
                }
                String indexDef = resultSet.getString("INDEXDEF");
                if (StringUtils.isNotBlank(indexDef) && StringUtils.isNotBlank(indexName)) {
                    ddlBuilder.append(indexDef).append(";").append("\n");
                }
            }
            ddlBuilder.append("\n");
        });

        SQLExecutor.getInstance().preExecute(connection, TABLE_COLUMN_COMMENT_SQL, new String[]{schemaName, tableName}, resultSet -> {
            while (resultSet.next()) {
                String comment = resultSet.getString("comment");

                if (StringUtils.isBlank(comment)) {
                    continue;
                }

                String objectType = resultSet.getString("object_type");
                String quoteTableName = resultSet.getString("table_name");
                String quoteSchemaName = resultSet.getString("schema_name");
                String columnName = resultSet.getString("column_name");

                ddlBuilder.append("comment on ").append(objectType.toLowerCase()).append(" ").append(quoteSchemaName).append(".").append(quoteTableName);

                if (StringUtils.isNotBlank(columnName)) {
                    ddlBuilder.append(".").append(columnName);
                }

                ddlBuilder.append(" is ").append(comment).append(";\n");

            }
        });

        SQLExecutor.getInstance().preExecute(connection, TABLE_INDEX_COMMENT_SQL, new String[]{schemaName, tableName}, resultSet -> {
            while (resultSet.next()) {

                String index_name = resultSet.getString("index_name");
                String index_comment = resultSet.getString("index_comment");

                ddlBuilder.append("comment on index ").append(index_name)
                        .append(" is ").append(index_comment).append(";\n");
            }

        });

//        SQLExecutor.getInstance().preExecute(connection, TABLE_SEQUENCES_COMMENT_SQL, new String[]{schemaName, schemaName, tableName}, resultSet -> {
//            while (resultSet.next()) {
//                String sequence_name = resultSet.getString("sequence_name");
//                String sequence_comment = resultSet.getString("sequence_comment");
//                ddlBuilder.append("COMMENT ON SEQUENCE ").append(sequence_name)
//                        .append(" IS ").append(sequence_comment).append(";\n");
//
//            }
//        });
        return ddlBuilder.toString();
    }


    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "f", functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setFunctionBody(resultSet.getString("code"));
            }
            return function;
        });

    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setDdl(resultSet.getString("definition"));
            }
            return table;
        });
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, schemaName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("trigger_body"));
            }

            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "p", procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            if (resultSet.next()) {
                procedure.setProcedureBody(resultSet.getString("code"));
            }
            return procedure;
        });
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {

        String constraintSql = String.format(SELECT_KEY_INDEX, schemaName, tableName);
        Map<String, String> constraintMap = new HashMap();
        LinkedHashMap<String, TableIndex> foreignMap = new LinkedHashMap();
        SQLExecutor.getInstance().execute(connection, constraintSql, resultSet -> {
            while (resultSet.next()) {
                String keyName = resultSet.getString("Key_name");
                String constraintType = resultSet.getString("Constraint_type");
                constraintMap.put(keyName, constraintType);
                if (StringUtils.equalsIgnoreCase(constraintType, PostgreSQLIndexTypeEnum.FOREIGN.getKeyword())) {
                    TableIndex tableIndex = foreignMap.get(keyName);
                    String columnName = resultSet.getString("Column_name");
                    if (tableIndex == null) {
                        tableIndex = new TableIndex();
                        tableIndex.setDatabaseName(databaseName);
                        tableIndex.setSchemaName(schemaName);
                        tableIndex.setTableName(tableName);
                        tableIndex.setName(keyName);
                        tableIndex.setForeignSchemaName(resultSet.getString("Foreign_schema_name"));
                        tableIndex.setForeignTableName(resultSet.getString("Foreign_table_name"));
                        tableIndex.setForeignColumnNamelist(Lists.newArrayList(columnName));
                        tableIndex.setType(PostgreSQLIndexTypeEnum.FOREIGN.getName());
                        foreignMap.put(keyName, tableIndex);
                    } else {
                        tableIndex.getForeignColumnNamelist().add(columnName);
                    }
                }
            }
            return null;
        });

        String sql = String.format(SELECT_TABLE_INDEX, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap(foreignMap);

            while (resultSet.next()) {
                String keyName = resultSet.getString("Key_name");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    if (columnList == null) {
                        columnList = new ArrayList<>();
                        tableIndex.setColumnList(columnList);
                    }
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
                    index.setUnique(!StringUtils.equals("t", resultSet.getString("NON_UNIQUE")));
                    index.setMethod(resultSet.getString("Index_method"));
                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    String constraintType = constraintMap.get(keyName);
                    if (StringUtils.equals("t", resultSet.getString("Index_primary"))) {
                        index.setType(PostgreSQLIndexTypeEnum.PRIMARY.getName());
                    } else if (StringUtils.equalsIgnoreCase(constraintType, PostgreSQLIndexTypeEnum.UNIQUE.getName())) {
                        index.setType(PostgreSQLIndexTypeEnum.UNIQUE.getName());
                    } else {
                        index.setType(PostgreSQLIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableColumn> columnList = super.columns(connection, databaseName, schemaName, tableName);

        EasyCollectionUtils.stream(columnList).forEach(v -> {
            if (StringUtils.equalsIgnoreCase(v.getColumnType(), "bpchar")) {
                v.setColumnType(PostgreSQLColumnTypeEnum.CHAR.getColumnType().getTypeName().toUpperCase());
            } else {
                v.setColumnType(v.getColumnType().toUpperCase());
            }
        });
        return columnList;
    }

    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("Column_name"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("Seq_in_index"));
        tableIndexColumn.setCollation(resultSet.getString("Collation"));
        tableIndexColumn.setAscOrDesc(resultSet.getString("Collation"));
        return tableIndexColumn;
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new PostgreSQLSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(PostgreSQLColumnTypeEnum.getTypes())
                .charsets(PostgreSQLCharsetEnum.getCharsets())
                .collations(PostgreSQLCollationEnum.getCollations())
                .indexTypes(PostgreSQLIndexTypeEnum.getIndexTypes())
                .defaultValues(PostgreSQLDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
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
