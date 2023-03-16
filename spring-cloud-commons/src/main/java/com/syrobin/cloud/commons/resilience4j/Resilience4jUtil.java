package com.syrobin.cloud.commons.resilience4j;

import java.lang.reflect.Method;
import java.net.URL;

public class Resilience4jUtil {
	public static String getServiceInstance(URL url) {
		return getServiceInstance(url.getHost(), url.getPort());
	}

	public static String getServiceInstance(String host, int port) {
		return host + ":" + port;
	}

	public static String getServiceInstanceMethodId(URL url, Method method) {
		return getServiceInstance(url) + ":" + method.toGenericString();
	}

	public static String getServiceInstanceMethodId(String host, int port, Method method) {
		return getServiceInstance(host, port) + ":" + method.toGenericString();
	}

	public static String getServiceInstanceMethodId(String host, int port, String path) {
		return getServiceInstance(host, port) + path;
	}
}
