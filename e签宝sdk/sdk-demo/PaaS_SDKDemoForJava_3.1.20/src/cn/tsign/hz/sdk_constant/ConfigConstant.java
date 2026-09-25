package cn.tsign.hz.sdk_constant;

import com.timevale.esign.paas.sdk.constants.AlgorithmType;
import com.timevale.esign.paas.sdk.constants.HttpType;

/**
 * SDK3.0请求配置项
 * description 常用配置常量类
 */
public class ConfigConstant {

	//应用ID,沙箱环境7开头，正式环境5开头
	public static final String PROJECT_ID = "743*****";

	//应用Secret
	public static final String PROJECT_SECRET = "b5972cab5******e5703785ec";

	//协议类型（可选HTTP或HTTPS)
	public static final HttpType HTTP_TYPE = HttpType.HTTP;

	//e签宝环境地址 模拟环境：http://smlitsm.tsign.cn:8080    正式环境：http://sdkapi.esign.cn
	public static final String API_HOST = "http://smlitsm.tsign.cn:8080";

	//开放平台地址
	public static final String API_URL = API_HOST + "/tgmonitor/rest/app!getAPIInfo2";

	//算法类型（可选HMACSHA256/RSA，推荐使用HMACSHA256)
	public static final AlgorithmType ALGORITHM_TYPE = AlgorithmType.HMACSHA256;

	//e签宝公钥，可从开放平台获取，若算法类型为RSA，此项必填
	public static final String ESIGN_PUB_KEY = null;

	//e签宝私钥，可从开放平台下载密钥生成工具生成，若算法为RSA，此项必填
	public static final String ESIGN_PRI_KEY = null;

	//当前SDK版本号
	public static final String TECH_SDK_VERSION = "1.0.0";

}
