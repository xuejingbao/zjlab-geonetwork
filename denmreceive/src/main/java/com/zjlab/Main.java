package com.zjlab;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * @author xue
 * @create 2022-10-27 9:14
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        // Config
        Config.INSTANCE.loadConfig(args);
        // UdpReceive
        UdpReceive.INSTANCE.init();
        // GPS
        GpsService.INSTANCE.init();

    }

}
