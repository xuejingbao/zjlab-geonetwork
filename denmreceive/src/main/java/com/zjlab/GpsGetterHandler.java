package com.zjlab;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author xue
 * @create 2022-11-04 10:38
 */
@Slf4j
@ChannelHandler.Sharable
public class GpsGetterHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        byte[] message = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(message);
        // 申请多线程执行
        channelHandlerContext.executor().parent().execute(() -> {
            String json = new String(message, StandardCharsets.UTF_8);
            List list = GsonUtil.stringToList(json, List.class).get(0);
            Double lon = (double) list.get(0);
            Double lat = (double) list.get(1);
            if (log.isDebugEnabled()) {
                log.debug("获取小车位置信息,经度: {},纬度: {}", lon, lat);
            }
            WarnMessage warnMessage = AreaJudge.INSTANCE.inside(new Position(lat, lon));
            if (warnMessage != null) {
                log.warn("在警告区域内部，警告点是:{},警告半径:{},当前位置:{}"
                        , warnMessage.getWarnPoint(), warnMessage.getWarnRadius(), warnMessage.getNowPoint());
                channelHandlerContext.writeAndFlush(GsonUtil.object2String(Arrays.asList(
                        warnMessage
                )));
            }
        });

    }


}
