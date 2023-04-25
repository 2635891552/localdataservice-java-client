package com.scapeak.client;

import com.alibaba.fastjson2.JSONObject;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * @author sooyaaa
 * @createTime 2023/4/25
 */
public class DataClient {
    // 要读取的设备节点
    public List<String> deviceNodeIds;

    // OPCUA客户端
    private OpcUaClient client;

    private CompletableFuture<OpcUaClient> future;

    public DataClient(String opcuaUrl,String deviceNodePrefix, List<String> deviceNodeIds) throws Exception {
        if (deviceNodePrefix == null || deviceNodePrefix.isEmpty()) {
            throw new RuntimeException("设备前缀不能为空");
        }
        if (!deviceNodePrefix.endsWith(".")) {
            deviceNodePrefix = deviceNodePrefix + ".";
        }
        // 如果节点列表为空，那么读取该设备下的所有节点
        if (deviceNodeIds.isEmpty()) {
            List<? extends UaNode> nodes = client.getAddressSpace().browseNodes(new NodeId(1, deviceNodePrefix));
            if (nodes.isEmpty()) {
                throw new RuntimeException("没有找到:" + deviceNodeIds + "下的节点");
            }
            for (UaNode node : nodes) {
                deviceNodeIds.add(node.getNodeId().getIdentifier().toString());
            }
        } else {
            ListIterator<String> iterator = deviceNodeIds.listIterator();
            while (iterator.hasNext()) {
                String nodeId = iterator.next();
                nodeId = deviceNodePrefix + nodeId;
                iterator.set(nodeId);
            }
        }
        ClientRunner clientRunner = new ClientRunner(opcuaUrl);
        this.deviceNodeIds = deviceNodeIds;
        this.client = clientRunner.getClient();
        this.future = clientRunner.getFuture();
    }

    /**
     * @return key:变量名,value:value
     * @throws Exception
     */
    public Map<String, Object> readValue() throws Exception {
        client.connect().get();
        Map<String, Object> readValueMap = new HashMap<>();
        for (String node : deviceNodeIds) {
            NodeId nodeId = new NodeId(1, node);
            DataValue value = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get();
            // 去掉.前面的内容,只保留变量名
            String itemName = node.substring(node.lastIndexOf(".") + 1);
            readValueMap.put(itemName, value.getValue().getValue());
        }
        future.complete(client);
        return readValueMap;
    }

    void subscribeValue(RabbitTemplate rabbitTemplate, String exchangeName, String routeKey) throws ExecutionException, InterruptedException {
        client.connect().get();
        // 创建监控项请求，该请求最后用于创建订阅
        UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0).get();

        // 创建要监控的变量列表
        List<MonitoredItemCreateRequest> requests = new ArrayList<>();

        // 用来标识每个创建的监控项，如果有多个要监控的变量这个值必须唯一
        AtomicInteger atomicInteger = new AtomicInteger(0);
        for (String node : deviceNodeIds) {
            MonitoringParameters parameters = new MonitoringParameters(
                    uint(atomicInteger.getAndIncrement()),
                    100.0,     // sampling interval
                    null,       // filter, null means use default
                    uint(10),   // queue size
                    true        // discard oldest
            );
            ReadValueId readValueId = new ReadValueId(
                    new NodeId(1, node),
                    AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
            );
            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);
            requests.add(request);
        }

        // 创建监控项，并且作为变量值改变时候的回调函数。
        subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                requests,
                (item, id) -> {
                    item.setValueConsumer((item1, value) -> {
                        String itemName = item1.toString().substring(item1.toString().lastIndexOf(".") + 1);
                        Object itemValue = value.getValue().getValue();
                        rabbitTemplate.convertAndSend(exchangeName, routeKey, Objects.requireNonNull(new JSONObject().put(itemName, itemValue)));
                    });
                }
        ).get();
        // 一直订阅
        Thread.sleep(Long.MAX_VALUE);
    }
}
