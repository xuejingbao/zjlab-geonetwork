package com.zjlab;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author xue
 * @create 2022-10-27 9:18
 */
@Slf4j
@Getter
public enum Config {
    /**
     * 单例
     */
    INSTANCE;

    /**
     * gps相关配置
     */
    private String myAddress;
    private Integer gpsReceivePort;

    /**
     * 监听端口
     */
    private Integer receivePort;


    public Config loadConfig(String[] args) throws IOException {
        InputStream configInput = Config.class.getClassLoader().getResourceAsStream("car.properties");
        Properties configPro = new Properties();
        configPro.load(configInput);
        this.myAddress = configPro.getProperty("myAddress");
        this.gpsReceivePort = Integer.parseInt(configPro.getProperty("gpsReceivePort"));
        this.receivePort = Integer.parseInt(configPro.getProperty("receivePort"));

        for (int i = 0; i < args.length; i++) {
            String argItem = args[i];
            if ("--recport".equals(argItem)) {
                i++;
                this.receivePort = Integer.parseInt(args[i]);
            } else if ("--gpsport".equals(argItem)) {
                i++;
                this.gpsReceivePort = Integer.parseInt(args[i]);
            } else if ("--myaddr".equals(argItem)) {
                i++;
                this.myAddress = args[i];
            }
        }
        log.info("配置信息读取完成：" +
                "\nmyAddress: {}" +
                "\ngpsReceivePort: {}" +
                "\n接收端口：{}", myAddress, gpsReceivePort, receivePort
        );
        return this;
    }


}
