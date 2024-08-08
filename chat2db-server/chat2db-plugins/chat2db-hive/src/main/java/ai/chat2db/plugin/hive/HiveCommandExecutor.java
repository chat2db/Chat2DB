package ai.chat2db.plugin.hive;

import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HiveCommandExecutor extends SQLExecutor {

    /**
     * Execute command
     */
    @Override
    public List<ExecuteResult> execute(Command command) {
        List<ExecuteResult> result = new ArrayList<>();
        result = super.execute(command);
        if (CollectionUtils.isNotEmpty(result)) {
            for (ExecuteResult executeResult : result) {
                if (executeResult.getHeaderList() != null) {
                    for (Header header : executeResult.getHeaderList()) {
                        header.setName(formatTableName(header.getName()));
                    }
                }
            }
        }
        return result;
    }


    /**
     * Execute command
     */
    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n) throws SQLException {
        return super.executeUpdate(sql, connection, n);
    }


    /**
     *
     */
    @Override
    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count)
            throws SQLException {
        return super.execute(sql, connection, limitRowSize, offset, count);
    }

    public static String formatTableName(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return tableName;
        }
        if (tableName.contains(".")) {
            String[] split = tableName.split("\\.");
            return split[1];
        }
        return tableName;
    }
}
