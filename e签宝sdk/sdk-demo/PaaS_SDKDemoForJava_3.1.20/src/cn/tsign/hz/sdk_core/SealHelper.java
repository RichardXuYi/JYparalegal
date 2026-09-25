package cn.tsign.hz.sdk_core;

import java.text.MessageFormat;
import java.util.List;
import com.timevale.esign.paas.tech.bean.result.AddSealResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.enums.OrganizeTemplateType;
import com.timevale.esign.paas.tech.enums.PersonTemplateType;
import com.timevale.esign.paas.tech.enums.SealColor;
import com.timevale.esign.paas.tech.enums.StampRuleEnum;
import com.timevale.esign.paas.tech.service.TemplateSealService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import cn.tsign.hz.exception.DefineException;

/**
 * description SDK印章辅助类
 */

public class SealHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(SealHelper.class);
	private TemplateSealService templateSealService;
	private ServiceClient serviceClient;
	public SealHelper(ServiceClient serviceClient) {
		this.serviceClient = serviceClient;
		templateSealService = serviceClient.templateSealService();
	}

	//-----------------------------------公有方法 start-------------------------------------------




	/**
	 *
	 * description 创建个人模板印章
	 */
	public String createPersonalSeal(String accountId,String name,PersonTemplateType type, SealColor color,
								  StampRuleEnum stampRule) throws DefineException{
		AddSealResult sealRst = templateSealService.createPsnSeal(accountId, name,type, color, stampRule);
		return castAddSealRst(sealRst, "个人");
	}



	/**
	 *
	 * description 创建企业模板印章
	 */
	public String createOfficialSeal(String accountId,OrganizeTemplateType type, String roundText,
											List<String> hTexts, String qText, SealColor color)throws DefineException{
		AddSealResult sealRst = templateSealService.createOrgSeal(accountId, type, roundText,
				hTexts, qText, color);
		return castAddSealRst(sealRst, "企业");
	}


	//-----------------------------------公有方法 end---------------------------------------------


	//-----------------------------------私有方法 start-------------------------------------------

	/**
	 *
	 * description 封装创建模板印章方法
	 * 			{@link AddSealResult}创建个人/企业模板印章返回的对象
	 * @param typeMsg
	 * 			{@link String}个人模板印章/企业模板印章
	 * @return sealData
	 * 			{@link String}最终生成的电子印章图片Base64数据
	 */
	private String castAddSealRst(AddSealResult sealRst, String typeMsg)
			throws DefineException{
		if(sealRst.getErrCode() != 0){
			throw new DefineException(MessageFormat.format("创建{0}失败：errCode={1},msg={2}",
					typeMsg,sealRst.getErrCode(),sealRst.getMsg()));
		}
		LOGGER.info("创建{}模板印章成功：印章图片Base64数据sealData={}，可将该sealData保存到贵司数据库以便日后直接使用",typeMsg,sealRst.getSealData());
		//System.out.println("创建企业模板印章成功," + JSONObject.fromObject(sealRst));
		return sealRst.getSealData();
	}

	//-----------------------------------私有方法 end---------------------------------------------

	//-----------------------------------getter 、setter 方法 start-------------------------------
	public ServiceClient getServiceClient() {
		return serviceClient;
	}
	public void setServiceClient(ServiceClient serviceClient) {
		this.serviceClient = serviceClient;
	}
	public TemplateSealService getTemplateSealService() {
		return templateSealService;
	}
	public void setTemplateSealService(TemplateSealService templateSealService) {
		this.templateSealService = templateSealService;
	}
	//-----------------------------------getter 、setter 方法 end---------------------------------

}
