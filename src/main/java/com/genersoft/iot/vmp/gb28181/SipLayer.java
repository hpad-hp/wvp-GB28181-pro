package com.genersoft.iot.vmp.gb28181;

import com.genersoft.iot.vmp.conf.SipConfig;
import com.genersoft.iot.vmp.gb28181.transmit.ISIPProcessorObserver;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sip.*;
import java.util.Properties;
import java.util.TooManyListenersException;

@Configuration
public class SipLayer{

	private final static Logger logger = LoggerFactory.getLogger(SipLayer.class);

	@Autowired
	private SipConfig sipConfig;

	@Autowired
	private ISIPProcessorObserver sipProcessorObserver;

	private SipStackImpl sipStack;

	private SipFactory sipFactory;


	@Bean("sipFactory")
	SipFactory createSipFactory() {
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		return sipFactory;
	}
	
	@Bean("sipStack")
	@DependsOn({"sipFactory"})
	SipStack createSipStack() throws PeerUnavailableException {
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "GB28181_SIP");
		properties.setProperty("javax.sip.IP_ADDRESS", sipConfig.getMonitorIp());
		/**
		 * 完整配置参考 gov.nist.javax.sip.SipStackImpl，需要下载源码
		 * gov/nist/javax/sip/SipStackImpl.class
		 * sip消息的解析在 gov.nist.javax.sip.stack.UDPMessageChannel的processIncomingDataPacket方法
		 */
//		 * gov/nist/javax/sip/SipStackImpl.class
		if (logger.isDebugEnabled()) {
			properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "false");
		}
		// 接收所有notify请求，即使没有订阅
		properties.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
		properties.setProperty("gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING", "false");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		// 为_NULL _对话框传递_终止的_事件
		properties.setProperty("gov.nist.javax.sip.DELIVER_TERMINATED_EVENT_FOR_NULL_DIALOG", "true");
		// 会话清理策略
		properties.setProperty("gov.nist.javax.sip.RELEASE_REFERENCES_STRATEGY", "Normal");
		// 处理由该服务器处理的基于底层TCP的保持生存超时
		properties.setProperty("gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT", "60");

		/**
		 * sip_server_log.log 和 sip_debug_log.log ERROR, INFO, WARNING, OFF, DEBUG, TRACE
		 */
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "ERROR");
//		properties.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", "com.genersoft.iot.vmp.gb28181.session.SipMessagePreprocessing");
//		if (logger.isDebugEnabled()) {
//			properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "DEBUG");
//		}

		sipStack = (SipStackImpl) sipFactory.createSipStack(properties);

		return sipStack;
	}

	@Bean(name = "tcpSipProvider")
	@DependsOn("sipStack")
	SipProviderImpl startTcpListener() {
		ListeningPoint tcpListeningPoint = null;
		SipProviderImpl tcpSipProvider  = null;
		try {
			tcpListeningPoint = sipStack.createListeningPoint(sipConfig.getMonitorIp(), sipConfig.getPort(), "TCP");
			tcpSipProvider = (SipProviderImpl)sipStack.createSipProvider(tcpListeningPoint);
			tcpSipProvider.setDialogErrorsAutomaticallyHandled();
			tcpSipProvider.addSipListener(sipProcessorObserver);
//			tcpSipProvider.setAutomaticDialogSupportEnabled(false);
			logger.info("[Sip Server] TCP 启动成功 {}:{}", sipConfig.getMonitorIp(), sipConfig.getPort());
		} catch (TransportNotSupportedException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			logger.error("[Sip Server]  无法使用 [ {}:{} ]作为SIP[ TCP ]服务，可排查: 1. sip.monitor-ip 是否为本机网卡IP; 2. sip.port 是否已被占用"
					, sipConfig.getMonitorIp(), sipConfig.getPort());
		} catch (TooManyListenersException e) {
			e.printStackTrace();
		} catch (ObjectInUseException e) {
			e.printStackTrace();
		}
		return tcpSipProvider;
	}
	
	@Bean(name = "udpSipProvider")
	@DependsOn("sipStack")
	SipProviderImpl startUdpListener() {
		ListeningPoint udpListeningPoint = null;
		SipProviderImpl udpSipProvider = null;
		try {
			udpListeningPoint = sipStack.createListeningPoint(sipConfig.getMonitorIp(), sipConfig.getPort(), "UDP");
			udpSipProvider = (SipProviderImpl)sipStack.createSipProvider(udpListeningPoint);
			udpSipProvider.addSipListener(sipProcessorObserver);
//			udpSipProvider.setAutomaticDialogSupportEnabled(false);
		} catch (TransportNotSupportedException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			logger.error("[Sip Server]  无法使用 [ {}:{} ]作为SIP[ UDP ]服务，可排查: 1. sip.monitor-ip 是否为本机网卡IP; 2. sip.port 是否已被占用"
					, sipConfig.getMonitorIp(), sipConfig.getPort());
		} catch (TooManyListenersException e) {
			e.printStackTrace();
		} catch (ObjectInUseException e) {
			e.printStackTrace();
		}
		logger.info("[Sip Server] UDP 启动成功 {}:{}", sipConfig.getMonitorIp(), sipConfig.getPort());
		return udpSipProvider;
	}

}
