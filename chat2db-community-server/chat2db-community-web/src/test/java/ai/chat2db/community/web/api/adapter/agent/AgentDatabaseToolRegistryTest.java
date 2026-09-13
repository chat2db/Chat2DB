package ai.chat2db.community.web.api.adapter.agent;

import ai.chat2db.community.domain.api.model.request.agent.DbAgentDatabaseRequest.*;
import ai.chat2db.community.domain.api.model.response.agent.DbAgentDatabaseResponse;
import ai.chat2db.community.domain.api.service.agent.AgentDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentDatabaseToolRegistryTest {
    @Test
    void exposesIndependentSchemasAndRejectsLegacyOrCoercedArguments() {
        AtomicReference<Object> input = new AtomicReference<>();
        var registry = registry(input, DbAgentDatabaseResponse.success(null, List.of(), null, null, List.of()));
        assertEquals(Set.of("db_search_datasources", "db_search_databases", "db_search_schemas", "db_search_tables", "db_search_columns", "db_describe_objects", "db_query"), registry.names());
        var query = registry.definitions().stream().filter(t -> t.name().equals("db_query")).findFirst().orElseThrow();
        assertEquals(List.of("description", "dataSourceId", "sql"), query.parameters().get("required"));
        assertEquals(false, query.parameters().get("additionalProperties"));
        assertFalse(query.promptGuidelines().isEmpty());
        assertFalse(query.promptSnippet().isBlank());
        var fields = (Map<?, ?>) query.parameters().get("properties");
        assertTrue(((Map<?, ?>) fields.get("description")).get("description").toString().contains("what you are doing"));
        assertTrue(((Map<?, ?>) fields.get("sql")).get("description").toString().contains("complete SQL batch"));
        assertFalse(((Map<?, ?>) fields.get("sql")).get("description").toString().contains("no writes"));
        assertTrue(((Map<?, ?>) fields.get("database")).containsKey("anyOf"));
        assertFalse(((Map<?, ?>) fields.get("dataSourceId")).containsKey("anyOf"));
        assertFalse(registry.execute("execute_sql", Map.of("sql", "SELECT 1")).ok());
        var legacy = registry.execute("db_query", Map.of("dataSourceId", "7", "sql", "SELECT 1", "databaseName", "app"));
        assertEquals("databaseName", legacy.error().field());
        assertFalse(registry.execute("db_query", Map.of("dataSourceId", 7, "sql", "SELECT 1")).ok());
        assertFalse(registry.execute("db_query", Map.of("dataSourceId", "7", "sql", "SELECT 1", "pageSize", "100")).ok());
        assertNull(input.get());
        assertTrue(registry.execute("db_query", Map.of("dataSourceId", "7", "database", "app", "sql", "SELECT 1", "pageSize", 100)).ok());
        assertEquals(100, ((Query)input.get()).pageSize());
        Map<String, Object> nullable = new HashMap<>(); nullable.put("dataSourceId", "7"); nullable.put("sql", "SELECT 1");
        nullable.put("database", null); nullable.put("schema", null);
        assertTrue(registry.execute("db_query", nullable).ok());
    }

    @Test
    void forwardsLargeResultsWithoutRetryingOrLosingTheOriginalOutcome() {
        String body = "x".repeat(600000);
        var expected = DbAgentDatabaseResponse.success(null, body, null, null, List.of());
        var registry = registry(new AtomicReference<>(), expected);
        var result = registry.execute("db_query", Map.of("dataSourceId", "7", "sql", "SELECT body FROM samples", "pageSize", 100));
        assertSame(expected, result);
        assertTrue(result.ok());
        assertEquals(body, result.data());
        assertNull(result.nextAction());
    }
    @Test
    void objectDefinitionsUseTypedNamesWithinAnExplicitSharedScope() {
        AtomicReference<Object> input = new AtomicReference<>();
        var registry = registry(input, DbAgentDatabaseResponse.success(null, List.of(), null, null, List.of()));
        var args = Map.<String, Object>of("dataSourceId", "7", "database", "app", "schema", "public",
                "objects", List.of(Map.of("type", "VIEW", "name", "active_users"), Map.of("type", "TRIGGER", "name", "after_insert")));
        assertTrue(registry.execute("db_describe_objects", args).ok());
        assertEquals(new Describe("7", "app", "public", List.of(new ObjectRef("VIEW", "active_users"), new ObjectRef("TRIGGER", "after_insert")), null), input.get());
        var nestedUnknown = new HashMap<>(args);
        nestedUnknown.put("objects", List.of(Map.of("type", "VIEW", "name", "active_users", "database", "other")));
        assertEquals("INVALID_ARGUMENT", registry.execute("db_describe_objects", nestedUnknown).error().code());
        var definition = registry.definitions().stream().filter(t -> t.name().equals("db_describe_objects")).findFirst().orElseThrow();
        assertEquals(List.of("description", "dataSourceId", "objects"), definition.parameters().get("required"));
    }

    private AgentDatabaseToolRegistry registry(AtomicReference<Object> input, DbAgentDatabaseResponse<?> result) {
        var service = (AgentDatabaseService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AgentDatabaseService.class},
                (p,m,a) -> { input.set(a[0]); return result; });
        return new AgentDatabaseToolRegistry(service);
    }
}
