package com.zjlab;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author xue
 * @create 2022-10-27 9:27
 */
@Slf4j
public enum MqttLocation {

    /**
     * 单例
     */
    INSTANCE;

    private final LinkedBlockingQueue<Position> positionQueue = new LinkedBlockingQueue<>(100);

    private static final Integer CONNECTION_TIMEOUT = 10;
    private static final Integer KEEPALIVE_INTERVAL = 20;
    private static final Integer QOS = 1;

    public void init() {
        try {
            MqttClient mqttClient = new MqttClient(Config.INSTANCE.getHost(), Config.INSTANCE.getClientId(), new MemoryPersistence());
            // MQTT的连接设置
            MqttConnectOptions options = new MqttConnectOptions();
            // 设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(true);
            // 设置连接的用户名
            options.setUserName(Config.INSTANCE.getUserName());
            // 设置连接的密码
            options.setPassword(Config.INSTANCE.getPassword().toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(KEEPALIVE_INTERVAL);
            // 设置回调函数

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.info("connectionLost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String msg = new String(message.getPayload());
                    JSONObject json = JSON.parseObject(msg);

                    positionQueue.put(new Position(json.getDouble("lat"), json.getDouble("lon")));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.info("deliveryComplete---------{}", token.isComplete());
                }

            });

            mqttClient.connect(options);
            //订阅消息
            mqttClient.subscribe(Config.INSTANCE.getTopic(), QOS);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public Position getPosition() throws InterruptedException {
        return positionQueue.take();
    }


}
