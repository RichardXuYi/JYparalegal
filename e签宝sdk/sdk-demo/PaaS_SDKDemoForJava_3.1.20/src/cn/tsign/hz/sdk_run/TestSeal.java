package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.*;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.enums.OrganizeTemplateType;
import com.timevale.esign.paas.tech.enums.PersonTemplateType;
import com.timevale.esign.paas.tech.enums.SealColor;
import com.timevale.esign.paas.tech.enums.StampRuleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;

/***
 * description: 印章服务
 */


public class TestSeal {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSeal.class);
    private static SealHelper sealHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            sealHelper = new SealHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }


    public static void main(String[] args) throws DefineException {


        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：创建个人模板印章<=====");
                createPersonalSeal();
                break;
            case 1:
                LOGGER.info("====>场景演示：创建企业模板印章<=====");
                createOfficialSeal();
                break;
            default:
                LOGGER.info("====>请选择应用场景<=====");
                break;
        }


    }

    /**
     * 创建个人模板印章
     */
    private static String createPersonalSeal() throws DefineException {
        String accountId = "";//个人账号ID（创建个人签署账号接口返回）,与name必须二选一传值
        String name = "张三";// 用户姓名（待创建印章的内容文本）,与accountId必须二选一传值
        PersonTemplateType type = PersonTemplateType.BORDERLESS; // 印章模板类型,可选：SQUARE-正方形印章 | RECTANGLE-矩形印章 | BORDERLESS-无框矩形印章
        SealColor color = SealColor.RED;// 印章颜色：RED-红色 | BLUE-蓝色 | BLACK-黑色
        StampRuleEnum stampRule = StampRuleEnum.SEAL_NONE;//印章是否加“印”规则：SEAL_NONE，无‘印’|  SEAL_ONE，带‘印’|  SEAL_TWO，带‘之印’
        return sealHelper.createPersonalSeal(accountId, name, type, color, stampRule);
    }


    /**
     * 创建企业模板印章
     */
    private static String createOfficialSeal() throws DefineException {
        String accountId = "";//企业账号ID（创建企业签署账号接口返回）,与roundText必须二选一传值
        OrganizeTemplateType type = OrganizeTemplateType.STAR; // 印章模板类型,可选：STAR-标准公章 | DEDICATED-圆形无五角星章 | OVAL-椭圆形印章
        SealColor color = SealColor.RED;// 印章颜色：RED-红色 | BLUE-蓝色 | BLACK-黑色
        String roundText = "测试企业用户有限公司";// 印章里的企业名称（生成印章中的上弦文）,与accountId必须二选一传值
        List<String> hTexts = Arrays.asList("合同专用章");// hText 生成印章中的横向文内容 如“合同专用章、财务专用章”
        String qText = "";// qText 生成印章中的下弦文内容，公章防伪码（一般为13位数字），没有可以传空
        return sealHelper.createOfficialSeal(accountId, type, roundText,
                hTexts, qText, color);
    }
}
