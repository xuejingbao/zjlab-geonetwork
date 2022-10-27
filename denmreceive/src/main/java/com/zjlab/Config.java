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
     * mqtt相关配置
     */
    private String host;
    private String topic;
    private String clientId;
    private String userName;
    private String password;

    /**
     * 监听端口
     */
    private Integer receivePort;


    public Config loadConfig(String[] args) throws IOException {
        InputStream configInput = Config.class.getClassLoader().getResourceAsStream("car.properties");
        Properties configPro = new Properties();
        configPro.load(configInput);
        this.host = configPro.getProperty("host");
        this.topic = configPro.getProperty("topic");
        this.clientId = configPro.getProperty("clientId");
        this.userName = configPro.getProperty("userName");
        this.password = configPro.getProperty("password");
        this.receivePort = Integer.parseInt(configPro.getProperty("receivePort"));

        for (int i = 0; i < args.length; i++) {
            String argItem = args[i];
            if ("--recport".equals(argItem)) {
                i++;
                this.receivePort = Integer.parseInt(args[i]);
            }
        }
        log.info("配置信息读取完成：" +
                "\n接收端口：{}", receivePort
        );
        return this;
    }



}
