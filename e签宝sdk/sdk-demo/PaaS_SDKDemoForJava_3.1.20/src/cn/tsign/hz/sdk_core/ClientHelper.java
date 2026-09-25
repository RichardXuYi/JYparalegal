package cn.tsign.hz.sdk_core;

import java.text.MessageFormat;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import com.timevale.esign.paas.tech.bean.result.Result;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.client.ServiceClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.timevale.esign.paas.sdk.bean.HttpConnectionConfig;
import com.timevale.esign.paas.sdk.bean.ProjectConfig;
import com.timevale.esign.paas.sdk.bean.SignatureConfig;
import com.timevale.esign.paas.sdk.constants.AlgorithmType;
import cn.tsign.hz.exception.DefineException;


/**
 * description SDK客户端辅助类
 */
public class ClientHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(ClientHelper.class);


	//----------------------------------------公有方法 start-------------------------------------------

	/**
	 *
	 * description 注册客户端
	 */
	public static void registClient() throws DefineException{

		/**1、进行项目配置，从开放平台获取*/
		ProjectConfig proCfg = getProjectCfg();

		/**2、Http配置*/
		HttpConnectionConfig httpConCfg = getHttpConCfg();

		/**3、签名配置*/
		SignatureConfig signCfg = getSignatureCfg();

		/**4、注册客户端*/
		Result rst = ServiceClientManager.registClient(proCfg, httpConCfg, signCfg);

		if(rst.getErrCode() != 0){
			String rstMsg = MessageFormat.format("注册[{0}]的客户端失败：errorCode={1}，msg={2}",
					ConfigConstant.PROJECT_ID,rst.getErrCode(),rst.getMsg());
			throw new DefineException(rstMsg);
		}

		LOGGER.info("注册[{}]的客户端成功",ConfigConstant.PROJECT_ID);
	}



	/**
	 *
	 * description 获取客户端
	 */
	public static ServiceClient getServiceClient(String projectId)throws DefineException{
		ServiceClient serviceClient = ServiceClientManager.get(projectId);
		if(serviceClient == null){
			throw new DefineException(MessageFormat.format(
					"ServiceClient为空，获取[{0}]的客户端失败，请重新注册客户端", projectId));
		}

		LOGGER.info("获取[{}]的客户端成功", projectId);
		return serviceClient;
	}



	/**
	 *
	 * description 关闭客户端
	 */
	public static void shutDownServiceClient(String projectId) throws DefineException{
		try {
			ServiceClientManager.shutdown(projectId);
		} catch (Exception e) {
			throw new DefineException(MessageFormat.format(
					"[{0}]的客户端关闭异常,请检查[{0}]的客户端是否注册成功或已关闭", projectId));
		}

		ServiceClient serviceClient = ServiceClientManager.get(projectId);
		if(serviceClient != null){
			throw new DefineException(MessageFormat.format("关闭[{0}]的客户端失败，请检查原因", projectId));
		}

		LOGGER.info("[{}]的客户端关闭成功", projectId);
	}

	//----------------------------------------公有方法 end--------------------------------------------

	//----------------------------------------私有方法 start------------------------------------------

	/**
	 *
	 * description 项目配置
	 */
	private static ProjectConfig getProjectCfg(){
		ProjectConfig proCfg = new ProjectConfig();
		//项目ID（应用ID）
		proCfg.setProjectId(ConfigConstant.PROJECT_ID);
		//项目Secret(应用Secret)
		proCfg.setProjectSecret(ConfigConstant.PROJECT_SECRET);
		//开放平台地址
		proCfg.setItsmApiUrl(ConfigConstant.API_URL);
		return proCfg;
	}



	/**
	 *
	 * description http配置
	 */
	private static HttpConnectionConfig getHttpConCfg(){
		HttpConnectionConfig httpConCfg = new HttpConnectionConfig();
		//代理服务IP配置
		//httpConCfg.setProxyIp(null);
		//代理服务端口
		//httpConCfg.setProxyPort(null);
		//协议类型，默认Https
		//httpConCfg.setHttpType(null);
		//请求失败重试次数，默认5次
		//httpConCfg.setRetry(null);
		//连接超时时间配置，最大不能超过30秒
		//httpConCfg.setTimeoutConnect(30);
		//请求超时时间，最大不能超过30
		//httpConCfg.setTimeoutRequest(30);
		//代理服务器登录用户名
		//httpConCfg.setUsername(null);
		//代理服务器登录密码
		//httpConCfg.setPassword(null);
		return httpConCfg;
	}



	/**
	 *
	 * description 签名配置
	 */
	private static SignatureConfig getSignatureCfg(){
		SignatureConfig signCfg = new SignatureConfig();
		signCfg.setAlgorithm(ConfigConstant.ALGORITHM_TYPE);

		//若算法类型是RSA，需要设置e签宝公钥和平台私钥
		if(AlgorithmType.RSA == ConfigConstant.ALGORITHM_TYPE){
			signCfg.setEsignPublicKey(ConfigConstant.ESIGN_PUB_KEY);
			signCfg.setPrivateKey(ConfigConstant.ESIGN_PRI_KEY);
		}
		return signCfg;
	}
	//----------------------------------------私有方法 end--------------------------------------------

}
