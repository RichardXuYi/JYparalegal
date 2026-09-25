package cn.tsign.hz.api_run;

import cn.tsign.hz.api_bean.ContextInfo;
import cn.tsign.hz.api_bean.IndivInfo;
import cn.tsign.hz.exception.DefineException;
import cn.tsign.hz.api_factory.Factory;
import cn.tsign.hz.api_factory.base.PsnIdentityVerify;
import cn.tsign.hz.api_factory.indivIdentity.*;
import cn.tsign.hz.api_factory.response.indivIdentity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 个人核身认证服务（不在SDK3.0-jar包内，属于独立的HTTP-API请求）
 * 身份核验认证服务接口请求-正式环境域名：https://openapi.esign.cn
 * 身份核验认证服务接口请求-沙箱环境域名：https://smlopenapi.esign.cn
 */

public class PsnApiVerifyDemo {

    static {

        String host = "https://smlopenapi.esign.cn"; //沙箱环境-API网关请求域名
       // String host = "https://openapi.esign.cn"; //正式环境-API网关请求域名
        String project_id = "7438807248";//应用id
        String project_scert = "982f8f78e6ce079fdbba9582489af101";//应用secret
        Factory.init(host, project_id, project_scert);//初始化，传入请求网关和应用id以及密钥,全局运行一次
        Factory.setDebug(true);//开启日志，测试阶段建议开启，方便记录数据，日志保存在根目录的log.txt文件中


    }

    public static void main(String[] args) throws DefineException {
        Logger logger = LoggerFactory.getLogger(PsnApiVerifyDemo.class);
        try {

            //请求参数：
            String name = "哈哈先生";//个人姓名
            String idNo = "36010219951009001X";//个人证件号
            String mobileNo = "198****2222";//手机号
            String bankCardNo = "6222********253";//银行卡号
            int source = 2;//信息比对来源.建议传2
            String callbackUrl = "1234566"; //认证完成以后前端需要跳转的页面地址
            String notifyUrl = "https://dev-open.ymbank.com/api/contract/esign/callback";//认证结束后后端异步通知地址

            /**
             * demo只提供基础案例调用参数，其他完整参数请按照对接文档按需传入
             * 以下分为纯API版本核身认证（无e签宝页面，三种认证方式独立对接）以及e签宝网页版核身认证（将三种认证方式合成到一个e签宝页面内）
             * 个人核身认证服务-对接指南：https://open.esign.cn/doc/opendoc/paas_sdk_3/ek6qs3kxlcwtxpiz
             */

            switch (0) {
                case 0:
                    logger.info("-----------------【人脸识别认证】个人核身 start-----------------");
                    FaceIdentity faceIdentity = PsnIdentityVerify.faceIdentity(name, idNo, "TENCENT", callbackUrl);
                    FaceIdentityResponse faceIdentityResponse = faceIdentity.execute();
                    String flowId0 = faceIdentityResponse.getData().getFlowId();
                    logger.info("！请注意保存-认证流程id：" + flowId0 +"，接下来可以在用户刷脸完跳转到贵司重定向地址后，调用查询个人刷脸状态接口主动查询用户刷脸结果：成功/失败");
                    logger.info("-----------------【人脸识别认证】个人核身 end-----------------");
                    break;
                case 1:
                    String flowId1="3839415858502306732";
                    logger.info("-----------------查询个人刷脸状态 start-----------------");
                    QryFaceStatus qryFaceStatus = PsnIdentityVerify.qryFaceStatus(flowId1);
                    qryFaceStatus.execute();
                    logger.info("-----------------查询个人刷脸状态 end-----------------");
                    break;

                 case 2:
                    logger.info("-----------------【手机号认证】运营商3要素核身 start-----------------");
                    IndividualTelecom3Factors telecom3Factors = PsnIdentityVerify.individualTelecom3Factors(name, idNo, mobileNo,source);
                    IndividualTelecom3FactorsResponse telecom3FactorsResponse = telecom3Factors.execute();
                    String flowId2 = telecom3FactorsResponse.getData().getFlowId();
                    logger.info("！请注意保存-认证流程id：" + flowId2 + "，接下来请调用【手机号认证】短信验证码校验接口进行短信验证码回填验证!");
                    logger.info("-----------------【手机号认证】运营商3要素核身 end-----------------");
                    break;
                case 3:

                    logger.info("-----------------【手机号认证】短信验证码校验 start-----------------");
                    String flowId3 = "3839419413191723918";
                    String authCode = "123456";//发起运营商三要素以后收到的短信验证码
                    Tel3FactorsCodeVerify tel3FactorsCodeVerify = PsnIdentityVerify.tel3FactorsCodeVerify(flowId3, authCode);
                    tel3FactorsCodeVerify.execute();
                    logger.info("-----------------【手机号认证】短信验证码校验 end-----------------");
                    break;

                case 4:
                    logger.info("-----------------【银行卡认证】银行卡4要素核身 start-----------------");
                    IndividualBankCard4Factors bankCard4Factors = PsnIdentityVerify.individualBankCard4Factors(name, "INDIVIDUAL_CH_IDCARD", idNo, mobileNo, bankCardNo,source);
                    IndividualBankCard4FactorsResponse bankCard4FactorsResponse =  bankCard4Factors.execute();
                    String flowId4 = bankCard4FactorsResponse.getData().getFlowId();
                    logger.info("！请注意保存-认证流程id：" + flowId4 + "，接下来请调用【银行卡认证】预留手机号校验接口进行短信验证码回填验证!");
                    logger.info("-----------------【银行卡认证】银行卡4要素核身 end-----------------");
                    break;
                case 5:
                    logger.info("-----------------【银行卡认证】预留手机号校验 start-----------------");
                    String flowId5 = "3839435409428319113";
                    authCode = "123456";//发起银行卡四要素以后手机收到的短信验证码
                    BankCard4FactorsCodeVerify bankCard4FactorsCodeVerify = PsnIdentityVerify.bankCard4FactorsCodeVerify(flowId5, authCode);
                    bankCard4FactorsCodeVerify.execute();
                    logger.info("-----------------【银行卡认证】预留手机号校验 end-----------------");
                    break;

                case 6:
                    logger.info("-----------------网页版-获取个人核身认证地址 start-----------------");
                    IndivInfo indivInfo = new IndivInfo();//个人实名认证的基本信息
                    indivInfo.setName(name);//个人姓名
                    indivInfo.setCertNo(idNo);//个人证件号

                    ContextInfo contextInfo = new ContextInfo();//业务方交互上下文信息
                    contextInfo.setRedirectUrl(callbackUrl);//认证完成以后前端需要跳转的页面地址
                    contextInfo.setNotifyUrl(notifyUrl);//认证结束后后端异步通知地址
                    contextInfo.setContextId("开发者自定义业务标识，例如：No000001");

                    IndivAuthUrl indivAuthUrl = PsnIdentityVerify.indivAuthUrl();
                    indivAuthUrl.setIndivInfo(indivInfo);
                    indivAuthUrl.setContextInfo(contextInfo);
                    IndivAuthUrlResponse indivAuthUrlResponse = indivAuthUrl.execute();
                    String flowId6 = indivAuthUrlResponse.getData().getFlowId();
                    logger.info("！请注意保存-认证流程id：" + flowId6);
                    logger.info("-----------------网页版-获取个人核身认证地址 end-----------------");
                    break;
                default:
                    logger.info("====>请选择应用场景<=====");
                    break;

            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("运行结束");
    }

}
