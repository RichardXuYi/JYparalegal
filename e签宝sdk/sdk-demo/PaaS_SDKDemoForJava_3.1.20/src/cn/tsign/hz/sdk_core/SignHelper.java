package cn.tsign.hz.sdk_core;

import com.timevale.esign.paas.tech.bean.request.*;
import com.timevale.esign.paas.tech.bean.result.BatchFileDigestSignResult;
import com.timevale.esign.paas.tech.bean.result.FileDigestSignResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.service.PlatformSignService;
import com.timevale.esign.paas.tech.service.UserSignService;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;
import java.util.List;

/**
 * description SDK签署辅助类
 */
public class SignHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(SignHelper.class);
	private PlatformSignService platformSignService;
	private UserSignService userSignService;
	private ServiceClient serviceClient;

	public SignHelper(ServiceClient serviceClient) {
		this.serviceClient = serviceClient;
		this.platformSignService = serviceClient.platformSignService();
		this.userSignService = serviceClient.userSignService();
	}

	// -----------------------------------公有方法 start-------------------------------------------


	/**
	 *
	 * description 平台自身PDF文件签署
	 */
	public void platformSign(PlatformSignParam platformSignParam)
			throws DefineException {
		FileDigestSignResult signRst = platformSignService.platformSign(platformSignParam);
		castSignRst(signRst, "平台自身");
	}



	/**
	 *
	 * description 企业用户PDF文件签署
	 */
	public void orgSign(OrgSignParam orgSignParam) throws DefineException{
		FileDigestSignResult signRst = userSignService.orgSign(orgSignParam);
		castSignRst(signRst, "企业用户");
	}


	/**
	 *
	 * description 个人用户PDF文件签署
	 */
	public void personSign(PersonSignParam personSignParam)throws DefineException{
		FileDigestSignResult signRst = userSignService.personSign(personSignParam);
		castSignRst(signRst,"个人用户");
	}


	/**
	 *
	 * description 平台自身PDF文件批量签署
	 */
	public void platformBatchSign(List<PlatformSignParam> batchPlatformSign)throws DefineException{
		BatchFileDigestSignResult batchSignRst = platformSignService.batchPlatformSign(batchPlatformSign);
		if (batchSignRst.getErrCode() != 0)
			System.out.println("平台自身批量签署失败：" + JSONObject.fromObject(batchSignRst));
		else
			System.out.println("平台自身批量签署成功" + JSONObject.fromObject(batchSignRst));
	}

	/**
	 *
	 * description 企业用户PDF文件批量签署
	 */
	public void orgBatchSign(BatchOrgSignParam batchOrgSignParam)throws DefineException{
		BatchFileDigestSignResult batchSignRst = userSignService.batchOrgSign(batchOrgSignParam);
		if (batchSignRst.getErrCode() != 0)
			System.out.println("企业用户批量签署失败：" + JSONObject.fromObject(batchSignRst));
		else
			System.out.println("企业用户批量签署成功" + JSONObject.fromObject(batchSignRst));
	}


	/**
	 *
	 * description 个人用户PDF文件批量签署
	 */
	public void personBatchSign(BatchPersonSignParam batchPersonSignParam)throws DefineException{
		BatchFileDigestSignResult batchSignRst = userSignService.batchPersonSign(batchPersonSignParam);
		if (batchSignRst.getErrCode() != 0)
			System.out.println("个人用户批量签署失败：" + JSONObject.fromObject(batchSignRst));
		else
			System.out.println("个人用户批量签署成功" + JSONObject.fromObject(batchSignRst));
	}





	// -----------------------------------公有方法  end---------------------------------------------

	// -----------------------------------私有方法  start-------------------------------------------

	/**
	 *
	 * description 单文件签署结果处理
	 */
	private void castSignRst(FileDigestSignResult signRst, String typeMsg) throws DefineException {

		if (signRst.getErrCode() != 0) {
			throw new DefineException(
					MessageFormat.format("{0}签署失败: errCode = {1},msg = {2}",
							typeMsg, signRst.getErrCode(), signRst.getMsg()));
		}
	    if (signRst.getStream() != null) {
			signLog(signRst.getSignServiceId(), signRst.getDstFilePath(), typeMsg, true);
			//String outSignedPdfPath = "/Users/zhanghe/Downloads/Signed_pdf.pdf";
			//FileHelper.saveFileByStream(signRst.getStream(), outSignedPdfPath);// 将PDF文件字节流保存为本地PDF文件
			System.out.println("签署成功" + JSONObject.fromObject(signRst));
		}else if(signRst.getStream() == null) {
			signLog(signRst.getSignServiceId(), signRst.getDstFilePath(), typeMsg, false);
			System.out.println("签署成功" + JSONObject.fromObject(signRst));
		}

	    }


	/**
	 *
	 * description 记录签署后打印日志
	 */
	private void signLog(String signServiceId,String dstFilePath, String typeMsg, boolean streamSign) {
		LOGGER.info("{}签署成功:SignServiceId [{}],"
				+ "请妥善保管签署记录ID(SignServiceId)", typeMsg, signServiceId);

		if (streamSign) {
			LOGGER.info("{}签署成功, 请妥善保管签署后的文件字节流",typeMsg);
		} else {
			LOGGER.info("{}签署成功后的PDF文件存放路径：{}，请妥善保管签署后的文件",typeMsg, dstFilePath);
		}
	}


	// -----------------------------------私有方法  end---------------------------------------------

	// -----------------------------------getter 、setter 方法  start-------------------------------
	public ServiceClient getServiceClient() {
		return serviceClient;
	}

	public void setServiceClient(ServiceClient serviceClient) {
		this.serviceClient = serviceClient;
	}

	public PlatformSignService getPlatformSignService() {
		return platformSignService;
	}

	public void setPlatformSignService(PlatformSignService platformSignService) {
		this.platformSignService = platformSignService;
	}

	public UserSignService getUserSignService() {
		return userSignService;
	}

	public void setUserSignService(UserSignService userSignService) {
		this.userSignService = userSignService;
	}

	// -----------------------------------getter 、setter 方法  end---------------------------------

}
