//package ai.chat2db.spi.jdbc;
//
//import ai.chat2db.server.tools.common.util.I18nUtils;
//import ai.chat2db.spi.ValueHandler;
//import cn.hutool.core.io.unit.DataSizeUtil;
//import lombok.extern.slf4j.Slf4j;
//
//import java.math.BigDecimal;
//import java.nio.charset.StandardCharsets;
//import java.sql.*;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//@Slf4j
//public class DefaultValueHandler implements ValueHandler {
//
//    private static final long MAX_RESULT_SIZE = 10* 1024 * 1024;
//
//    @Override
//    public String getString(ResultSet rs, int index, boolean limitSize) throws SQLException {
//        try {
//            Object obj = rs.getObject(index);
//            if (obj == null) {
//                return null;
//            }
//            if (obj instanceof BigDecimal bigDecimal) {
//                return bigDecimal.toPlainString();
//            } else if (obj instanceof Double d) {
//                return BigDecimal.valueOf(d).toPlainString();
//            } else if (obj instanceof Float f) {
//                return BigDecimal.valueOf(f).toPlainString();
//            } else if (obj instanceof Clob) {
//                return largeString(rs, index, limitSize);
//            } else if (obj instanceof byte[]) {
//                return largeString(rs, index, limitSize);
//            } else if (obj instanceof Blob blob) {
//                return largeStringBlob(blob, limitSize);
//            } else if (obj instanceof Timestamp || obj instanceof LocalDateTime) {
//                return largeTime(obj);
//            } else if (obj instanceof SQLXML){
//                return ((SQLXML) obj).getString();
//            } else {
//                return obj.toString();
//            }
//        } catch (Exception e) {
//            log.warn("Failed to parse number:{},", index, e);
//            return rs.getString(index);
//        }
//    }
//
//    private String largeStringBlob(Blob blob, boolean limitSize) throws SQLException {
//        if (blob == null) {
//            return null;
//        }
//        int length = Math.toIntExact(blob.length());
//        if (limitSize && length > MAX_RESULT_SIZE) {
//            length = Math.toIntExact(MAX_RESULT_SIZE);
//        }
//        byte[] data = blob.getBytes(1, length);
//        String result = new String(data, StandardCharsets.UTF_8);
//
//        if (length > MAX_RESULT_SIZE) {
//            return "[ " + DataSizeUtil.format(MAX_RESULT_SIZE) + " of " + DataSizeUtil.format(length)
//                    + " ,"
//                    + I18nUtils.getMessage("execute.exportCsv") + " ] " + result;
//        }
//        return result;
//    }
//
//    private String largeTime(Object obj) throws SQLException {
//        Object timeField = obj; // Assuming a time field of type Object
//
//        LocalDateTime localDateTime;
//
//        if (obj instanceof Timestamp) {
//            // Convert a time field of type Object to a LocalDateTime object
//            localDateTime = ((Timestamp) timeField).toLocalDateTime();
//        } else if(obj instanceof  LocalDateTime){
//            localDateTime = (LocalDateTime) timeField;
//        } else {
//            try {
//                localDateTime = LocalDateTime.parse(timeField.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
//            }catch (Exception e){
//                localDateTime = LocalDateTime.parse(timeField.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
//            }
//        }
//        // Create a DateTimeFormatter instance and specify the output date and time format
//        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//        // Format date time
//        String formattedDateTime = dtf.format(localDateTime);
//        return formattedDateTime;
//    }
//
//    private static String largeString(ResultSet rs, int index, boolean limitSize) throws SQLException {
//        String result = rs.getString(index);
//        if (result == null) {
//            return null;
//
//        }
//        if (!limitSize) {
//            return result;
//        }
//
//        if (result.length() > MAX_RESULT_SIZE) {
//            return "[ " + DataSizeUtil.format(MAX_RESULT_SIZE) + " of " + DataSizeUtil.format(result.length()) + " ,"
//                    + I18nUtils.getMessage("execute.exportCsv") + " ] " + result.substring(0,
//                    Math.toIntExact(MAX_RESULT_SIZE));
//        }
//        return result;
//    }
//}
