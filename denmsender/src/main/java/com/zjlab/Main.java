package com.zjlab;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xue
 * @create 2022-10-26 15:27
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        Config.INSTANCE.loadConfig(args);
        UdpSender.init();

        ScheduledThreadPoolExecutor pool = ThreadPoolUtils.getScheduledPool(2, "udp发送");
        pool.scheduleAtFixedRate(() -> {
            UdpSender.sendMessage(MessageFactory.getSimpleCam().asByteArray());
            UdpSender.sendMessage(MessageFactory.getSimpleDenm().asByteArray());
        }, 0, 1, TimeUnit.SECONDS);


    }

}
