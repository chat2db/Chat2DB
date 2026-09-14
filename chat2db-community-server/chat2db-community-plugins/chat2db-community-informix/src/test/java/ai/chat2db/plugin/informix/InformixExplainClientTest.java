package ai.chat2db.plugin.informix;

import org.junit.jupiter.api.Test;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.community.domain.api.model.datasource.SSHInfo;
import java.sql.DatabaseMetaData;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InformixExplainClientTest {
    @Test
    void prepareReturnsPlanWithoutExecutionOrClosingSource() throws Exception {
        Fixture fixture = new Fixture(new String[]{"sqx_sqlstatementplan"}, new String[]{"INDEX PATH"});
        assertEquals("INDEX PATH", fixture.client.getExplainInfo(fixture.source, "SELECT * FROM t"));
        assertTrue(fixture.closed.containsAll(List.of("observer", "prepared", "session", "monitor")));
        assertFalse(fixture.closed.contains("source"));
    }

    @Test
    void olderServersReturnEstimatesWithoutRequiringTheNewPlanColumn() throws Exception {
        Fixture fixture = new Fixture(new String[]{"sqx_estcost", "sqx_estrows"}, new String[]{"8", "12"});
        String plan = fixture.client.getExplainInfo(fixture.source, "DELETE FROM t WHERE id=1");
        assertTrue(plan.contains("Estimated Cost: 8"));
        assertTrue(plan.contains("Estimated Rows: 12"));
        assertTrue(plan.contains("unavailable"));
    }

    @Test
    void absentAndDeniedPlansFailWithoutExecutingSqlAndCloseOwnedResources() throws Exception {
        Fixture absent = new Fixture(new String[]{"sqx_estcost"}, null);
        assertThrows(SQLException.class, () -> absent.client.getExplainInfo(absent.source, "UPDATE t SET id=1"));
        assertTrue(absent.closed.containsAll(List.of("prepared", "observer")));
        Fixture denied = new Fixture(new String[]{"sqx_estcost"}, new String[]{"1"});
        denied.queryFailure = new SQLException("Permission denied", "42000");
        SQLException error = assertThrows(SQLException.class,
                () -> denied.client.getExplainInfo(denied.source, "SELECT * FROM t"));
        assertEquals("42000", error.getSQLState());
        assertTrue(denied.closed.containsAll(List.of("prepared", "observer")));
        assertFalse(denied.closed.contains("source"));
    }

    @Test
    void rejectsDdlAndBatchesBeforePreparingAnything() throws Exception {
        Fixture fixture = new Fixture(new String[]{"sqx_estcost"}, new String[]{"1"});
        for (String sql : List.of("DROP TABLE t", "", "SELECT 1; DELETE FROM t", "SET EXPLAIN ON")) {
            assertThrows(SQLException.class, () -> fixture.client.getExplainInfo(fixture.source, sql));
        }
        assertTrue(fixture.closed.isEmpty());
        assertEquals("SELECT ';'", InformixExplainClient.explainSql("/* plan */ EXPLAIN SELECT ';'"));
        assertNull(InformixExplainClient.explainSql("SELECT 'EXPLAIN'"));
    }

    @Test
    void nullMetadataUrlUsesConfiguredAddressAndExistingSshForwarding() throws Exception {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, (proxy, method, args) -> null);
        Connection source = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> metadata);
        ConnectInfo info = new ConnectInfo();
        info.setUrl("jdbc:informix-sqli://db.example:9088/test:INFORMIXSERVER=informix;");
        assertEquals(info.getUrl(), InformixExplainClient.observerUrl(source, info));
        info.setHost("db.example");
        info.setPort(9088);
        SSHInfo ssh = new SSHInfo();
        ssh.setUse(true);
        ssh.setLocalPort("19088");
        info.setSsh(ssh);
        assertEquals("jdbc:informix-sqli://127.0.0.1:19088/test:INFORMIXSERVER=informix;",
                InformixExplainClient.observerUrl(source, info));
        ssh.setLocalPort(null);
        assertThrows(SQLException.class, () -> InformixExplainClient.observerUrl(source, info));
    }

    private static final class Fixture {
        final List<String> closed = new ArrayList<>();
        SQLException queryFailure;
        final Connection source;
        final InformixExplainClient client;

        Fixture(String[] columns, String[] values) throws Exception {
            CachedRowSet plan = rows(columns, values);
            PreparedStatement monitor = proxy(PreparedStatement.class, "monitor", (method, args) -> switch (method) {
                case "setInt" -> { assertEquals(73, args[1]); yield null; }
                case "executeQuery" -> { if (queryFailure != null) throw queryFailure; yield plan; }
                default -> throw new AssertionError("Unexpected monitor call: " + method);
            });
            Connection observer = proxy(Connection.class, "observer", (method, args) -> {
                assertEquals("prepareStatement", method);
                assertTrue(((String) args[0]).contains("sysmaster:syssqexplain"));
                return monitor;
            });
            PreparedStatement prepared = proxy(PreparedStatement.class, "prepared", (method, args) -> {
                assertEquals("getMetaData", method, "User SQL must never be executed");
                return null;
            });
            Statement session = proxy(Statement.class, "session", (method, args) -> {
                assertEquals("executeQuery", method);
                assertTrue(((String) args[0]).contains("DBINFO('sessionid')"));
                return rows(new String[]{"sid"}, new String[]{"73"});
            });
            source = proxy(Connection.class, "source", (method, args) -> switch (method) {
                case "createStatement" -> session;
                case "prepareStatement" -> prepared;
                default -> throw new AssertionError("Unexpected source call: " + method);
            });
            client = new InformixExplainClient() {
                @Override Connection openObserver(Connection ignored) { return observer; }
            };
        }

        <T> T proxy(Class<T> type, String name, Call call) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if ("close".equals(method.getName())) { closed.add(name); return null; }
                return call.invoke(method.getName(), args);
            }));
        }
    }

    private interface Call { Object invoke(String method, Object[] args) throws Throwable; }

    private static CachedRowSet rows(String[] columns, String[] values) throws SQLException {
        CachedRowSet result = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columns.length);
        for (int i = 0; i < columns.length; i++) {
            metadata.setColumnName(i + 1, columns[i]);
            metadata.setColumnLabel(i + 1, columns[i]);
            metadata.setColumnType(i + 1, Types.VARCHAR);
        }
        result.setMetaData(metadata);
        if (values != null) {
            result.moveToInsertRow();
            for (int i = 0; i < values.length; i++) result.updateString(i + 1, values[i]);
            result.insertRow();
            result.moveToCurrentRow();
            result.beforeFirst();
        }
        return result;
    }
}
