package com.jzy.game.engine.netty;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jzy.game.engine.mina.MinaTcpClient;
import com.jzy.game.engine.netty.code.DefaultClientChannelInitializer;
import com.jzy.game.engine.netty.config.NettyClientConfig;
import com.jzy.game.engine.netty.service.NettyClientService;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Netty Tcp 客户端
 * 
 * @see MinaTcpClient
 * @author JiangZhiYong
 * @QQ 359135103 2017年8月24日 下午8:13:14
 */
public class NettyTcpClient implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(NettyTcpClient.class);

	/** 配置 */
	private NettyClientConfig nettyClientConfig;
	/** 工作组 */
	private EventLoopGroup group;
	/** 初始化channel */
	private ChannelInitializer<SocketChannel> channelInitializer;
	/** 服务 */
	private NettyClientService service;

	private Bootstrap bootstrap;
	/** 连接 */
	private Map<String, Channel> channels = new ConcurrentHashMap<>(10);

	/**
	 * 
	 */
	public NettyTcpClient(NettyClientService nettyClientService) {
		this.nettyClientConfig = nettyClientService.getNettyClientConfig();
		this.channelInitializer = new DefaultClientChannelInitializer(nettyClientService);
	}

	/**
	 * 使用本地默认service配置
	 * 
	 * @param nettyClientService
	 * @param channelInitializer
	 */
	public NettyTcpClient(NettyClientService nettyClientService, ChannelInitializer<SocketChannel> channelInitializer) {
		this.nettyClientConfig = nettyClientService.getNettyClientConfig();
		this.service = nettyClientService;
		this.channelInitializer = channelInitializer;
	}

	public NettyTcpClient(NettyClientService nettyClientService, ChannelInitializer<SocketChannel> channelInitializer,
			NettyClientConfig nettyClientConfig) {
		this.nettyClientConfig = nettyClientConfig;
		this.service = nettyClientService;
		this.channelInitializer = channelInitializer;
	}

	@Override
	public void run() {
		connect();

	}

	/**
	 * 连接服务器
	 * 
	 * @author JiangZhiYong
	 * @QQ 359135103 2017年8月24日 下午8:40:13
	 */
	private synchronized void connect() {

		try {
			if (group == null || bootstrap == null) {
				group = new NioEventLoopGroup(nettyClientConfig.getGroupThreadNum());
				bootstrap = new Bootstrap();
				bootstrap.group(group);
				bootstrap.channel(NioSocketChannel.class);
				bootstrap.option(ChannelOption.TCP_NODELAY, nettyClientConfig.isTcpNoDealy());
				bootstrap.handler(channelInitializer);
			}

			for (int i = channels.size(); i < nettyClientConfig.getMaxConnectCount(); i++) {
				ChannelFuture channelFuture = bootstrap.connect(nettyClientConfig.getIp(), nettyClientConfig.getPort());
				channelFuture.awaitUninterruptibly(10000);	//最多等待10秒，如果服务器一直未开启情况下，房子阻塞当前线程
				channels.put(channelFuture.channel().id().asLongText(), channelFuture.channel());
				channelFuture.addListener(new GenericFutureListener<Future<? super Void>>() {
					@Override
					public void operationComplete(Future<? super Void> future) throws Exception {
						if (future.isSuccess()) {
							LOGGER.info("连接[{}]服务器{}:{}成功", nettyClientConfig.getType().toString(),
									nettyClientConfig.getIp(), nettyClientConfig.getPort());
							connectFinsh();
							channels.put(channelFuture.channel().id().asLongText(), channelFuture.channel());
						} else {
							LOGGER.warn("连接[{}]服务器{}:{}失败", nettyClientConfig.getType().toString(),
									nettyClientConfig.getIp(), nettyClientConfig.getPort());
							channels.remove(channelFuture.channel().id().asLongText());
						}
					}
				});
			}

		} catch (Exception e) {
			LOGGER.error("连接客户端", e);
		}
	}

	/**
	 * 连接服务器完成
	 * 
	 * @author JiangZhiYong
	 * @QQ 359135103 2017年8月25日 上午9:14:31
	 */
	protected void connectFinsh() {

	}

	public void stop() {
		group.shutdownGracefully();
	}

	public NettyClientService getService() {
		return service;
	}

	/**
	 * 检测tcp连接状态
	 * 
	 * @author JiangZhiYong
	 * @QQ 359135103 2017年8月28日 下午1:57:20
	 */
	public void checkStatus() {
		if (this.channels.size() < nettyClientConfig.getMaxConnectCount()&&channelInitializer!=null) {
			connect();
		}
		Optional<Channel> findAny = this.channels.values().stream().filter(c -> !c.isActive()).findAny();
		if (findAny.isPresent()) {
			channels.remove(findAny.get().id().asLongText());
		}
	}

	public NettyClientConfig getNettyClientConfig() {
		return nettyClientConfig;
	}

	public void setNettyClientConfig(NettyClientConfig nettyClientConfig) {
		this.nettyClientConfig = nettyClientConfig;
	}

}
