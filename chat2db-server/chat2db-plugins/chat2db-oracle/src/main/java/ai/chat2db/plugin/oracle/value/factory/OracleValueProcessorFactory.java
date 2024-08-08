package ai.chat2db.plugin.oracle.value.factory;

import ai.chat2db.plugin.oracle.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.value.sub.*;
import ai.chat2db.spi.jdbc.DefaultValueProcessor;

import java.util.Map;

/**
 * @author: zgq
 * @date: 2024年06月03日 23:21
 */  // TODO: 1.空间数据类型  2.动态类型数据
public class OracleValueProcessorFactory {

    private static final Map<String, DefaultValueProcessor> PROCESSOR_MAP;

    static {
        OracleClobProcessor oracleClobProcessor = new OracleClobProcessor();
        OracleTimeStampProcessor oracleTimeStampProcessor = new OracleTimeStampProcessor();
        OracleBlobProcessor oracleBlobProcessor = new OracleBlobProcessor();
        OracleRawValueProcessor oracleRawValueProcessor = new OracleRawValueProcessor();
        PROCESSOR_MAP = Map.ofEntries(
                //clob
                Map.entry(OracleColumnTypeEnum.CLOB.name(), oracleClobProcessor),
                Map.entry(OracleColumnTypeEnum.NCLOB.name(), oracleClobProcessor),
                Map.entry(OracleColumnTypeEnum.LONG.name(), oracleClobProcessor),
                //date
                Map.entry(OracleColumnTypeEnum.DATE.name(), new OracleDateProcessor()),
                //timestamp
                Map.entry(OracleColumnTypeEnum.TIMESTAMP.name(), oracleTimeStampProcessor),
                Map.entry(OracleColumnTypeEnum.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getColumnType().getTypeName(), new OracleTimeStampLTZProcessor()),
                Map.entry(OracleColumnTypeEnum.TIMESTAMP_WITH_TIME_ZONE.getColumnType().getTypeName(), new OracleTimeStampTZProcessor()),
                //INTERVAL
                Map.entry("INTERVALDS", new OracleIntervalDSProcessor()),
                Map.entry("INTERVALYM", new OracleIntervalYMProcessor()),
                //number
                Map.entry(OracleColumnTypeEnum.NUMBER.name(), new OracleNumberProcessor()),
                //blob
                Map.entry(OracleColumnTypeEnum.BLOB.name(), oracleBlobProcessor),
                //raw
                Map.entry(OracleColumnTypeEnum.RAW.name(), oracleRawValueProcessor),
                //long raw
                Map.entry(OracleColumnTypeEnum.LONG_RAW.getColumnType().getTypeName(), new OracleLongRawProcessor()),
                //xml
                Map.entry("SYS.XMLTYPE", new OracleXmlValueProcessor()),
                Map.entry("SYS.ANYDATA", new OracleAnyDataProcessor())
        );

    }

    public static DefaultValueProcessor getValueProcessor(String type) {
        return PROCESSOR_MAP.get(type);
    }

}
