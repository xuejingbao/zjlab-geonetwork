package com.zjlab;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

/**
 * @author xue
 * @create 2022-10-27 9:39
 */
@Slf4j
@ChannelHandler.Sharable
public class DenmHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
        ByteBuf data = datagramPacket.content();
        int length = data.readableBytes();
        if (length == 107) {
            int latitude = data.getInt(40);
            int longitude = data.getInt(44);
            int radius = data.getShort(48);
            log.info("收到警告信息：" +
                    "\n经度: {}" +
                    "\n纬度: {}" +
                    "\n半径: {}", longitude, latitude, radius);
            AreaJudge.INSTANCE.changeArea(new CircularAreaJudge(radius, longitude / 1e7, latitude / 1e7));
        }
    }

}
