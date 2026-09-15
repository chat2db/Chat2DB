package ai.chat2db.plugin.informix.builder;

import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.plugin.informix.InformixMetaData;
import ai.chat2db.spi.ISqlBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Table table(String name, List<TableColumn> columns) {
        return Table.builder()
                .name(name)
                .columnList(columns)
                .indexList(List.of())
                .build();
    }
}
