package com.zjlab;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xue
 * @create 2022-10-26 16:49
 */
@Slf4j
public class UdpSender {

    private static EventLoopGroup group;
    private static Channel channel;

    public static void init() throws InterruptedException {
        group = new NioEventLoopGroup(2, new ThreadFactory() {
            private AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "SimpleUDP-" + count.getAndAdd(1));
            }
        });
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                // 主线程处理
                .channel(NioDatagramChannel.class)
                // 广播
                .option(ChannelOption.SO_BROADCAST, true)
                // 设置udp单帧超过2M的办法
                //.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
                // 设置读缓冲区为2M
                .option(ChannelOption.SO_RCVBUF, 2048 * 1024)
                // 设置写缓冲区为1M
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .handler(new ChannelInitializer<NioDatagramChannel>() {

                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                log.info("udp接收信息：{}", ByteBufUtil.hexDump(msg.content()));
                            }
                        });
                    }
                });

        ChannelFuture f = bootstrap.bind(Config.LOCAL_PORT).sync();
        channel = f.channel();
        log.info("SimpleUDP发送端准备就绪...");
        f.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                log.info("SimpleUDP发送端已关闭...");
            }
        });
    }

    public static void sendMessage(byte[] messageBytes) {
        if (channel.isActive()) {
            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
            byteBuf.writeBytes(messageBytes);
            InetSocketAddress address = new InetSocketAddress(Config.ADDRESS, Config.PORT);
            if (log.isDebugEnabled()) {
                log.debug("发送信息:{}", ByteBufUtil.hexDump(byteBuf));
            }
            channel.writeAndFlush(new DatagramPacket(byteBuf, address));
        }
    }


    public static void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

}
