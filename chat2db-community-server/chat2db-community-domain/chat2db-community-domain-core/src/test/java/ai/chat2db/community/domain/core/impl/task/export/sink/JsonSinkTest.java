package ai.chat2db.community.domain.core.impl.task.export.sink;

import ai.chat2db.community.domain.api.model.task.pipeline.ExportSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSinkTest {

    private static final ExportSchema SCHEMA = new ExportSchema(List.of("id", "name"));

    @Test
    void jsonSinkEmitsOneArrayOfRowObjects() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonSink sink = new JsonSink(out);
        sink.writeSchema(SCHEMA, "t");
        sink.writeRows(List.of(row(1, "a"), row(2, null)));
        sink.writeRows(List.of(row(3, "c\r\nline")));
        sink.finishTable("t");
        sink.close();

        assertEquals("""
                [
                {"id":1,"name":"a"},
                {"id":2,"name":null},
                {"id":3,"name":"c\\r\\nline"}
                ]""", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void emptyJsonExportIsAnEmptyArray() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonSink sink = new JsonSink(out);
        sink.writeSchema(SCHEMA, "t");
        sink.finishTable("t");
        sink.close();

        assertEquals("[]", out.toString(StandardCharsets.UTF_8));
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
