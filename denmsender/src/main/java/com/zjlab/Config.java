package com.zjlab;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * @author xue
 * @create 2022-10-26 15:28
 */
@Slf4j
@Getter
public enum Config {
    /**
     * 单例
     */
    INSTANCE;

    public final static String ADDRESS = "127.0.0.1";
    public final static Integer PORT = 5000;
    public final static Integer LOCAL_PORT = 6666;

    private Double latitude;
    private Double longitude;
    private Integer radius;

    public Config loadConfig(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            String argItem = args[i];
            if ("--lat".equals(argItem)) {
                i++;
                this.latitude = Double.parseDouble(args[i]);
            } else if ("--lon".equals(argItem)) {
                i++;
                this.longitude = Double.parseDouble(args[i]);
            } else if ("--rad".equals(argItem)) {
                i++;
                this.radius = Integer.parseInt(args[i]);
            }
        }
        if (latitude == null || longitude == null || radius == null) {
            throw new RuntimeException("请配置警告中心点经纬度及警告半径！");
        }
        log.info("配置信息读取完成：" +
                "\n经度：{}" +
                "\n纬度：{}" +
                "\n警告半径：{}", longitude, latitude, radius
        );
        return this;
    }

}