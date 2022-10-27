package com.zjlab;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author xue
 * @create 2022-10-27 9:14
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        // Config
        Config.INSTANCE.loadConfig(args);
        // GPS
        MqttLocation.INSTANCE.init();
        // UdpReceive
        UdpReceive.init();

        ThreadPoolExecutor pool = ThreadPoolUtils.getThreadPool(2, 2, "gps获取");
        pool.execute(new GpsInput());

    }

}
