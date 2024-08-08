package ai.chat2db.server.domain.core.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import org.h2.api.Trigger;

public class H2Triggers implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName,
        String tableName, boolean before, int type) throws SQLException {
        // Initialization logic, if needed.
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow)
        throws SQLException {
        // This method is called when the trigger is executed.
        // In this example, let's simply print the new values when a row is inserted.
        if (newRow != null) {
//            System.out.println("New Row Inserted: " + Arrays.toString(newRow));
        }
    }

    @Override
    public void close() throws SQLException {
        // Cleanup logic, if needed.
    }

    @Override
    public void remove() throws SQLException {
        // Logic to execute when the trigger is dropped/removed.
    }
}
