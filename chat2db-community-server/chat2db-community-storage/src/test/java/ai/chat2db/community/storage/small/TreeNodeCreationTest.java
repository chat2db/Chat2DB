package ai.chat2db.community.storage.small;

import ai.chat2db.community.domain.api.enums.NodeTypeEnum;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.model.workspace.Namespace;
import ai.chat2db.community.domain.api.model.workspace.Node;
import ai.chat2db.community.storage.LocalWorkspaceStorage;
import ai.chat2db.community.storage.TestHome;
import ai.chat2db.community.storage.converter.StorageConverterImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TreeNodeCreationTest {
    private final LocalWorkspaceStorage storage = new LocalWorkspaceStorage(new StorageConverterImpl());

    @BeforeAll
    static void isolatedHome() { TestHome.init(); }

    @BeforeEach
    @AfterEach
    void clearFixtures() {
        TreeNodeStorage.INSTANCE.dataMap.clear();
        TreeNodeStorage.INSTANCE.saveDataList();
        NamespaceStorage.INSTANCE.dataMap.clear();
        NamespaceStorage.INSTANCE.saveDataList();
        DataSourceStorage.INSTANCE.dataMap.clear();
        DataSourceStorage.INSTANCE.saveDataList();
    }

    @Test
    void newRootNamespaceIsVisibleInAlreadyPopulatedTree() {
        long parentId = rootGroup();
        Namespace sibling = new Namespace();
        sibling.setName("review sibling");
        long siblingId = storage.createNamespace(sibling);
        assertEquals(List.of(parentId, siblingId), storage.getTree().stream().map(Node::getId).toList());
    }

    @Test
    void newChildNamespaceKeepsRequestedParent() {
        long parentId = rootGroup();
        Namespace child = new Namespace();
        child.setName("review child");
        child.setParentId(parentId);
        long childId = storage.createNamespace(child);
        Node parent = storage.getTree().get(0);
        assertEquals(parentId, parent.getId());
        assertNotNull(parent.getChildren(), "new child must be inserted into the persisted tree");
        assertEquals(List.of(childId), parent.getChildren().stream().map(Node::getId).toList());
    }

    @Test
    void newDatasourceIsVisibleInRequestedGroup() {
        long parentId = rootGroup();
        WorkspaceDataSource datasource = new WorkspaceDataSource();
        datasource.setAlias("review datasource");
        datasource.setSpaceId(parentId);
        long id = storage.createDataSource(datasource);
        assertNotNull(storage.queryDataSourceById(id, false), "datasource record was persisted");
        Node parent = storage.getTree().get(0);
        assertNotNull(parent.getChildren(), "new datasource must be inserted into the persisted tree");
        assertEquals(List.of(id), parent.getChildren().stream().map(Node::getId).toList());
        assertEquals(NodeTypeEnum.DATA_SOURCE.name(), parent.getChildren().get(0).getType());
    }

    @Test
    void childCreationFailsAndRollsBackWhenParentDoesNotExist() {
        Namespace child = new Namespace();
        child.setName("missing parent");
        child.setParentId(999999L);

        assertThrows(RuntimeException.class, () -> storage.createNamespace(child));
        assertTrue(storage.getTree().isEmpty());
        assertTrue(storage.getNamespaceDataSources().getNamespaces().stream()
                .noneMatch(namespace -> "missing parent".equals(namespace.getName())));
    }

    private long rootGroup() {
        Namespace parent = new Namespace();
        parent.setName("review parent");
        long id = storage.createNamespace(parent);
        assertEquals(List.of(id), storage.getTree().stream().map(Node::getId).toList());
        return id;
    }
}
