package ai.chat2db.plugin.informix.constant;

public final class InformixMetaDataConstants {
    private static final String COLUMN_CONSTRAINTS_TEMPLATE = """
            SELECT k.constrname, k.constrtype
              FROM systables t
              JOIN syscolumns c ON c.tabid = t.tabid
              JOIN sysconstraints k ON k.tabid = t.tabid
             WHERE t.tabname = ? AND t.owner = %s AND c.colname = ?
               AND (EXISTS (SELECT 1 FROM syscoldepend d
                             WHERE d.constrid = k.constrid AND d.colno = c.colno)
                    OR EXISTS (SELECT 1 FROM sysindexes i
                                WHERE i.tabid = t.tabid AND i.idxname = k.idxname
                                  AND c.colno IN (
                                      ABS(i.part1), ABS(i.part2), ABS(i.part3), ABS(i.part4),
                                      ABS(i.part5), ABS(i.part6), ABS(i.part7), ABS(i.part8),
                                      ABS(i.part9), ABS(i.part10), ABS(i.part11), ABS(i.part12),
                                      ABS(i.part13), ABS(i.part14), ABS(i.part15), ABS(i.part16))))
            """;

    public static final String COLUMN_CONSTRAINTS_SQL = COLUMN_CONSTRAINTS_TEMPLATE.formatted("?");
    public static final String CURRENT_USER_COLUMN_CONSTRAINTS_SQL = COLUMN_CONSTRAINTS_TEMPLATE.formatted("USER");

    private InformixMetaDataConstants() {
    }
}
