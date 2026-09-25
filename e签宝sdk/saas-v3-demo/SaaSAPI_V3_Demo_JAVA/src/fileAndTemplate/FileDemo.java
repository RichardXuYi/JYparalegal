package fileAndTemplate;

import cn.tsign.hz.comm.EsignFileBean;
import cn.tsign.hz.comm.EsignHttpHelper;
import cn.tsign.hz.comm.EsignHttpResponse;
import cn.tsign.hz.enums.EsignHeaderConstant;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * description 文件服务
 * @author 语冰
 * datetime 2022年8月10日上午09:43:24
 */
public class FileDemo extends Exception {

	private static String eSignHost= EsignDemoConfig.EsignHost;
	private static String eSignAppId=EsignDemoConfig.EsignAppId;
	private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;

	public static void main(String[] args) throws EsignDemoException, InterruptedException {

		String rootFolder = new File("").getAbsolutePath();
		//获取文件id以及文件上传地址
		String filePath=rootFolder + File.separator + "pdf" + File.separator + "合同模板.pdf";
		EsignHttpResponse getUploadUrl = getUploadUrl(filePath);
		Gson gson = new Gson();
		JsonObject getUploadUrlJsonObject = gson.fromJson(getUploadUrl.getBody(), JsonObject.class);
		JsonObject data = getUploadUrlJsonObject.getAsJsonObject("data");
		//文件id后续发起签署使用
		String fileId =data.get("fileId").getAsString();
		String fileUploadUrl =data.get("fileUploadUrl").getAsString();
		System.out.println("获取文件id以及文件上传地址成功，文件id:"+fileId);
		System.out.println("上传链接"+fileUploadUrl);

		//文件上传
		EsignHttpResponse uploadFileResponse = uploadFile(fileUploadUrl,filePath);
		JsonObject uploadFileResponseJsonObject = gson.fromJson(uploadFileResponse.getBody(), JsonObject.class);
		String code = uploadFileResponseJsonObject.get("errCode").getAsString();
		System.out.println("文件上传成功，状态码:"+code);

		//文件上传成功后文件会有一个异步处理过程，建议轮询文件状态，正常后发起签署
		//查询文件上传状态
		int i=0;
		while(i<3) {
			EsignHttpResponse fileStatus = getFileStatus(fileId);
			JsonObject fileStatusJsonObject = gson.fromJson(fileStatus.getBody(), JsonObject.class);
			String status = fileStatusJsonObject.getAsJsonObject("data").get("fileStatus").getAsString();
			System.out.println(String.format("查询文件状态执行第%s次",i+1));
			if("2".equalsIgnoreCase(status)||"5".equalsIgnoreCase(status)){//查询状态为2或者5代表文件准备完成
				System.out.println("文件准备完成");
				break;
			}
			System.out.println("文件未准备完成,等待两秒重新查询");
			TimeUnit.SECONDS.sleep(2);
			i++;
		}
	}

	/**
	 * 获取文件上传地址
	 * @return
	 */
	public static EsignHttpResponse getUploadUrl(String filePath) throws EsignDemoException {
		//自定义的文件封装类，传入文件地址可以获取文件的名称大小,文件流等数据
		EsignFileBean esignFileBean = new EsignFileBean(filePath);
		String apiaddr="/v3/files/file-upload-url";
		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm="{\n" +
				"    \"contentMd5\": \""+esignFileBean.getFileContentMD5()+"\",\n" +
				"    \"fileName\":\""+esignFileBean.getFileName()+"\"," +
				"    \"fileSize\": "+esignFileBean.getFileSize()+",\n" +
				"    \"convertToPDF\": false,\n" +
				"    \"contentType\": \""+ EsignHeaderConstant.CONTENTTYPE_STREAM.VALUE()+"\"\n" +
				"}";
		//请求方法
		EsignRequestType requestType= EsignRequestType.POST;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}
	/**
	 * 文件流上传
	 * @return
	 */
	public static EsignHttpResponse uploadFile(String uploadUrl,String filePath) throws EsignDemoException {
		//根据文件地址获取文件contentMd5
		EsignFileBean esignFileBean = new EsignFileBean(filePath);
		//请求方法
		EsignRequestType requestType= EsignRequestType.PUT;
		return EsignHttpHelper.doUploadHttp(uploadUrl,requestType,esignFileBean.getFileBytes(),esignFileBean.getFileContentMD5(), EsignHeaderConstant.CONTENTTYPE_STREAM.VALUE(),true);
	}

	/**
	 * 获取文件上传状态
	 */
	public static EsignHttpResponse getFileStatus(String fileId) throws EsignDemoException {
		String apiaddr="/v3/files/"+fileId;

		//请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
		String jsonParm=null;
		//请求方法
		EsignRequestType requestType= EsignRequestType.GET;
		//生成签名鉴权方式的的header
		Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
		//发起接口请求
		return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
	}
}
