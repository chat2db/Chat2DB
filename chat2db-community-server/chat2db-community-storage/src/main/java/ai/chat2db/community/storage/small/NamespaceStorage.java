package ai.chat2db.community.storage.small;

import ai.chat2db.community.domain.api.enums.NodeTypeEnum;
import ai.chat2db.community.domain.api.model.workspace.Namespace;
import ai.chat2db.community.domain.api.model.workspace.Node;
import org.apache.commons.collections4.CollectionUtils;
import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.List;

public class NamespaceStorage extends SmallDataStorage<Namespace> {

    public static final NamespaceStorage INSTANCE = new NamespaceStorage();

    protected NamespaceStorage() {
        super("namespace", Namespace.class);
    }


    public synchronized void deleteDataSourcePosition(Long dataSourceId) {
        List<Namespace> candidate = copyNamespaces();
        for (Namespace namespace : candidate) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (!CollectionUtils.isEmpty(dataSourceIds)) {
                if (dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.remove(dataSourceId);
                }
            }
        }
        persistNamespaces(candidate);
    }

    public synchronized void updateDataSourcePosition(Long namespaceId, Long dataSourceId) {
        List<Namespace> candidate = copyNamespaces();
        for (Namespace namespace : candidate) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (!CollectionUtils.isEmpty(dataSourceIds)) {
                if (dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.remove(dataSourceId);
                }
            }
        }
        Namespace namespace = candidate.stream().filter(item -> namespaceId != null && namespaceId.equals(item.getId())).findFirst().orElse(null);
        if (namespace != null) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (CollectionUtils.isEmpty(dataSourceIds)) {
                dataSourceIds = new ArrayList<>();
                dataSourceIds.add(dataSourceId);
                namespace.setDatasourceIds(dataSourceIds);
            } else {
                if (!dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.add(dataSourceId);
                }
            }
        }
        persistNamespaces(candidate);
    }

    public synchronized Long save(Namespace namespace){
        Long id = super.save(namespace);
        Node dropToNode = null;
        if (namespace.getParentId() != null) {
            dropToNode = new Node();
            dropToNode.setId(namespace.getParentId());
            dropToNode.setType(NodeTypeEnum.NAMESPACE.name());
        }
        Node node = new Node();
        node.setId(namespace.getId());
        node.setType(NodeTypeEnum.NAMESPACE.name());
        try {
            if (!TreeNodeStorage.INSTANCE.insertNode(dropToNode, node)) {
                throw new IllegalStateException("Parent namespace does not exist");
            }
        } catch (RuntimeException exception) {
            try {
                super.delete(id);
            } catch (RuntimeException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
        return id;
    }

    public synchronized void delete(Long id) {
        List<Node> treeBefore = TreeNodeStorage.INSTANCE.snapshotNodes();
        try {
            TreeNodeStorage.INSTANCE.deleteNode(Node.builder().id(id).type(NodeTypeEnum.NAMESPACE.name()).build());
            super.delete(id);
        } catch (RuntimeException exception) {
            try {
                TreeNodeStorage.INSTANCE.restoreNodes(treeBefore);
            } catch (RuntimeException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
    }

    synchronized List<Namespace> snapshotNamespaces() {
        return copyNamespaces();
    }

    synchronized void restoreNamespaces(List<Namespace> namespaces) {
        persistNamespaces(namespaces == null ? new ArrayList<>() : namespaces);
    }

    private List<Namespace> copyNamespaces() {
        return JSON.parseArray(JSON.toJSONString(getDataList()), Namespace.class);
    }

    private void persistNamespaces(List<Namespace> namespaces) {
        saveDataList(namespaces);
        dataMap.clear();
        for (Namespace namespace : namespaces) {
            dataMap.put(namespace.getId(), namespace);
        }
    }
}
