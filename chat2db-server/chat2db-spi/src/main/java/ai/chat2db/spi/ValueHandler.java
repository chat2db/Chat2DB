package ai.chat2db.spi;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface ValueHandler {

    /**
     * 处理结果集中的列值
     * @param rs
     * @param index
     * @param limitSize
     * @return
     * @throws SQLException
     */
    String getString(ResultSet rs, int index, boolean limitSize)throws SQLException;

    /**
     * 处理结果集中的列值
     * @param rs
     * @param index
     * @param limitSize
     * @return Object
     * @throws SQLException
     */
    Object getObject(ResultSet rs, int index, boolean limitSize)throws SQLException;
}
