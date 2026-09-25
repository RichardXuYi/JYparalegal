package auth;

import cn.tsign.hz.comm.EsignHttpHelper;
import cn.tsign.hz.comm.EsignHttpResponse;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;


import java.util.Map;

/**
 * description 认证与授权服务
 * @author 语冰
 * datetime 2022年8月10日上午09:43:24
 */
public class AuthDemo extends Exception {

	private static String eSignHost= EsignDemoConfig.EsignHost;
	private static String eSignAppId=EsignDemoConfig.EsignAppId;
	private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;

	public static void main(String[] args) throws EsignDemoException {
		Gson gson = new Gson();
		/* 获取个人认证授权链接*/
		EsignHttpResponse getPsnAuthUrl = getPsnAuthUrl();
		JsonObject getPsnAuthUrlObject = gson.fromJson(getPsnAuthUrl.getBody(), JsonObject.class);
		JsonObject data = getPsnAuthUrlObject.getAsJsonObject("data");
		String authUrl = data.get("authUrl").getAsString();
		System.err.println("个人授权链接:"+authUrl);
		/* 查询个人认证授权状态*/
		EsignHttpResponse getPsnIdentityInfo = getPsnIdentityInfo();
		JsonObject getPsnIdentityInfoObject = gson.fromJson(getPsnIdentityInfo.getBody(), JsonObject.class);
		String realnameStatus =getPsnIdentityInfoObject.getAsJsonObject("data").get("realnameStatus").getAsString();//授权状态authorizeUserInfo
		System.err.println("个人认证状态:"+realnameStatus);
		/* 获取企业认证授权链接*/
		EsignHttpResponse getOrgAuthUrl = getOrgAuthUrl();
		JsonObject getOrgAuthUrlObject = gson.fromJson(getOrgAuthUrl.getBody(), JsonObject.class);
		String orgAuthUrl =	getOrgAuthUrlObject.getAsJsonObject("data").get("authUrl").getAsString();
		System.err.println("企业授权链接:"+orgAuthUrl);
		/* 查询企业认证授权状态*/
		EsignHttpResponse getOrgIdentityInfo = getOrgIdentityInfo();
		JsonObject getOrgIdentityInfoObject = gson.fromJson(getOrgIdentityInfo.getBody(), JsonObject.class);
		String orgRealnameStatus =	getOrgIdentityInfoObject.getAsJsonObject("data").get("realnameStatus").getAsString();//授权状态authorizeUserInfo
		System.err.println("企业认证状态:"+orgRealnameStatus);

	}

	/**
	 * 获取个人认证链接
	 * @return
	 * @throws EsignDemoException
	 */
	public static EsignHttpResponse getPsnAuthUrl() throws EsignDemoException {
		String apiaddr="/v3/psn-auth-url";
		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm="{\n" +
				"    \"psnAuthConfig\": {\n" +
				"        \"psnAccount\": \"184xxxxx04\",\n" +
				"        \"psnAuthPageConfig\": {\n" +
				"            \"psnEditableFields\": [\n" +
				"                \"name\",\n" +
				"                \"IDCardNum\",\n" +
				"                \"mobile\",\n" +
				"                \"bankCardNum\"  \n" +
				"            ]\n" +
				"        }\n" +
				"    },\n" +
				"    \"clientType\": \"ALL\",\n" +
				"    \"appScheme\":\"esign://demo/realBack\",\n" +
				"    \"redirectConfig\": {\n" +
				"        \"redirectDelayTime\": \"3\",\n" +
				"        \"redirectUrl\": \"esign://demo/realBack\"\n" +
				"    },\n" +
				"    \"authorizeConfig\": {\n" +
				"        \"authorizedScopes\": [\n" +
				"            \"get_psn_identity_info\",\n" +
				"            \"psn_initiate_sign\",\n" +
				"            \"manage_psn_resource\"\n" +
				"        ]\n" +
				"    },\n" +
				"    \"repeatableAuth\": true\n" +
				"}";

		//请求方法
		EsignRequestType requestType= EsignRequestType.POST;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}

	/**
	 * 查询个人认证授权状态
	 */
	public static EsignHttpResponse getPsnIdentityInfo() throws EsignDemoException {
		String psnAccount="184xxxxx04";
		String apiaddr="/v3/persons/identity-info?psnAccount="+psnAccount;

		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm=null;
		//请求方法
		EsignRequestType requestType= EsignRequestType.GET;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}
	/**
	 * 获取企业认证链接
	 * @return
	 * @throws EsignDemoException
	 *
	 *
	 */
	public static EsignHttpResponse getOrgAuthUrl() throws EsignDemoException {
		String apiaddr="/v3/org-auth-url";
		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm="{\n" +
				"    \"orgAuthConfig\": {\n" +
				"        \"orgName\": \"语冰test\",\n" +
				"        \"transactorInfo\": {\n" +
				"            \"psnAccount\": \"184xxxx2104\"\n" +
				"        }\n" +
				"    },\n" +
				"    \"clientType\": \"all\",\n" +
				"    \"notifyUrl\":\"https://www.xxx.cn/notify\",\n" +
				"    \"redirectConfig\": {\n" +
				"        \"redirectUrl\": \"https://www.esign.cn\"\n" +
				"    },\n" +
				"    \"authorizeConfig\":{\n" +
				"        \"authorizedScopes\":[\"get_org_identity_info\",\"get_psn_identity_info\",\"org_initiate_sign\",\"psn_initiate_sign\",\"manage_org_resource\",\"manage_psn_resource\",\"use_org_order\"]\n" +
				"    }\n" +
				"}";

		//请求方法
		EsignRequestType requestType= EsignRequestType.POST;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}

	/**
	 * 查询企业认证授权状态
	 */
	public static EsignHttpResponse getOrgIdentityInfo() throws EsignDemoException {
		String orgName="语冰test";
		String apiaddr="/v3/organizations/identity-info?orgName="+orgName;

		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm="{}";
		//请求方法
		EsignRequestType requestType= EsignRequestType.GET;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}
}
