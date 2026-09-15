package ai.chat2db.community.storage.small;

import ai.chat2db.community.domain.api.enums.NodeTypeEnum;
import ai.chat2db.community.domain.api.model.workspace.Node;
import ai.chat2db.community.domain.api.model.db.TreeNode;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.PropertyFilter;
import org.apache.commons.collections4.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class TreeNodeStorage extends SmallDataStorage<TreeNode> {
    public static final TreeNodeStorage INSTANCE = new TreeNodeStorage();

    protected TreeNodeStorage() {
        super("tree", TreeNode.class);
    }

    TreeNodeStorage(File storageFile) {
        super(storageFile, TreeNode.class);
    }

    public synchronized List<Node> getNodes() {
        List<TreeNode> treeNodes = getDataList();
        return treeNodes.isEmpty() ? new ArrayList<>() : treeNodes.get(0).getChildren();
    }

    public synchronized void createTree(List<Node> nodes) {
        if (nodes != null) {
            persistTree(copyNodes(nodes));
        }
    }

    private void persistTree(List<Node> nodes) {
        List<TreeNode> treeNodes = getDataList();
        TreeNode replacement = new TreeNode();
        replacement.setId(treeNodes.isEmpty() ? generateId() : treeNodes.get(0).getId());
        replacement.setChildren(nodes);
        Map<Long, TreeNode> persistedData = new TreeMap<>(dataMap);
        persistedData.put(replacement.getId(), replacement);
        saveDataList(new ArrayList<>(persistedData.values()));
        dataMap.put(replacement.getId(), replacement);
    }

    synchronized void insertNode(Node parentNode, Node newNode) {
        if (newNode == null) {
            return;
        }
        List<Node> nodes = getNodes();
        List<Node> updatedNodes = nodes == null ? new ArrayList<>() : copyNodes(nodes);
        if (findNode(updatedNodes, newNode) != null) {
            return;
        }
        if (parentNode == null) {
            updatedNodes.add(newNode);
        } else if (!addNode(updatedNodes, parentNode, newNode, 2)) {
            return;
        }
        createTree(updatedNodes);
    }


    public synchronized ActionResult updatePosition(Node dropToNode, Node dragNode, Integer dropPosition) {
        if (dragNode == null) {
            return ActionResult.isSuccess();
        }
        List<Node> nodes = getNodes();
        if (nodes == null) {
            return ActionResult.isSuccess();
        }
        List<Node> updatedNodes = copyNodes(nodes);
        Node sourceNode = findNode(updatedNodes, dragNode);
        if (sourceNode == null) {
            return ActionResult.isSuccess();
        }
        Node targetNode = findNode(updatedNodes, dropToNode);
        if (dropToNode != null && (targetNode == null || sameNode(sourceNode, targetNode)
                || findNode(sourceNode.getChildren(), targetNode) != null)) {
            return ActionResult.isSuccess();
        }
        removeNode(updatedNodes, sourceNode, false);
        if (targetNode == null) {
            updatedNodes.add(sourceNode);
        } else if (!addNode(updatedNodes, targetNode, sourceNode, dropPosition)) {
            return ActionResult.isSuccess();
        }
        persistTree(updatedNodes);
        return ActionResult.isSuccess();
    }

    private List<Node> copyNodes(List<Node> nodes) {
        PropertyFilter filter = (object, name, value) -> !"data".equals(name);
        String json = JSON.toJSONString(nodes, filter);
        return JSON.parseArray(json, Node.class);
    }

    private Node findNode(List<Node> nodes, Node expected) {
        if (CollectionUtils.isEmpty(nodes) || expected == null) {
            return null;
        }
        for (Node node : nodes) {
            if (sameNode(node, expected)) {
                return node;
            }
            Node child = findNode(node.getChildren(), expected);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private boolean sameNode(Node left, Node right) {
        return left != null && right != null
                && Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getType(), right.getType());
    }

    private boolean removeNode(List<Node> nodes, Node dragNode, boolean deleteChildren) {
        if (CollectionUtils.isEmpty(nodes)) {
            return false;
        }
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (sameNode(node, dragNode)) {
                iterator.remove();
                if (deleteChildren && NodeTypeEnum.NAMESPACE.name().equals(node.getType())
                        && CollectionUtils.isNotEmpty(node.getChildren())) {
                    nodes.addAll(node.getChildren());
                }
                return true;
            }
            if (removeNode(node.getChildren(), dragNode, deleteChildren)) {
                return true;
            }
        }
        return false;
    }

    public synchronized ActionResult deleteNode(Node dragNode) {
        List<Node> nodes = getNodes();
        if (nodes == null || dragNode == null) {
            return ActionResult.isSuccess();
        }
        List<Node> updatedNodes = copyNodes(nodes);
        if (!removeNode(updatedNodes, dragNode, true)) {
            return ActionResult.isSuccess();
        }
        persistTree(updatedNodes);
        return ActionResult.isSuccess();
    }

    private boolean addNode(List<Node> nodes, Node dropToNode, Node dragNode, Integer dropToGap) {
        if (CollectionUtils.isEmpty(nodes)) {
            return false;
        }
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (sameNode(node, dropToNode)) {
                if (dropToGap == 0 || dropToGap == 2) {
                    List<Node> children = node.getChildren();
                    if (children == null) {
                        children = new ArrayList<>();
                        node.setChildren(children);
                    }
                    children.add(dropToGap == 0 ? 0 : children.size(), dragNode);
                } else {
                    nodes.add(dropToGap == 1 ? index + 1 : index, dragNode);
                }
                return true;
            }
            if (addNode(node.getChildren(), dropToNode, dragNode, dropToGap)) {
                return true;
            }
        }
        return false;
    }
}
