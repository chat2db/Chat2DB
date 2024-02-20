package ai.chat2db.spi;

import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Command executor
 * <p>
 * The command executor is used to execute the command.
 * <br>
 */
public interface CommandExecutor {

    /**
     * Execute command
     */
    List<ExecuteResult> execute(Command command);


    /**
     * Execute command
     */
    ExecuteResult executeUpdate(String sql, Connection connection, int n)throws SQLException;


    /**
     *
     *
     */
     ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count, ValueHandler valueHandler)
            throws SQLException;
}
