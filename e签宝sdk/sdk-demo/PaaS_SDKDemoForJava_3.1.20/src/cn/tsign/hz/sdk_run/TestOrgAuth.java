package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.AuthHelper;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.AuthEffectiveInfoParam;
import com.timevale.esign.paas.tech.bean.request.OnlineCreateAuthParam;
import com.timevale.esign.paas.tech.bean.request.OnlineCreateLegalRepAuthParam;
import com.timevale.esign.paas.tech.bean.result.AuthEffectiveInfoResult;
import com.timevale.esign.paas.tech.bean.result.OnlineCreateAuthResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.bean.request.AuthFlowSignUrlParam;
import com.timevale.esign.paas.tech.enums.CertTimeEnum;
import com.timevale.esign.paas.tech.enums.IdNoTypeEnum;
import com.timevale.esign.paas.tech.enums.OrganRegTypeEnum;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/***
 * description: 企业签署授权服务
 */


public class TestOrgAuth {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOrgAuth.class);
    private static AuthHelper authHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            authHelper = new AuthHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

    public static void main(String[] args) throws DefineException {


        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：【线上】发起企业授权书签署任务<=====");
                createAuth();
                break;
            case 1:
                LOGGER.info("====>场景演示：【线上】发起企业法定代表人授权书签署任务<=====");
                createLegalAuth();
                break;
            case 2:
                LOGGER.info("====>场景演示：【线上】获取授权签署任务链接<=====");
                getAuthFlowUrl();
                break;
            case 3:
                LOGGER.info("====>场景演示：获取用户授权结果（按授权ID查询）<=====");
                queryAuth();
                break;
            case 4:
                LOGGER.info("====>场景演示：获取用户有效授权记录（按证件号查询）<=====");
                queryAuthByIdno();
                break;
            case 5:
                LOGGER.info("====>场景演示：取消授权<=====");
                cancelAuth();
                break;
            default:
                LOGGER.info("====>请选择应用场景<=====");
                break;

        }
    }

    /**
     * 【线上】发起企业授权书签署任务
     */
    private static String createAuth() throws DefineException {
        OnlineCreateAuthParam onlineCreateAuthParam = new OnlineCreateAuthParam();//创建授权参数
        onlineCreateAuthParam.setOrganizeId("1EEFF9E1*******AFC0D45D1F");//授权方企业账号ID
        onlineCreateAuthParam.setPersonId("11DAE******F7069B");//授权方经办人个人账号ID
        onlineCreateAuthParam.setAuthType(1);//授权模式，默认为 1（授权至平台）：1 - 授权至平台，personId无需传递；2 - 授权至经办人，personId必传
        onlineCreateAuthParam.setContact("153*****50");//代委托方企业签署本授权书的经办人的联系方式：手机号或者邮箱
        onlineCreateAuthParam.setTransactorName("张三");//代委托方企业签署本授权书的经办人的姓名
        onlineCreateAuthParam.setIdentityVerify(true);//是否在发起阶段校验经办人实名信息一致性（当开发者指定的经办人信息与该联系方式contact在e签宝已有的身份信息不一致时如何处理），默认：false
        onlineCreateAuthParam.setLegalRepName("李四");//授权方企业的法定代表人姓名
        onlineCreateAuthParam.setSendNotice(true);//是否发送通知
        onlineCreateAuthParam.setSealScope("合同专用章");//自定义授权印章范围
        onlineCreateAuthParam.setFileType("物流条款");//自定义签署文件类型
        onlineCreateAuthParam.setNotifyUrl("http://XXXX/asyn/notify");//回调通知地址
        onlineCreateAuthParam.setRedirectUrl("https://open.esign.cn");//重定向跳转地址
        onlineCreateAuthParam.setCertTime(CertTimeEnum.THREEYEAR);//新版授权有效期（1年、2年、3年）
        //onlineCreateAuthParam.setValidDate(17085374740000L);//老版本授权有效期截止时间（截止到当日0点）
        return authHelper.createAuth(onlineCreateAuthParam);

    }

    /**
     * 【线上】发起企业法定代表人授权书签署任务
     */
    private static String createLegalAuth() throws DefineException {
        OnlineCreateLegalRepAuthParam onlineCreateLegalRepAuthParam = new OnlineCreateLegalRepAuthParam();//创建授权参数
        onlineCreateLegalRepAuthParam.setOrganizeId("CE6E87D4884*****CF615E63BFD9");//授权方企业账号ID
        onlineCreateLegalRepAuthParam.setName("测试法人");//法定代表人姓名
        onlineCreateLegalRepAuthParam.setIdNoType(IdNoTypeEnum.MAINLAND);//法定代表人身份证件类型
        onlineCreateLegalRepAuthParam.setIdNo("13040*****8395");//法定代表人证件号
        //onlineCreateLegalRepAuthParam.setPersonId("836534XXXXXX12345F4FAD90294432");//授权方经办人个人账号ID
        onlineCreateLegalRepAuthParam.setAuthType(1);//授权模式，默认为 1（授权至平台）：1 - 授权至平台，personId无需传递；2 - 授权至经办人，personId必传
        onlineCreateLegalRepAuthParam.setContact("198******22");//手机号或者邮箱
        onlineCreateLegalRepAuthParam.setSendNotice(true);//是否发送通知
        onlineCreateLegalRepAuthParam.setSealScope("法定代表人章");//自定义授权印章范围
        onlineCreateLegalRepAuthParam.setFileType("物流条款");//自定义签署文件类型
        onlineCreateLegalRepAuthParam.setNotifyUrl("http://notify.com.cn/callback/XXXXX");//回调通知地址
        onlineCreateLegalRepAuthParam.setRedirectUrl("https://open.esign.cn");//重定向跳转地址
        return authHelper.createLegalAuth(onlineCreateLegalRepAuthParam);

    }

    /**
     * 【线上】获取授权签署任务链接
     */
    private static String getAuthFlowUrl() throws DefineException {
        AuthFlowSignUrlParam authFlowSignUrlParam = new AuthFlowSignUrlParam();
        authFlowSignUrlParam.setAuthId("175*******3383");//授权流程ID
        authFlowSignUrlParam.setNeedLogin(false);//是否需要登录打开链接:true - 需登录打开链接，false - 免登录
        authFlowSignUrlParam.setRedirectUrl("https://open.esign.cn");//重定向跳转地址
        authFlowSignUrlParam.setClientType("ALL");//指定客户端类型，默认值 ：ALL ; H5 - 移动端适配 , PC - PC端适配 , ALL - 自动适配移动端或PC端
        return authHelper.getAuthFlowUrl(authFlowSignUrlParam);
    }


    /**
     * 获取用户授权结果（按授权ID查询）
     */
    private static int queryAuth() throws DefineException {
        String authId = "387237111111593889";
        return authHelper.queryAuth(authId);//授权状态，1-进行中 2-授权成功 3-授权失败 4-取消授权
    }


    /**
     * 获取用户有效授权记录（按证件号查询）
     */
    private static String queryAuthByIdno() throws DefineException {
        AuthEffectiveInfoParam authEffectiveInfoParam = new AuthEffectiveInfoParam();
        authEffectiveInfoParam.setAuthorizerType(1);
        authEffectiveInfoParam.setAuthType(1);
        authEffectiveInfoParam.setOrganizeIdNo("913020******15732");
        authEffectiveInfoParam.setOrganizeIdNoType(OrganRegTypeEnum.MERGE);
        //authEffectiveInfoParam.setLegalIdNo("130207199401011778");
        authEffectiveInfoParam.setLegalIdNoType(IdNoTypeEnum.MAINLAND);
        //authEffectiveInfoParam.setPersonId("37655E43BDB347829A9C20C4559B689E");
        return authHelper.queryAuthByIdno(authEffectiveInfoParam);//

    }

    /**
     * 取消授权
     */
    private static void cancelAuth() throws DefineException {
        String authId = "17********050";
        authHelper.cancelAuth(authId);
    }
}

