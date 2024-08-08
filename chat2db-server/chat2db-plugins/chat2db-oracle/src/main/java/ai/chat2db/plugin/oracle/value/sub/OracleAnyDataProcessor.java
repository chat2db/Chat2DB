package ai.chat2db.plugin.oracle.value.sub;

import ai.chat2db.spi.jdbc.DefaultValueProcessor;
import ai.chat2db.spi.model.JDBCDataValue;
import ai.chat2db.spi.model.SQLDataValue;

/**
 * @author: zgq
 * @date: 2024年07月07日 10:42
 */
public class OracleAnyDataProcessor extends DefaultValueProcessor {
    /**
     * @param dataValue
     * @return
     */
    @Override
    public String convertSQLValueByType(SQLDataValue dataValue) {
        return dataValue.getValue();
    }

    @Override
    public String convertJDBCValueByType(JDBCDataValue dataValue) {
//        byte[] bytes = dataValue.getBytes();
//        int length = bytes.length;
//        String rawString = new String(bytes);
//
//        // Filter printable characters
//        StringBuilder printableString = new StringBuilder();
//        for (char c : rawString.toCharArray()) {
//            if (c >= 32 && c <= 126) { // ASCII printable characters range
//                printableString.append(c);
//            }
//        }

        return "SYS.ANYDATA";
    }


    /**
     * @param dataValue
     * @return
     */
    @Override
    public String convertJDBCValueStrByType(JDBCDataValue dataValue) {
        return "SYS.ANYDATA";
    }


}
