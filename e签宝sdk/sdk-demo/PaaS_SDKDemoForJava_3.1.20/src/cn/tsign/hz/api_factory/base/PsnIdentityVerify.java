package cn.tsign.hz.api_factory.base;

import cn.tsign.hz.api_factory.indivIdentity.*;

/**
 * 核身认证个人认证功能类

 */
public class PsnIdentityVerify {

    /**
     * 获取个人核身认证地址
     * @return
     */
    public static IndivAuthUrl indivAuthUrl(){
        return new IndivAuthUrl();
    }


    /**
     * 发起个人刷脸核身认证
     * @param name
     * @param idNo
     * @param faceauthMode
     * @param callbackUrl
     * @return
     */
    public static FaceIdentity faceIdentity(String name, String idNo, String faceauthMode, String callbackUrl){
        return new FaceIdentity(name,idNo,faceauthMode,callbackUrl);
    }

    /**
     * 查询个人刷脸状态
     * @param flowId
     * @return
     */
    public static QryFaceStatus qryFaceStatus(String flowId){
        return new QryFaceStatus(flowId);
    }



    /**
     * 发起运营商3要素核身认证
     * @param name
     * @param idNo
     * @param mobileNo
     * @return
     */
    public static IndividualTelecom3Factors individualTelecom3Factors(String name, String idNo, String mobileNo,int source){
        return new IndividualTelecom3Factors(name,idNo,mobileNo, source);
    }

    /**
     * 运营商短信验证码校验
     * @param flowId
     * @param authcode
     * @return
     */
    public static Tel3FactorsCodeVerify tel3FactorsCodeVerify(String flowId, String authcode){
        return new Tel3FactorsCodeVerify(flowId, authcode);
    }


    /**
     * 发起银行4要素核身认证
     * @param name
     * @param certType
     * @param idNo
     * @param mobileNo
     * @param bankCardNo
     * @return
     */
    public static IndividualBankCard4Factors individualBankCard4Factors(String name, String certType, String idNo, String mobileNo, String bankCardNo, int source){
        return new IndividualBankCard4Factors(name, certType, idNo, mobileNo, bankCardNo, source);
    }

    /**
     * 银行预留手机号验证码校验
     * @param flowId
     * @param authcode
     * @return
     */
    public static BankCard4FactorsCodeVerify bankCard4FactorsCodeVerify(String flowId, String authcode){
        return new BankCard4FactorsCodeVerify(flowId, authcode);
    }

}
