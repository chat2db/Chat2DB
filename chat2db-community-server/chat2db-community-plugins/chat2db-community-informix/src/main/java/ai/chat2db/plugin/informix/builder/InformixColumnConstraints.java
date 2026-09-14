package ai.chat2db.plugin.informix.builder;

import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class InformixColumnConstraints {
    private static final String INDEX_PARTS = IntStream.rangeClosed(1, 16)
            .mapToObj(i -> "ABS(i.part" + i + ")").collect(Collectors.joining(", "));

    static void check(Table table, TableColumn column) {
        String oldName = column.getOldColumn() == null
                ? StringUtils.defaultIfBlank(column.getOldName(), column.getName()) : column.getOldColumn().getName();
        String owner = StringUtils.defaultIfBlank(table.getSchemaName(), column.getSchemaName());
        // MODIFY drops column constraints and inbound foreign keys, including those
        // referencing a composite PK/UNIQUE. The editor cannot represent them all.
        String sql = "SELECT k.constrname FROM systables t JOIN syscolumns c ON c.tabid = t.tabid "
                + "JOIN sysconstraints k ON k.tabid = t.tabid "
                + "WHERE t.tabname = ? AND t.owner = " + (StringUtils.isBlank(owner) ? "USER" : "?")
                + " AND c.colname = ? AND k.constrtype IN ('P', 'U', 'R', 'C') AND ("
                + "EXISTS (SELECT 1 FROM syscoldepend d WHERE d.constrid = k.constrid AND d.colno = c.colno) "
                + "OR EXISTS (SELECT 1 FROM sysindexes i WHERE i.tabid = t.tabid AND i.idxname = k.idxname "
                + "AND c.colno IN (" + INDEX_PARTS + ")))";
        try {
            Connection connection = Chat2DBContext.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, table.getName());
                if (StringUtils.isNotBlank(owner)) {
                    statement.setString(index++, owner);
                }
                statement.setString(index, oldName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        throw new BusinessException("informix.column.constraintModification",
                                new Object[]{oldName, result.getString(1).trim()});
                    }
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("informix.column.constraintInspectionFailed", null, e);
        }
    }
}
