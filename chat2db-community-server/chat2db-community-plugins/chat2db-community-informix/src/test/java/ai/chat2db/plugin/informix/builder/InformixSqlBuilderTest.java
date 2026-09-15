package ai.chat2db.plugin.informix.builder;

import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.plugin.informix.InformixMetaData;
import ai.chat2db.spi.ISqlBuilder;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InformixSqlBuilderTest {

    @Test
    void metadataReturnsInformixSqlBuilder() {
        ISqlBuilder builder = new InformixMetaData().getSqlBuilder();

        assertInstanceOf(InformixSqlBuilder.class, builder);
    }

    @Test
    void buildAlterTableUsesRenameTableSyntaxWhenNameChanges() {
        InformixSqlBuilder builder = new InformixSqlBuilder();
        Table oldTable = table("orders", List.of());
        Table newTable = table("orders_archive", List.of());

        assertEquals("RENAME TABLE orders TO orders_archive;\n", builder.buildAlterTable(oldTable, newTable));
    }

    @Test
    void modifyDefinitionPreservesSeparateDimensionsDefaultAndNullability() {
        TableColumn column = new TableColumn();
        column.setColumnType("VARCHAR");
        column.setColumnSize(32);
        column.setDefaultValue("'ready'");
        column.setNullable(0);
        assertEquals("VARCHAR(32) DEFAULT 'ready' NOT NULL", InformixSqlBuilder.columnDefinition(column));
        column.setColumnType("DECIMAL");
        column.setColumnSize(8);
        column.setDecimalDigits(2);
        column.setDefaultValue("0");
        assertEquals("DECIMAL(8,2) DEFAULT 0 NOT NULL", InformixSqlBuilder.columnDefinition(column));
    }

    @Test
    void removingAttributesDoesNotReintroduceOldDefaultsOrNotNull() {
        TableColumn oldColumn = new TableColumn();
        oldColumn.setDefaultValue("1");
        oldColumn.setNullable(0);
        TableColumn column = new TableColumn();
        column.setColumnType("INTEGER");
        column.setColumnSize(10);
        column.setOldColumn(oldColumn);
        column.setNullable(1);
        assertEquals("INTEGER", InformixSqlBuilder.columnDefinition(column));
    }

    @Test
    void explainIsRoutedToExecutorWithoutEnablingSqlExecution() {
        InformixSqlBuilder builder = new InformixSqlBuilder();
        assertEquals("EXPLAIN DELETE FROM orders", builder.buildExplain("DELETE FROM orders"));
        assertEquals("/* test */ EXPLAIN SELECT 1", builder.buildExplain("/* test */ EXPLAIN SELECT 1"));
    }

    @Test
    void renameQualifiesAndEscapesOwnerAndDelimitedTableNames() {
        Table before = table("order.items", List.of());
        before.setSchemaName("team'o");
        Table after = table("order\"archive", List.of());
        after.setSchemaName("team'o");
        assertEquals("RENAME TABLE 'team''o'.\"order.items\" TO \"order\"\"archive\";\n",
                new InformixSqlBuilder().buildAlterTable(before, after));
    }

    @Test
    void typeAliasesKeepLengthAndDoNotDefaultToOneCharacter() {
        for (String type : List.of("character varying", "CHARACTER   VARYING", " char varying ")) {
            TableColumn column = new TableColumn();
            column.setColumnType(type);
            column.setColumnSize(64);
            column.setNullable(0);
            column.setDefaultValue("'four'");
            assertEquals("VARCHAR(64) DEFAULT 'four' NOT NULL", InformixSqlBuilder.columnDefinition(column));
        }
    }

    @Test
    void buildingModifySqlInspectsConstraintsAndKeepsConnectionOpen() throws Exception {
        TableColumn original = new TableColumn();
        original.setName("qty"); original.setColumnType("SMALLINT"); original.setNullable(0);
        TableColumn changed = new TableColumn();
        changed.setName("qty"); changed.setColumnType("INTEGER"); changed.setNullable(0);
        changed.setDefaultValue("1"); changed.setEditStatus("MODIFY"); changed.setOldColumn(original);
        Table before = table("orders", List.of(original));
        before.setSchemaName("other_owner");
        boolean[] constrained = {false};
        Map<Integer, String> parameters = new HashMap<>();
        ResultSet constraints = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "next" -> { boolean next = constrained[0]; constrained[0] = false; yield next; }
                        case "getString" -> Integer.valueOf(2).equals(args[0]) ? "C" : "qty_check";
                        case "close" -> null;
                        default -> throw new AssertionError(method.getName());
                    };
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "setString" -> { parameters.put((Integer) args[0], (String) args[1]); yield null; }
                        case "executeQuery" -> constraints;
                        case "close" -> null;
                        default -> throw new AssertionError(method.getName());
                    };
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isClosed" -> false;
                        case "prepareStatement" -> statement;
                        default -> throw new AssertionError("Unexpected connection operation: " + method.getName());
                    };
                });
        ConnectInfo info = new ConnectInfo();
        info.setDbType("INFORMIX");
        info.setDriverConfig(new ai.chat2db.community.domain.api.config.DriverConfig());
        info.setConnection(connection);
        Chat2DBContext.putContext(info);
        try {
            assertEquals("ALTER TABLE 'other_owner'.orders MODIFY (qty INTEGER DEFAULT 1 NOT NULL);\n",
                    new InformixSqlBuilder().buildAlterTable(before, table("orders", List.of(changed))));
            assertEquals(Map.of(1, "orders", 2, "other_owner", 3, "qty"), parameters);
            constrained[0] = true;
            assertThrows(BusinessException.class,
                    () -> new InformixSqlBuilder().buildAlterTable(before, table("orders", List.of(changed))));
        } finally {
            info.setConnection(null);
            Chat2DBContext.removeContext();
        }
    }

    private static Table table(String name, List<TableColumn> columns) {
        return Table.builder()
                .name(name)
                .columnList(columns)
                .indexList(List.of())
                .build();
    }
}
