package ai.chat2db.plugin.sqlserver;

import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SqlServerCommandExecutor extends SQLExecutor {

    /**
     * Execute command
     */
    @Override
    public List<ExecuteResult> execute(Command command) {
        String sql = command.getScript();
        command.setScript(removeSpecialGO(sql));
        return super.execute(command);
    }


    private String removeSpecialGO(String sql) {
        if (StringUtils.isBlank(sql)) {
            return null;
        }
        sql = sql.replaceAll("(?mi)^[ \\t]*go[ \\t]*$", ";");
        return sql;
    }

    /**
     * Execute command
     */
    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n) throws SQLException {
        sql = removeSpecialGO(sql);
        return super.executeUpdate(sql, connection, n);
    }


    /**
     *
     */
    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count)
            throws SQLException {
        return super.execute(removeSpecialGO(sql), connection, limitRowSize, offset, count);
    }
}
