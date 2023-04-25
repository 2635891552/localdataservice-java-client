package com.scapeak.client;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 读取节点
 *
 * @author sooyaaa
 * @createTime 2023/4/25
 */
public class ReadNodeClient {
    // 设备节点前缀
    private String deviceNopePrefix;

    // 要读取的设备节点
    public List<String> deviceNodeIds;

    public Map<String, Object> readValue(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
        if (deviceNopePrefix.isEmpty()) {
            throw new RuntimeException("设备节点前缀不能为空");
        }
        if (!deviceNopePrefix.endsWith(".")) {
            deviceNopePrefix = deviceNopePrefix + ".";
        }
        // 获取连接
        client.connect().get();
        Map<String, Object> readValueMap = new HashMap<>();
        if (!deviceNodeIds.isEmpty()) {
            for (String nodeName : deviceNodeIds) {
                NodeId node = new NodeId(1, deviceNopePrefix + nodeName);
                DataValue value2 = client.readValue(0.0, TimestampsToReturn.Both, node).get();
                readValueMap.put(node.getIdentifier().toString(), value2.getValue().getValue());
            }
        } else {
            // 如果节点列表为空，那么读取该设备下的所有节点
            List<? extends UaNode> nodes = client.getAddressSpace().browseNodes(new NodeId(1, deviceNopePrefix));
            if (nodes.isEmpty()) {
                throw new RuntimeException("没有找到:" + deviceNodeIds + "下的节点");
            }
            for (UaNode uaNode : nodes) {
                NodeId node = uaNode.getNodeId();
                if (uaNode.getNodeClass() == NodeClass.Variable) {
                    DataValue value2 = client.readValue(0.0, TimestampsToReturn.Both, node).get();
                    readValueMap.put(node.getIdentifier().toString(), value2.getValue().getValue());
                }
            }
        }
        return readValueMap;
    }
}
