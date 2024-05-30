package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.enums.TableVectorEnum;
import ai.chat2db.server.domain.api.param.*;
import ai.chat2db.server.domain.api.service.PinService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.core.cache.CacheManage;
import ai.chat2db.server.domain.core.converter.PinTableConverter;
import ai.chat2db.server.domain.core.converter.TableConverter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.TableCacheDO;
import ai.chat2db.server.domain.repository.entity.TableCacheVersionDO;
import ai.chat2db.server.domain.repository.entity.TableVectorMappingDO;
import ai.chat2db.server.domain.repository.mapper.TableCacheMapper;
import ai.chat2db.server.domain.repository.mapper.TableCacheVersionMapper;
import ai.chat2db.server.domain.repository.mapper.TableVectorMappingMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.enums.EditStatus;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.chat2db.server.domain.core.cache.CacheKey.getColumnKey;
import static ai.chat2db.server.domain.core.cache.CacheKey.getTableKey;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 September 23, 2022 15:51 moji Exp $
 * @date 2022/09/23
 */
@Service
@Slf4j
public class TableServiceImpl implements TableService {

    @Autowired
    private PinService pinService;

    @Autowired
    private PinTableConverter pinTableConverter;


    private TableCacheMapper getTableCacheMapper() {
        return Dbutils.getMapper(TableCacheMapper.class);
    }

    @Autowired
    private TableConverter tableConverter;


    private TableCacheVersionMapper getVersionMapper() {
        return Dbutils.getMapper(TableCacheVersionMapper.class);
    }


    private TableVectorMappingMapper getTableVectorMapper() {
        return Dbutils.getMapper(TableVectorMappingMapper.class);
    }

    @Override
    public DataResult<String> showCreateTable(ShowCreateTableParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        return DataResult.of(ddl);
    }

    @Override
    public ActionResult drop(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.dropTable(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getTableSchema(), param.getTableName());
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<String> createTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleCreateTable();
        return DataResult.of(sql);
    }

    @Override
    public DataResult<String> alterTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleAlterTable();
        return DataResult.of(sql);
    }

    @Override
    public DataResult<Table> query(TableQueryParam param, TableSelector selector) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        List<Table> tables = metaSchema.tables(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        if (!CollectionUtils.isEmpty(tables)) {
            Table table = tables.get(0);
            table.setIndexList(
                    metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
            table.setColumnList(
                    metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
            setPrimaryKey(table);
            return DataResult.of(table);
        }
        return DataResult.of(null);
    }

    private void setPrimaryKey(Table table) {
        if (table == null) {
            return;
        }
        List<TableIndex> tableIndices = table.getIndexList();
        if (CollectionUtils.isEmpty(tableIndices)) {
            return;
        }
        List<TableColumn> columns = table.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        Map<String, TableColumn> columnMap = columns.stream()
                .collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        List<TableIndex> indexes = new ArrayList<>();
        for (TableIndex tableIndex : tableIndices) {
            if ("Primary".equalsIgnoreCase(tableIndex.getType())) {
                List<TableIndexColumn> indexColumns = tableIndex.getColumnList();
                if (CollectionUtils.isNotEmpty(indexColumns)) {
                    for (TableIndexColumn indexColumn : indexColumns) {
                        TableColumn column = columnMap.get(indexColumn.getColumnName());
                        if (column != null) {
                            column.setPrimaryKey(true);
                            column.setPrimaryKeyOrder(indexColumn.getOrdinalPosition());
                            column.setPrimaryKeyName(tableIndex.getName());
                        }
                    }
                }
            } else {
                indexes.add(tableIndex);
            }
        }
        table.setIndexList(indexes);
    }

    @Override
    public ListResult<Sql> buildSql(Table oldTable, Table newTable) {
        initOldTable(oldTable, newTable);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        List<Sql> sqls = new ArrayList<>();
        if (oldTable == null) {
            initPrimaryKey(newTable);
            sqls.add(Sql.builder().sql(sqlBuilder.buildCreateTableSql(newTable)).build());
        } else {
            initUpdatePrimaryKey(oldTable, newTable);
            sqls.add(Sql.builder().sql(sqlBuilder.buildModifyTaleSql(oldTable, newTable)).build());
        }
        return ListResult.of(sqls);
    }

    private void initUpdatePrimaryKey(Table oldTable, Table newTable) {
        if (newTable == null || oldTable == null) {
            return;
        }
        List<TableColumn> newColumns = getPrimaryKeyColumn(newTable);
        List<TableColumn> oldColumns = getPrimaryKeyColumn(oldTable);
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            return;
        }
        if (!CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            initPrimaryKey(newTable);
            return;
        }
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isNotEmpty(oldColumns)) {
            addPrimaryKey(newTable, oldColumns.get(0), EditStatus.DELETE.name());
            return;
        }
        if (newColumns.size() != oldColumns.size()) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
            return;
        }
        boolean flag = false;
        Map<String, TableColumn> oldColumnMap = oldColumns.stream().collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        for (TableColumn column : newColumns) {
            TableColumn oldColumn = oldColumnMap.get(column.getName());
            if (oldColumn == null) {
                flag = true;
            }
        }
        if (flag) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
        }
    }

    private List<TableColumn> getPrimaryKeyColumn(Table table) {
        if (table == null || CollectionUtils.isEmpty(table.getColumnList())) {
            return null;
        }
        return table.getColumnList().stream().filter(tableColumn ->
                        tableColumn.getPrimaryKey() != null && tableColumn.getPrimaryKey())
                .collect(Collectors.toList());
    }

    private void initPrimaryKey(Table newTable) {
        if (newTable == null) {
            return;
        }
        List<TableColumn> columns = newTable.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        for (TableColumn column : columns) {
            if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                addPrimaryKey(newTable, column, EditStatus.ADD.name());
            }
        }
    }

    private void addPrimaryKey(Table newTable, TableColumn column, String status) {
        List<TableIndex> indexes = newTable.getIndexList();
        if (indexes == null) {
            indexes = new ArrayList<>();
        }
        TableIndex keyIndex = indexes.stream().filter(index -> "Primary".equalsIgnoreCase(index.getType())).findFirst().orElse(null);
        if (keyIndex == null) {
            keyIndex = new TableIndex();
            keyIndex.setType("Primary");
            keyIndex.setName(StringUtils.isBlank(column.getPrimaryKeyName()) ? "PRIMARY_KEY" : column.getPrimaryKeyName());
            keyIndex.setTableName(newTable.getName());
            keyIndex.setSchemaName(newTable.getSchemaName());
            keyIndex.setDatabaseName(newTable.getDatabaseName());
            keyIndex.setEditStatus(status);
            if (!EditStatus.ADD.name().equals(status)) {
                keyIndex.setOldName(keyIndex.getName());
            }
            indexes.add(keyIndex);
        }
        List<TableIndexColumn> tableIndexColumns = keyIndex.getColumnList();
        if (tableIndexColumns == null) {
            tableIndexColumns = new ArrayList<>();
        }
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName(column.getName());
        indexColumn.setTableName(newTable.getName());
        indexColumn.setSchemaName(newTable.getSchemaName());
        indexColumn.setDatabaseName(newTable.getDatabaseName());
        indexColumn.setOrdinalPosition(Short.valueOf(column.getPrimaryKeyOrder() + ""));
        indexColumn.setEditStatus(status);
        tableIndexColumns.add(indexColumn);
        List<TableIndexColumn> sortTableIndexColumns = tableIndexColumns.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition)).collect(Collectors.toList());
        Set<String> statusList = sortTableIndexColumns.stream().map(TableIndexColumn::getEditStatus).collect(Collectors.toSet());
        if (statusList.size() == 1) {
            //only one status ,set index status
            keyIndex.setEditStatus(statusList.iterator().next());
        } else {
            //more status ,set index status modify
            keyIndex.setEditStatus(EditStatus.MODIFY.name());
        }

        keyIndex.setColumnList(sortTableIndexColumns);
        newTable.setIndexList(indexes);

    }


    private void initOldTable(Table oldTable, Table newTable) {
        if (oldTable == null || newTable == null) {
            return;
        }
        Map<String, TableColumn> columnMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(oldTable.getColumnList())) {
            for (TableColumn column : oldTable.getColumnList()) {
                columnMap.put(column.getName(), column);
            }
        }
        if (CollectionUtils.isNotEmpty(newTable.getColumnList())) {
            for (TableColumn newColumn : newTable.getColumnList()) {
                if (EditStatus.ADD.name().equals(newColumn.getEditStatus())) {
                    continue;
                }
                String name = newColumn.getOldName() == null ? newColumn.getName() : newColumn.getOldName();
                TableColumn oldColumn = columnMap.get(name);
                if (oldColumn != null) {
                    if (oldColumn.equals(newColumn) && EditStatus.MODIFY.name().equals(newColumn.getEditStatus())) {
                        newColumn.setEditStatus(null);
                    } else {
                        newColumn.setOldColumn(oldColumn);
                    }
                }
            }
        }
    }

    @Override
    public PageResult<Table> pageQuery(TablePageQueryParam param, TableSelector selector) {
        LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper<>();
        String key = getTableKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName());
        queryWrapper.eq(TableCacheVersionDO::getKey, key);
        TableCacheVersionDO versionDO = getVersionMapper().selectOne(queryWrapper);
        long total = 0;
        long version = 0L;
        if (param.isRefresh() || versionDO == null) {
            total = addCache(param, versionDO);
        } else {
            if ("2".equals(versionDO.getStatus())) {
                version = versionDO.getVersion() - 1;
            } else {
                version = versionDO.getVersion();
            }
            total = versionDO.getTableCount();
        }
        Page<TableCacheDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        // page.setSearchCount(param.getEnableReturnCount());
        IPage<TableCacheDO> iPage = getTableCacheMapper().pageQuery(page, param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), param.getSearchKey());
        List<Table> tables = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
            for (TableCacheDO tableCacheDO : iPage.getRecords()) {
                Table t = new Table();
                t.setName(tableCacheDO.getTableName());
                t.setComment(tableCacheDO.getExtendInfo());
                t.setSchemaName(tableCacheDO.getSchemaName());
                t.setDatabaseName(tableCacheDO.getDatabaseName());
                tables.add(t);
            }
        }
        if (param.getPageNo() <= 1) {
            tables = pinTable(tables, param);
        }
        return PageResult.of(tables, total, param);
    }

    private long addCache(TablePageQueryParam param, TableCacheVersionDO versionDO) {
        LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper<>();
        String key = getTableKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName());
        queryWrapper.eq(TableCacheVersionDO::getKey, key);
        long total = 0;
        long version = getLock(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), versionDO);
        if (version == -1) {
            int n = 0;
            while (n < 100) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                versionDO = getVersionMapper().selectOne(queryWrapper);
                if (versionDO != null && "1".equals(versionDO.getStatus())) {
                    version = versionDO.getVersion();
                    total = versionDO.getTableCount();
                    break;
                }
                n++;
            }
        } else {
            total = addDBCache(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), version);
            TableCacheVersionDO versionDO1 = new TableCacheVersionDO();
            versionDO1.setStatus("1");
            versionDO1.setTableCount(total);
            getVersionMapper().update(versionDO1, queryWrapper);
        }
        return total;
    }

    @Override
    public ListResult<SimpleTable> queryTables(TablePageQueryParam param) {
        LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper<>();
        String key = getTableKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName());
        queryWrapper.eq(TableCacheVersionDO::getKey, key);
        TableCacheVersionDO versionDO = getVersionMapper().selectOne(queryWrapper);
        if (versionDO == null) {
            addCache(param, versionDO);
            versionDO = getVersionMapper().selectOne(queryWrapper);
        }
        long version = "2".equals(versionDO.getStatus()) ? versionDO.getVersion() - 1 : versionDO.getVersion();

        LambdaQueryWrapper<TableCacheDO> query = new LambdaQueryWrapper<>();
        query.eq(TableCacheDO::getVersion, version);
        query.eq(TableCacheDO::getDataSourceId, param.getDataSourceId());
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            query.eq(TableCacheDO::getDatabaseName, param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getSchemaName())) {
            query.eq(TableCacheDO::getSchemaName, param.getSchemaName());
        }
        List<SimpleTable> tables = new ArrayList<>();

        for (int i = 0; i < versionDO.getTableCount() / 500 + 1; i++) {
            Page<TableCacheDO> page = new Page<>(i + 1, 500);
            IPage<TableCacheDO> iPage = getTableCacheMapper().selectPage(page, query);
            if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
                for (TableCacheDO tableCacheDO : iPage.getRecords()) {
                    SimpleTable t = new SimpleTable();
                    t.setName(tableCacheDO.getTableName());
                    t.setComment(tableCacheDO.getExtendInfo());
                    tables.add(t);
                }
            }
        }
        return ListResult.of(tables);
    }

    private long addDBCache(Long dataSourceId, String databaseName, String schemaName, long version) {
        String key = getTableKey(dataSourceId, databaseName, schemaName);

        Connection connection = Chat2DBContext.getConnection();
        long n = 0;
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, schemaName, null,
                new String[]{"TABLE", "SYSTEM TABLE"})) {
            List<TableCacheDO> cacheDOS = new ArrayList<>();
            while (resultSet.next()) {
                TableCacheDO tableCacheDO = new TableCacheDO();
                tableCacheDO.setDatabaseName(databaseName);
                tableCacheDO.setSchemaName(schemaName);
                tableCacheDO.setTableName(resultSet.getString("TABLE_NAME"));
                tableCacheDO.setExtendInfo(resultSet.getString("REMARKS"));
                tableCacheDO.setDataSourceId(dataSourceId);
                tableCacheDO.setVersion(version);
                tableCacheDO.setKey(key);
                cacheDOS.add(tableCacheDO);
                if (cacheDOS.size() >= 500) {
                    getTableCacheMapper().batchInsert(cacheDOS);
                    cacheDOS = new ArrayList<>();
                }
                n++;
            }
            if (!CollectionUtils.isEmpty(cacheDOS)) {
                getTableCacheMapper().batchInsert(cacheDOS);
            }
            LambdaQueryWrapper<TableCacheDO> q = new LambdaQueryWrapper();
            q.eq(TableCacheDO::getDataSourceId, dataSourceId);
            q.lt(TableCacheDO::getVersion, version);
            if (StringUtils.isNotBlank(databaseName)) {
                q.eq(TableCacheDO::getDatabaseName, databaseName);
            }
            if (StringUtils.isNotBlank(schemaName)) {
                q.eq(TableCacheDO::getSchemaName, schemaName);
            }
            getTableCacheMapper().delete(q);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return n;
    }

    private Long getLock(Long dataSourceId, String databaseName, String schemaName, TableCacheVersionDO versionDO) {
        String key = getTableKey(dataSourceId, databaseName, schemaName);
        if (versionDO == null) {
            versionDO = new TableCacheVersionDO();
            versionDO.setDatabaseName(databaseName);
            versionDO.setSchemaName(schemaName);
            versionDO.setDataSourceId(dataSourceId);
            versionDO.setStatus("2");
            versionDO.setKey(key);
            versionDO.setVersion(0L);
            versionDO.setTableCount(0L);
            try {
                getVersionMapper().insert(versionDO);
                return 0L;
            } catch (Exception e) {
                log.error("getLock error", e);
                return -1L;
            }
        } else {
            long version = versionDO.getVersion() + 1;
            LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper();
            queryWrapper.eq(TableCacheVersionDO::getId, versionDO.getId());
            queryWrapper.eq(TableCacheVersionDO::getVersion, versionDO.getVersion());
            versionDO.setVersion(version);
            versionDO.setStatus("2");
            int n = getVersionMapper().update(versionDO, queryWrapper);
            if (n == 1) {
                return version;
            } else {
                return -1L;
            }
        }
    }


//    private String buildKey(Long dataSourceId, String databaseName, String schemaName) {
//        StringBuilder stringBuilder = new StringBuilder(dataSourceId.toString());
//        if (StringUtils.isNotBlank(databaseName)) {
//            stringBuilder.append("_").append(databaseName);
//        }
//        if (StringUtils.isNotBlank(schemaName)) {
//            stringBuilder.append("_").append(schemaName);
//        }
//        return stringBuilder.toString();
//    }

    private List<Table> pinTable(List<Table> list, TablePageQueryParam param) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        PinTableParam pinTableParam = pinTableConverter.toPinTableParam(param);
        pinTableParam.setUserId(ContextUtils.getUserId());
        ListResult<String> listResult = pinService.queryPinTables(pinTableParam);
        if (!listResult.success() || CollectionUtils.isEmpty(listResult.getData())) {
            return list;
        }
        List<Table> tables = new ArrayList<>();
        Map<String, Table> tableMap = list.stream().collect(Collectors.toMap(Table::getName, Function.identity()));
        for (String tableName : listResult.getData()) {
            Table table = tableMap.get(tableName);
            if (table != null) {
                table.setPinned(true);
                tables.add(table);
            }
        }

        for (Table table : list) {
            if (table != null && !tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    @Override
    public List<TableColumn> queryColumns(TableQueryParam param) {
        String tableColumnKey = getColumnKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return CacheManage.getList(tableColumnKey, TableColumn.class,
                (key) -> param.isRefresh(), (key) ->
                        metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
    }

    @Override
    public List<TableIndex> queryIndexes(TableQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());

    }

    @Override
    public List<Type> queryTypes(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.types(Chat2DBContext.getConnection());
    }

    @Override
    public TableMeta queryTableMeta(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        TableMeta tableMeta = metaSchema.getTableMeta(null, null, null);
        if (tableMeta != null) {
            //filter primary key
            List<IndexType> indexTypes = tableMeta.getIndexTypes();
            if (CollectionUtils.isNotEmpty(indexTypes)) {
                List<IndexType> types = indexTypes.stream().filter(indexType -> !"Primary".equalsIgnoreCase(indexType.getTypeName())).collect(Collectors.toList());
                tableMeta.setIndexTypes(types);
            }
        }
        return tableMeta;

    }

    @Override
    public ActionResult saveTableVector(TableVectorParam param) {
        if (checkTableVector(param).getData()) {
            return ActionResult.isSuccess();
        }
        TableVectorMappingDO mappingDO = tableConverter.toTableVectorMappingDO(param);
        mappingDO.setStatus(TableVectorEnum.SAVED.getCode());
        getTableVectorMapper().insert(mappingDO);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Boolean> checkTableVector(TableVectorParam param) {
        LambdaQueryWrapper<TableVectorMappingDO> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(TableVectorMappingDO::getApiKey, param.getApiKey());
        queryWrapper.eq(TableVectorMappingDO::getDataSourceId, param.getDataSourceId());
        queryWrapper.eq(TableVectorMappingDO::getDatabase, param.getDatabase());
        queryWrapper.eq(TableVectorMappingDO::getSchema, param.getSchema());
        TableVectorMappingDO mappingDO = getTableVectorMapper().selectOne(queryWrapper);
        if (Objects.nonNull(mappingDO) && TableVectorEnum.SAVED.getCode().equals(mappingDO.getStatus())) {
            return DataResult.of(true);
        }
        return DataResult.of(false);
    }

    @Override
    public DataResult<String> copyDmlSql(DmlSqlCopyParam param) {
        List<TableColumn> columns = queryColumns(param);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        Table table = Table.builder().name(param.getTableName()).columnList(columns).build();
        String sql = sqlBuilder.getTableDmlSql(table, param.getType());
        return DataResult.of(sql);
    }
}
