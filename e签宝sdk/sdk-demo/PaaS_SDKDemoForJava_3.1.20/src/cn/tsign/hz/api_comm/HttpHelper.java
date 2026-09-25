package cn.tsign.hz.api_comm;

import cn.tsign.hz.api_enums.HeaderConstant;
import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.exception.DefineException;
import cn.tsign.hz.api_factory.Factory;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * @description Http 请求 辅助类

 */
public class HttpHelper<T> {

	/**
	 * 不允许外部创建实例
	 */
	private HttpHelper() {

	}

	// ------------------------------公有方法start--------------------------------------------

	/**
	 * @description 发送常规HTTP 请求
	 *
	 * @param reqType 请求方式
	 * @param url 请求路径
	 * @param paramStr 请求参数
	 * @return
	 * @throws DefineException
	 * @date 2019年7月19日 下午2:27:59
	 * @author 澄泓
	 */
	public static Map doCommHttp(RequestType reqType, String url, String paramStr) throws DefineException {
		//对body体做md5摘要
		String contentMD5=Encryption.doContentMD5(paramStr);
		//get和delete方式请求不能携带body体
		if("GET".equals(reqType.name())||"DELETE".equals(reqType.name())){
			paramStr=null;
		}
		//传入生成的bodyMd5,加上其他请求头部信息拼接成字符串
		String message = Encryption.appendSignDataString(reqType.name(), HeaderConstant.ACCEPT.VALUE(), contentMD5, HeaderConstant.CONTENTTYPE_JSON.VALUE(), HeaderConstant.DATE.VALUE(), HeaderConstant.HEADERS.VALUE(), url);

		//整体做sha256签名
		String reqSignature = Encryption.doSignatureBase64(message, Factory.getProject_scert());


		return HttpCfgHelper.sendHttp(reqType, url, buildCommHeader(contentMD5,reqSignature), paramStr);
	}


	// ------------------------------公有方法end----------------------------------------------

	// ------------------------------私有方法start--------------------------------------------

	/**
	 * @description 创建常规请求头
	 * @param contentMD5
	 * @return
	 * @date 2019年7月19日 下午2:36:51
	 * @author 洪天宇
	 */
	private static Map<String, String> buildCommHeader(String contentMD5,String reqSignature) {

		Map<String, String> header = Maps.newHashMap();
		header.put("X-Tsign-Open-App-Id", Factory.getProject_id());
		header.put("X-Tsign-Open-Ca-Timestamp", Encryption.timeStamp());
		header.put("Accept",HeaderConstant.ACCEPT.VALUE());
		header.put("X-Tsign-Open-Ca-Signature",reqSignature);
		header.put("Content-MD5",contentMD5);
		header.put("Content-Type", HeaderConstant.CONTENTTYPE_JSON.VALUE());
		header.put("X-Tsign-Open-Auth-Mode", HeaderConstant.AUTHMODE.VALUE());
		return header;
	}


	// ------------------------------私有方法end----------------------------------------------
}
