import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline MySQL grammar validation; opens no network connection. */
class ParseSql {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            var statements = SQLUtils.parseStatements(Files.readString(Path.of(name)), DbType.mysql);
            if (statements.isEmpty()) throw new IllegalStateException("No statements: " + name);
            System.out.println(Path.of(name).getFileName() + ": " + statements.size() + " MySQL statements parsed");
        }
    }
}
