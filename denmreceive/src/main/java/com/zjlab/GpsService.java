package com.zjlab;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author xue
 * @create 2022-11-04 10:37
 */
@Slf4j
public enum GpsService {

    /**
     * 单例
     */
    INSTANCE;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;

    private static Channel channel;

    public void init() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workGroup = new NioEventLoopGroup(3);
        ServerBootstrap bootstrap = new ServerBootstrap();
        GpsGetterHandler gpsGetterHandler = new GpsGetterHandler();
        bootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();
                        pipeline.addLast("GPS工作线程", gpsGetterHandler);
                    }
                });
        ChannelFuture channelFuture = bootstrap.bind(Config.INSTANCE.getMyAddress(), Config.INSTANCE.getGpsReceivePort()).sync();
        log.info("GPSServer启动成功,IP是：{}，端口是：{}", Config.INSTANCE.getMyAddress(), Config.INSTANCE.getGpsReceivePort());
        channel = channelFuture.channel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                log.info("GPSServer已关闭,IP是：{}，端口是：{}", Config.INSTANCE.getMyAddress(), Config.INSTANCE.getGpsReceivePort());
            }
        });
    }

    /**
     * 发送消息
     *
     * @return
     */
    public static Boolean send() {
        if (channel != null && channel.isActive()) {
            return true;
        }
        return false;
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }


}
