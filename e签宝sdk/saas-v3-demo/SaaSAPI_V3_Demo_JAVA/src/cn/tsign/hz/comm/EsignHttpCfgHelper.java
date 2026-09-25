/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package cn.tsign.hz.comm;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @description Http请求 辅助类
 * @author 澄泓
 * @since JDK1.7
 */
public class EsignHttpCfgHelper {

	private static Logger LOGGER = LoggerFactory.getLogger(EsignHttpCfgHelper.class);
	/**
	 * 超时时间,默认15000毫秒
	 */
	private static int MAX_TIMEOUT = 15000;
	/**
	 * 请求池最大连接数,默认100个
	 */
	private static  int MAX_TOTAL=100;
	/**
	 * 单域名最大的连接数,默认50个
	 */
	private static  int ROUTE_MAX_TOTAL=50;
	/**
	 * 请求失败重试次数,默认3次
	 */
	private static   int MAX_RETRY = 3;
	/**
	 * 是否需要域名校验,默认不需要校验
	 */
	private static boolean SSL_VERIFY=false;

	/**
	 * 正向代理IP
	 */
	private static String PROXY_IP;
	/**
	 * 正向代理端口,默认8888
	 */
	private static int PROXY_PORT=8888;
	/**
	 * 代理协议,默认http
	 */
	private static String PROXY_AGREEMENT="http";

	/**
	 * 是否开启代理，默认false
	 */
	private static boolean OPEN_PROXY=false;

	/**
	 * 代理服务器用户名
	 */
	private static String PROXY_USERNAME="";

	/**
	 * 代理服务器密码
	 */
	private static String PROXY_PASSWORD="";


	private static PoolingHttpClientConnectionManager connMgr; //连接池
	private static HttpRequestRetryHandler retryHandler; //重试机制

	private static CloseableHttpClient httpClient=null;

	public static int getMaxTimeout() {
		return MAX_TIMEOUT;
	}

	public static void setMaxTimeout(int maxTimeout) {
		MAX_TIMEOUT = maxTimeout;
	}

	public static int getMaxTotal() {
		return MAX_TOTAL;
	}

	public static void setMaxTotal(int maxTotal) {
		MAX_TOTAL = maxTotal;
	}

	public static int getRouteMaxTotal() {
		return ROUTE_MAX_TOTAL;
	}

	public static void setRouteMaxTotal(int routeMaxTotal) {
		ROUTE_MAX_TOTAL = routeMaxTotal;
	}

	public static int getMaxRetry() {
		return MAX_RETRY;
	}

	public static void setMaxRetry(int maxRetry) {
		MAX_RETRY = maxRetry;
	}

	public static boolean isSslVerify() {
		return SSL_VERIFY;
	}

	public static void setSslVerify(boolean sslVerify) {
		SSL_VERIFY = sslVerify;
	}

	public static String getProxyIp() {
		return PROXY_IP;
	}

	public static void setProxyIp(String proxyIp) {
		PROXY_IP = proxyIp;
	}

	public static int getProxyPort() {
		return PROXY_PORT;
	}

	public static void setProxyPort(int proxyPort) {
		PROXY_PORT = proxyPort;
	}

	public static String getProxyAgreement() {
		return PROXY_AGREEMENT;
	}

	public static void setProxyAgreement(String proxyAgreement) {
		PROXY_AGREEMENT = proxyAgreement;
	}

	public static boolean getOpenProxy() {
		return OPEN_PROXY;
	}

	public static void setOpenProxy(boolean openProxy) {
		OPEN_PROXY = openProxy;
	}

	public static String getProxyUsername() {
		return PROXY_USERNAME;
	}

	public static void setProxyUserame(String proxyUsername) {
		PROXY_USERNAME = proxyUsername;
	}

	public static String getProxyPassword() {
		return PROXY_PASSWORD;
	}

	public static void setProxyPassword(String proxyPassword) {
		PROXY_PASSWORD = proxyPassword;
	}




	/**
	 * 不允许外部创建实例
	 */
	private EsignHttpCfgHelper() {
	}

	//------------------------------公有方法start--------------------------------------------


	/**
	 * @description 发起HTTP / HTTPS 请求
	 *
	 * @param reqType
	 * 			{@link EsignRequestType} 请求类型  GET、 POST 、 DELETE 、 PUT
	 * @param httpUrl
	 * 			{@link String} 请求目标地址
	 * @param headers
	 * 			{@link Map} 请求头
	 * @param param
	 * 			{@link Object} 参数
	 * @return
	 * @throws EsignDemoException
	 * @author 澄泓
	 */
	public static EsignHttpResponse sendHttp(EsignRequestType reqType, String httpUrl, Map<String, String> headers, Object param, boolean debug)
			throws EsignDemoException {
		HttpRequestBase reqBase=null;
		if(httpUrl.startsWith("http")){
			reqBase=reqType.getHttpType(httpUrl);
		}else{
			throw new EsignDemoException("请求url地址格式错误");
		}
		if(debug){
			LOGGER.info("请求头:{}",headers+"\n");
			LOGGER.info("请求参数\n{}", param+"\n");
			LOGGER.info("请求地址\n:{}\n请求方式\n:{}",reqBase.getURI(),reqType+"\n");
		}
		//请求方法不是GET或者DELETE时传入body体，否则不传入。
		String[] methods = {"DELETE", "GET"};
		if(param instanceof String&&Arrays.binarySearch(methods, reqType.name())<0){//POST或者PUT请求
			((HttpEntityEnclosingRequest) reqBase).setEntity(
					new StringEntity(String.valueOf(param), ContentType.create("application/json", "UTF-8")));
		}
		//参数时字节流数组
		else if(param instanceof byte[]) {
			reqBase=reqType.getHttpType(httpUrl);
			byte[] paramBytes = (byte[])param;
			((HttpEntityEnclosingRequest) reqBase).setEntity(new ByteArrayEntity(paramBytes));
		}
		//参数是form表单时
		else if(param instanceof List){
			((HttpEntityEnclosingRequest) reqBase).setEntity(new UrlEncodedFormEntity((Iterable<? extends NameValuePair>) param));
		}
		httpClient = getHttpClient();
		config(reqBase);

		//设置请求头
		if(headers != null &&headers.size()>0) {
			for(Map.Entry<String, String> entry :headers.entrySet()) {
				reqBase.setHeader(entry.getKey(), entry.getValue());
			}
		}
		//响应对象
		CloseableHttpResponse res = null;
		//响应内容
		String resCtx = null;
		int status;
		EsignHttpResponse esignHttpResponse = new EsignHttpResponse();
		try {
			//执行请求
			res = httpClient.execute(reqBase);
			status=res.getStatusLine().getStatusCode();

			//获取请求响应对象和响应entity
			HttpEntity httpEntity = res.getEntity();
			if(httpEntity != null) {
				resCtx = EntityUtils.toString(httpEntity,"utf-8");
			}
			if(debug) {
				LOGGER.info("响应\n{}", resCtx + "\n");
				LOGGER.info("----------------------------end------------------------");
			}
		} catch (NoHttpResponseException e) {
			throw new EsignDemoException("服务器丢失了",e);
		} catch (SSLHandshakeException e){
			String msg = MessageFormat.format("SSL握手异常", e);
			EsignDemoException ex = new EsignDemoException(msg, e);
			throw ex;
		} catch (UnknownHostException e){
			EsignDemoException ex = new EsignDemoException("服务器找不到", e);
			ex.initCause(e);
			throw ex;
		} catch(ConnectTimeoutException e){
			EsignDemoException ex = new EsignDemoException("连接超时", e);
			ex.initCause(e);
			throw ex;
		} catch(SSLException e){
			EsignDemoException ex = new EsignDemoException("SSL异常",e);
			ex.initCause(e);
			throw ex;
		} catch (ClientProtocolException e) {
			EsignDemoException ex = new EsignDemoException("请求头异常",e);
			ex.initCause(e);
			throw ex;
		} catch (IOException e) {
			EsignDemoException ex = new EsignDemoException("网络请求失败",e);
			ex.initCause(e);
			throw ex;
		} finally {
			if(res != null) {
				try {
					res.close();
				} catch (IOException e) {
					EsignDemoException ex = new EsignDemoException("--->>关闭请求响应失败",e);
					ex.initCause(e);
					throw ex;
				}
			}
		}
		esignHttpResponse.setStatus(status);
		esignHttpResponse.setBody(resCtx);
		return esignHttpResponse;
	}
	//------------------------------公有方法end----------------------------------------------

	//------------------------------私有方法start--------------------------------------------

	/**
	 * @description 请求头和超时时间配置
	 *
	 * @param httpReqBase
	 * @author 澄泓
	 */
	private static void config(HttpRequestBase httpReqBase) {
		// 配置请求的超时设置
		RequestConfig.Builder builder = RequestConfig.custom()
				.setConnectionRequestTimeout(MAX_TIMEOUT)
				.setConnectTimeout(MAX_TIMEOUT)
				.setSocketTimeout(MAX_TIMEOUT);
		if(OPEN_PROXY){
			HttpHost proxy=new HttpHost(PROXY_IP,PROXY_PORT,PROXY_AGREEMENT);
			builder.setProxy(proxy);
		}
		RequestConfig requestConfig = builder.build();
		httpReqBase.setConfig(requestConfig);
	}

	/**
	 * @description 连接池配置
	 *
	 * @return
	 * @author 澄泓
	 */
	private static void cfgPoolMgr() throws EsignDemoException {
		ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
		LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
		if(!SSL_VERIFY){
            sslsf=sslConnectionSocketFactory();
        }

		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("http", plainsf)
				.register("https", sslsf)
				.build();

		//连接池管理器
		connMgr = new PoolingHttpClientConnectionManager(registry);
		//请求池最大连接数
		connMgr.setMaxTotal(MAX_TOTAL);
		//但域名最大的连接数
		connMgr.setDefaultMaxPerRoute(ROUTE_MAX_TOTAL);
	}




	/**
	 * @description 设置重试机制
	 *
	 * @author 澄泓
	 */
	private static void cfgRetryHandler() {
		retryHandler = new HttpRequestRetryHandler() {

			@Override
			public boolean retryRequest(IOException e, int excCount, HttpContext ctx) {
				//超过最大重试次数，就放弃
				if(excCount > MAX_RETRY) {
					return false;
				}
				//服务器丢掉了链接，就重试
				if(e instanceof NoHttpResponseException) {
					return true;
				}
				//不重试SSL握手异常
				if(e instanceof SSLHandshakeException) {
					return false;
				}
				//中断
				if(e instanceof InterruptedIOException) {
					return false;
				}
				//目标服务器不可达
				if(e instanceof UnknownHostException) {
					return false;
				}
				//连接超时
				//SSL异常
				if(e instanceof SSLException) {
					return false;
				}

				HttpClientContext clientCtx = HttpClientContext.adapt(ctx);
				HttpRequest req = clientCtx.getRequest();
				//如果是幂等请求，就再次尝试
				if(!(req instanceof HttpEntityEnclosingRequest)) {
					return true;
				}
				return false;
			}
		};
	}

    /**
     * 忽略域名校验
     */
    private static SSLConnectionSocketFactory sslConnectionSocketFactory() throws EsignDemoException {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");    // 创建一个上下文（此处指定的协议类型似乎不是重点）
            X509TrustManager tm = new X509TrustManager() {     // 创建一个跳过SSL证书的策略
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }
            };
            ctx.init(null, new TrustManager[] { tm }, null);    // 使用上面的策略初始化上下文
            return new SSLConnectionSocketFactory(ctx, new String[] { "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2" }, null, NoopHostnameVerifier.INSTANCE);
        }catch (Exception e){
            EsignDemoException ex = new EsignDemoException("忽略域名校验失败",e);
            ex.initCause(e);
            throw ex;
        }

    }

	/**
	 * @description 获取单例HttpClient
	 *
	 * @return
	 * @author 澄泓
	 */
	private static synchronized CloseableHttpClient getHttpClient() throws EsignDemoException {
		if(httpClient==null) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope(PROXY_IP,PROXY_PORT),new UsernamePasswordCredentials(PROXY_USERNAME, PROXY_PASSWORD));
			cfgPoolMgr();
			cfgRetryHandler();
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            httpClient =  httpClientBuilder.setDefaultCredentialsProvider(credsProvider).setConnectionManager(connMgr).setRetryHandler(retryHandler).build();
		}
		return httpClient;

	}
	//------------------------------私有方法end----------------------------------------------


}
