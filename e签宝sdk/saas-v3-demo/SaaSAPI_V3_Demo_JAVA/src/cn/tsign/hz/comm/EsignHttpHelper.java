package cn.tsign.hz.comm;

import cn.tsign.hz.enums.EsignHeaderConstant;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @description Http 请求 辅助类
 * @author 澄泓
 * @since JDK1.7
 */
public class EsignHttpHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(EsignHttpHelper.class);

	/**
	 * 不允许外部创建实例
	 */
	private EsignHttpHelper() {

	}

	/**
	 * @description 发送常规HTTP 请求
	 *
	 * @param reqType 请求方式
	 * @param url 请求路径
	 * @param paramStr 请求参数
	 * @return
	 * @throws EsignDemoException
	 * @author 澄泓
	 */
	public static EsignHttpResponse doCommHttp(String host, String url,EsignRequestType reqType, Object paramStr ,Map<String,String> httpHeader,boolean debug) throws EsignDemoException {
		return EsignHttpCfgHelper.sendHttp(reqType, host+url,httpHeader, paramStr, debug);
	}


	/**
	 * @description 发送文件流上传 HTTP 请求
	 *
	 * @param reqType 请求方式
	 * @param uploadUrl 请求路径
	 * @param param 请求参数
	 * @param fileContentMd5 文件fileContentMd5
	 * @param contentType 文件MIME类型
	 * @return
	 * @throws EsignDemoException
	 * @author 澄泓
	 */
	public static EsignHttpResponse doUploadHttp( String uploadUrl,EsignRequestType reqType,byte[] param, String fileContentMd5,
												 String contentType, boolean debug) throws EsignDemoException {
		Map<String, String> uploadHeader = buildUploadHeader(fileContentMd5, contentType);
		if(debug){
			LOGGER.info("----------------------------start------------------------");
			LOGGER.info("fileContentMd5:{}",fileContentMd5);
			LOGGER.info("contentType:{}",contentType);
		}
		return EsignHttpCfgHelper.sendHttp(reqType,uploadUrl, uploadHeader, param,debug);
	}



	/**
	 * @description 构建一个签名鉴权+json数据的esign请求头
	 * @return
	 * @author 澄泓
	 */
	public static Map<String, String> buildSignAndJsonHeader(String projectId,String contentMD5,String accept,String contentType,String authMode) {

		Map<String, String> header = new HashMap<>();
		header.put("X-Tsign-Open-App-Id", projectId);
		header.put("X-Tsign-Open-Version-Sdk",EsignCoreSdkInfo.getSdkVersion());
		header.put("X-Tsign-Open-Ca-Timestamp", EsignEncryption.timeStamp());
		header.put("Accept",accept);
		header.put("Content-MD5",contentMD5);
		header.put("Content-Type", contentType);
		header.put("X-Tsign-Open-Auth-Mode", authMode);
		return header;
	}

	/**
	 * 签名计算并且构建一个签名鉴权+json数据的esign请求头
	 * @param  httpMethod
	 *      *         The name of a supported {@linkplain java.nio.charset.Charset
	 *      *         charset}
	 * @return
	 */
	public static Map<String,String> signAndBuildSignAndJsonHeader(String projectId, String secret,String paramStr,String httpMethod,String url,boolean debug) throws EsignDemoException {
		String contentMD5="";
		//统一转大写处理
		httpMethod = httpMethod.toUpperCase();
		if("GET".equals(httpMethod)||"DELETE".equals(httpMethod)){
			paramStr=null;
			contentMD5="";
		} else if("PUT".equals(httpMethod)||"POST".equals(httpMethod)){
			//对body体做md5摘要
			contentMD5=EsignEncryption.doContentMD5(paramStr);
		}else{
			throw new EsignDemoException(String.format("不支持的请求方法%s",httpMethod));
		}
		//构造一个初步的请求头
		Map<String, String> esignHeaderMap = buildSignAndJsonHeader(projectId, contentMD5, EsignHeaderConstant.ACCEPT.VALUE(), EsignHeaderConstant.CONTENTTYPE_JSON.VALUE(), EsignHeaderConstant.AUTHMODE.VALUE());
		//排序
		url=EsignEncryption.sortApiUrl(url);
		//传入生成的bodyMd5,加上其他请求头部信息拼接成字符串
		String message = EsignEncryption.appendSignDataString(httpMethod, esignHeaderMap.get("Content-MD5"),esignHeaderMap.get("Accept"),esignHeaderMap.get("Content-Type"),esignHeaderMap.get("Headers"),esignHeaderMap.get("Date"), url);
		//整体做sha256签名
		String reqSignature = EsignEncryption.doSignatureBase64(message, secret);
		//请求头添加签名值
		esignHeaderMap.put("X-Tsign-Open-Ca-Signature",reqSignature);
		if(debug){
			LOGGER.info("----------------------------start------------------------");
			LOGGER.info("待计算body值:{}", paramStr+"\n");
			LOGGER.info("MD5值:{}",contentMD5+"\n");
			LOGGER.info("待签名字符串:{}",message+"\n");
			LOGGER.info("签名值:{}",reqSignature+"\n");
		}
		return esignHeaderMap;
	}


	/**
	 * @description 构建一个Token鉴权+jsons数据的esign请求头
	 * @return
	 * @author 澄泓
	 */
	public static Map<String, String> buildTokenAndJsonHeader(String appid,String token) {
		Map<String, String> esignHeader = new HashMap<>();
		esignHeader.put("X-Tsign-Open-Version-Sdk",EsignCoreSdkInfo.getSdkVersion());
		esignHeader.put("Content-Type", EsignHeaderConstant.CONTENTTYPE_JSON.VALUE());
		esignHeader.put("X-Tsign-Open-App-Id", appid);
		esignHeader.put("X-Tsign-Open-Token", token);
		return esignHeader;
	}

	/**
	 * @description 构建一个form表单数据的esign请求头
	 * @return
	 * @author 澄泓
	 */
	public static Map<String, String> buildFormDataHeader(String appid) {
		Map<String, String> esignHeader = new HashMap<>();
		esignHeader.put("X-Tsign-Open-Version-Sdk",EsignCoreSdkInfo.getSdkVersion());
		esignHeader.put("X-Tsign-Open-Authorization-Version","v2");
		esignHeader.put("Content-Type", EsignHeaderConstant.CONTENTTYPE_FORMDATA.VALUE());
		esignHeader.put("X-Tsign-Open-App-Id", appid);
		return esignHeader;
	}

	/**
	 * @description 创建文件流上传 请求头
	 *
	 * @param fileContentMd5
	 * @param contentType
	 * @return
	 * @author 澄泓
	 */
	public static Map<String, String> buildUploadHeader(String fileContentMd5, String contentType) {
		Map<String, String> header = new HashMap<>();
		header.put("Content-MD5", fileContentMd5);
		header.put("Content-Type", contentType);

		return header;
	}

	// ------------------------------私有方法end----------------------------------------------
}
