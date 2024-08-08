package ai.chat2db.plugin.mysql.value.factory;

import ai.chat2db.plugin.mysql.type.MysqlColumnTypeEnum;
import ai.chat2db.plugin.mysql.value.sub.*;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;

import java.util.Map;

/**
 * @author: zgq
 * @date: 2024年06月03日 23:16
 */
public class MysqlValueProcessorFactory {

    private static final Map<String, DefaultValueProcessor> PROCESSOR_MAP;

    static {
        MysqlGeometryProcessor mysqlGeometryProcessor = new MysqlGeometryProcessor();
        MysqlVarBinaryProcessor mysqlVarBinaryProcessor = new MysqlVarBinaryProcessor();
        MysqlTimestampProcessor mysqlTimestampProcessor = new MysqlTimestampProcessor();
        MysqlTextProcessor mysqlTextProcessor = new MysqlTextProcessor();
        PROCESSOR_MAP = Map.ofEntries(
                //text
                Map.entry(MysqlColumnTypeEnum.TEXT.name(), mysqlTextProcessor),
                Map.entry(MysqlColumnTypeEnum.TINYTEXT.name(), mysqlTextProcessor),
                Map.entry(MysqlColumnTypeEnum.MEDIUMTEXT.name(), mysqlTextProcessor),
                Map.entry(MysqlColumnTypeEnum.LONGTEXT.name(), mysqlTextProcessor),
                // geometry
                Map.entry(MysqlColumnTypeEnum.GEOMETRY.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.POINT.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.LINESTRING.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.POLYGON.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.MULTIPOINT.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.MULTILINESTRING.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.MULTIPOLYGON.name(), mysqlGeometryProcessor),
                Map.entry(MysqlColumnTypeEnum.GEOMETRYCOLLECTION.name(), mysqlGeometryProcessor),
                // binary
                Map.entry(MysqlColumnTypeEnum.VARBINARY.name(), mysqlVarBinaryProcessor),
                Map.entry(MysqlColumnTypeEnum.BLOB.name(), mysqlVarBinaryProcessor),
                Map.entry(MysqlColumnTypeEnum.LONGBLOB.name(), mysqlVarBinaryProcessor),
                Map.entry(MysqlColumnTypeEnum.TINYBLOB.name(), mysqlVarBinaryProcessor),
                Map.entry(MysqlColumnTypeEnum.MEDIUMBLOB.name(), mysqlVarBinaryProcessor),
                // timestamp
                Map.entry(MysqlColumnTypeEnum.TIMESTAMP.name(), mysqlTimestampProcessor),
                Map.entry(MysqlColumnTypeEnum.DATETIME.name(), mysqlTimestampProcessor),
                //others
                Map.entry(MysqlColumnTypeEnum.YEAR.name(), new MysqlYearProcessor()),
                Map.entry(MysqlColumnTypeEnum.BIT.name(), new MysqlBitProcessor()),
                Map.entry(MysqlColumnTypeEnum.DECIMAL.name(), new MysqlDecimalProcessor()),
                Map.entry(MysqlColumnTypeEnum.BINARY.name(), new MysqlBinaryProcessor())
        );
    }

    public static DefaultValueProcessor getValueProcessor(String type) {
        return PROCESSOR_MAP.get(type);
    }
}
