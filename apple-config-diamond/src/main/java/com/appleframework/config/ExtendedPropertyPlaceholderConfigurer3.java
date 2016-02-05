package com.appleframework.config;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

import com.appleframework.config.core.EnvConfigurer;
import com.appleframework.config.core.PropertyConfigurer;
import com.appleframework.config.core.event.ConfigEventListener;
import com.appleframework.config.core.event.ConfigEventSource;
import com.appleframework.config.core.util.StringUtils;
import com.taobao.diamond.manager.DiamondManager;
import com.taobao.diamond.manager.ManagerListener;
import com.taobao.diamond.manager.impl.DefaultDiamondManager;

public class ExtendedPropertyPlaceholderConfigurer3 extends PropertyPlaceholderConfigurer {
	
	private static Logger logger = Logger.getLogger(ExtendedPropertyPlaceholderConfigurer3.class);
	
	private Properties props;
	
	private String eventListenerClass;
	
	private boolean loadRemote = true;
	
	public boolean isLoadRemote() {
		return loadRemote;
	}

	public void setLoadRemote(boolean loadRemote) {
		this.loadRemote = loadRemote;
	}

	public void setEventListenerClass(String eventListenerClass) {
		this.eventListenerClass = eventListenerClass;
	}
	
    private ConfigEventSource eventSource;


	@Override
	protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) throws BeansException {
		if(!isLoadRemote()) {
			super.processProperties(beanFactory, props);
			this.props = props;
			PropertyConfigurer.load(props);
			return;
		}
		
		String group = props.getProperty("deploy.group");
		String dataId = props.getProperty("deploy.dataId");
		
		logger.warn("配置项：group=" + group);
		logger.warn("配置项：dataId=" + dataId);
		
		if(!StringUtils.isEmpty(group) && !StringUtils.isEmpty(dataId)) {
			if(!StringUtils.isEmpty(EnvConfigurer.env)){
				dataId += "-" + EnvConfigurer.env;
				logger.warn("配置项：env=" + EnvConfigurer.env);
			}
			else {
				String env = props.getProperty("deploy.env");
				if(!StringUtils.isEmpty(env)){
					dataId += "-" + env;
				}
				logger.warn("配置项：env=" + env);
			}
			
			//定义事件源 
			try {
				eventSource = new ConfigEventSource();
				if(!StringUtils.isNullOrEmpty(eventListenerClass)) {
					//定义并向事件源中注册事件监听器  
					Class<?> clazz = Class.forName(eventListenerClass);
					ConfigEventListener configEventListener = (ConfigEventListener)clazz.newInstance();
					eventSource.addConfigListener(configEventListener);
				}
			} catch (Exception e) {
				logger.error(e);
			}
			
	        
			DiamondManager manager = new DefaultDiamondManager(group, dataId, new ManagerListener() {
		        
				public Executor getExecutor() {
					return null;
				}
				public void receiveConfigInfo(String configInfo) {
					// 客户端处理数据的逻辑
					logger.warn("已改动的配置：\n"+configInfo);
					StringReader reader = new StringReader(configInfo);
					try {
						PropertyConfigurer.props.load(reader);
					} catch (IOException e) {
						logger.error(e);
					}
					//事件处理
					try {
						//事件通知
						eventSource.notifyConfigEvent(PropertyConfigurer.props);  
					} catch (Exception e) {
						logger.error(e);
					}
				}
			});
			
			try {
				String configInfo = manager.getAvailableConfigureInfomation(30000);
				logger.warn("配置项内容: \n" + configInfo);
				if(!StringUtils.isEmpty(configInfo)) {
					StringReader reader = new StringReader(configInfo);
					props.load(reader);
					PropertyConfigurer.load(props);
				}
				else {
					logger.error("在配置管理中心找不到配置信息");
				}				
			} catch (IOException e) {
				logger.error(e);
			}
		} else {
			PropertyConfigurer.load(props);
		}
		super.processProperties(beanFactory, props);
		this.props = props;
	}

	public Object getProperty(String key) {
		return props.get(key);
	}
}