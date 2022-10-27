package com.zjlab;

import lombok.extern.slf4j.Slf4j;

/**
 * @author xue
 * @create 2022-10-27 14:04
 */
@Slf4j
public class GpsInput implements Runnable {

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Position position = MqttLocation.INSTANCE.getPosition();
                if (log.isDebugEnabled()) {
                    log.debug("获取小车位置信息,经度: {},纬度: {}", position.getLon(), position.getLat());
                }
                WarnMessage warnMessage = AreaJudge.INSTANCE.inside(position);
                if (warnMessage != null) {
                    log.warn("在警告区域内部，警告点是:{},警告半径:{},当前位置:{}"
                            , warnMessage.getWarnPoint(), warnMessage.getWarnRadius(), warnMessage.getNowPoint());
                }
            } catch (InterruptedException e) {
                log.error("gps获取线程被打断", e);
                Thread.currentThread().interrupt();
            }
        }
    }


}
