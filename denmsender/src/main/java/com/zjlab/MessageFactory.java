package com.zjlab;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * @author xue
 * @create 2022-10-26 15:34
 */
@Slf4j
public class MessageFactory {

    public static SimpleCam getSimpleCam() {
        return new SimpleCam(
                101,
                getGenerationDeltaTime(),
                (byte) 128,
                5,
                (int) (Config.INSTANCE.getLatitude() * 1e7),
                (int) (Config.INSTANCE.getLongitude() * 1e7),
                0,
                0,
                0,
                400,
                0,
                1,
                0,
                1,
                40,
                20,
                159,
                1,
                2,
                1,
                0
        );
    }

    public static SimpleDenm getSimpleDenm() {
        return new SimpleDenm(
                101,
                getGenerationDeltaTime(),
                (byte) 160,
                (byte) 64,
                1,
                2,
                0,
                (int) (Config.INSTANCE.getLatitude() * 1e7),
                (int) (Config.INSTANCE.getLatitude() * 1e7),
                Config.INSTANCE.getRadius(),
                Config.INSTANCE.getRadius(),
                2,
                3,
                0,
                0,
                0,
                1,
                5,
                (byte) 128,
                4,
                2,
                2,
                0,
                0,
                (byte) 8,
                0,
                0,
                5
        );
    }

    /**
     * 时间获取
     *
     * @return
     */
    private static int getGenerationDeltaTime() {
        Instant instant = Instant.now();
        long generationDeltaTime = (instant.getEpochSecond() * 1000 +
                instant.getNano() / 1000000) % 65536;
        return (int) generationDeltaTime;
    }

}
