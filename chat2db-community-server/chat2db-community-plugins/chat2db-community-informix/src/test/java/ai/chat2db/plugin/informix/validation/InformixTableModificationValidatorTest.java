package ai.chat2db.plugin.informix.validation;

import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.plugin.informix.InformixMetaData;
import ai.chat2db.plugin.informix.metadata.InformixColumnConstraint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InformixTableModificationValidatorTest {
    @Test
    void rejectsEachDestructiveConstraintUsingExplicitConnectionAndOriginalColumn() {
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> { throw new AssertionError("Validator must delegate database access"); });
        TableColumn oldColumn = TableColumn.builder().name("old_col").build();
        TableColumn column = TableColumn.builder().name("new_col").oldColumn(oldColumn).editStatus("MODIFY").build();
        for (String type : List.of("P", "U", "R", "C")) {
            InformixMetaData metadata = new InformixMetaData() {
                @Override
                public List<InformixColumnConstraint> columnConstraints(Connection actual, String owner, String table, String name) {
                    assertSame(connection, actual);
                    assertEquals("other_owner", owner); assertEquals("old_table", table); assertEquals("old_col", name);
                    return List.of(new InformixColumnConstraint("constraint_1", type));
                }
            };
            BusinessException error = assertThrows(BusinessException.class, () ->
                    new InformixTableModificationValidator(metadata).validate(connection,
                            table("other_owner", "old_table", oldColumn), table(null, "new_table", column)));
            assertEquals("informix.column.constraintModification", error.getCode());
            assertArrayEquals(new Object[]{"old_col", "constraint_1"}, error.getArgs());
        }
    }

    @Test
    void allowsNotNullAndSkipsColumnsThatAreNotModified() {
        AtomicInteger queries = new AtomicInteger();
        InformixMetaData metadata = new InformixMetaData() {
            @Override
            public List<InformixColumnConstraint> columnConstraints(Connection connection, String owner, String table, String name) {
                queries.incrementAndGet();
                assertEquals("fallback_owner", owner); assertEquals("old_name", name);
                return List.of(new InformixColumnConstraint("not_null", "N"));
            }
        };
        TableColumn modified = TableColumn.builder().name("renamed").oldName("old_name")
                .schemaName("fallback_owner").editStatus("MODIFY").build();
        Table after = table(null, "t", modified,
                TableColumn.builder().name("added").editStatus("ADD").build(),
                TableColumn.builder().name("dropped").editStatus("DELETE").build(),
                TableColumn.builder().name("unchanged").build());
        new InformixTableModificationValidator(metadata).validate(null, table(null, "t"), after);
        assertEquals(1, queries.get());
    }

    @Test
    void metadataFailurePreventsModification() {
        SQLException failure = new SQLException("Permission denied");
        InformixMetaData metadata = new InformixMetaData() {
            @Override
            public List<InformixColumnConstraint> columnConstraints(Connection c, String owner, String table, String column) throws SQLException {
                throw failure;
            }
        };
        BusinessException error = assertThrows(BusinessException.class, () ->
                new InformixTableModificationValidator(metadata).validate(null, table(null, "t"),
                        table(null, "t", TableColumn.builder().name("c").editStatus("MODIFY").build())));
        assertEquals("informix.column.constraintInspectionFailed", error.getCode());
        assertSame(failure, error.getCause());
    }

    private static Table table(String owner, String name, TableColumn... columns) {
        return Table.builder().schemaName(owner).name(name).columnList(List.of(columns)).build();
    }
}
