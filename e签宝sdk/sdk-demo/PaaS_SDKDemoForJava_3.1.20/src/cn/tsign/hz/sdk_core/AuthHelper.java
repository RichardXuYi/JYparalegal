package cn.tsign.hz.sdk_core;

import cn.tsign.hz.exception.DefineException;
import com.google.gson.Gson;
import com.timevale.esign.paas.tech.bean.request.*;
import com.timevale.esign.paas.tech.bean.result.*;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.AuthService;

import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description SDK授权服务辅助类
 */
public class AuthHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHelper.class);
    private ServiceClient serviceClient;
    private AuthService authService;

    public AuthHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
        this.authService = serviceClient.authService();
    }
    // -----------------------------------公有方法 start-------------------------------------------


    /**
     * description 【线上】发起企业授权书签署任务
     */
    public String createAuth(OnlineCreateAuthParam onlineCreateAuthParam)  throws DefineException {
        OnlineCreateAuthResult onlineCreateAuthResult = authService.createAuth(onlineCreateAuthParam);
        if (onlineCreateAuthResult.getErrCode() != 0) {
            LOGGER.info("发起企业授权失败：errCode = {},msg = {}",
                    onlineCreateAuthResult.getErrCode(), onlineCreateAuthResult.getMsg());
        } else {
            if (onlineCreateAuthResult.getAuthId() != null){
                LOGGER.info("发起企业静默签授权成功:authId={},请妥善保管authId以便后续签署场景关联", onlineCreateAuthResult.getAuthId());
                System.out.println("发起企业静默签授权的全部响应参数:" +  new Gson().toJson(onlineCreateAuthResult));
            }
            else {
                LOGGER.info("发起企业静默签授权成功:authId={},请妥善保管authId以便后续签署场景关联", onlineCreateAuthResult.getAuthIdByType());
                System.out.println("发起企业静默签授权的全部响应参数:" + new Gson().toJson(onlineCreateAuthResult));
            }
        }
        return onlineCreateAuthResult.getAuthId();
    }

    /**
     * description 【线上】发起企业法定代表人授权书签署任务
     */
    public String createLegalAuth(OnlineCreateLegalRepAuthParam onlineCreateLegalRepAuthParam)  throws DefineException {
        OnlineCreateAuthResult onlineCreateAuthResult = authService.createLegalAuth(onlineCreateLegalRepAuthParam);
        if (onlineCreateAuthResult.getErrCode() != 0) {
            LOGGER.info("发起企业法定代表人授权失败：errCode = {},msg = {}",
                    onlineCreateAuthResult.getErrCode(), onlineCreateAuthResult.getMsg());
        } else {
            if (onlineCreateAuthResult.getAuthId() != null){
                LOGGER.info("发起企业法定代表人授权成功:authId={},请妥善保管authId以便后续签署场景关联", onlineCreateAuthResult.getAuthId());
                System.out.println("发起企业法定代表人授权的全部响应参数:" +  new Gson().toJson(onlineCreateAuthResult));
            }
            else {
                LOGGER.info("发起企业法定代表人授权成功:authId={},请妥善保管authId以便后续签署场景关联", onlineCreateAuthResult.getAuthIdByType());
                System.out.println("发起企业法定代表人授权的全部响应参数:" + new Gson().toJson(onlineCreateAuthResult));
            }
        }
        return onlineCreateAuthResult.getAuthId();
    }




    /**
     * description 【线上】获取授权签署任务链接
     */

    public String getAuthFlowUrl(AuthFlowSignUrlParam authFlowSignUrlParam)  throws DefineException {
        AuthFlowSignUrlResult getAuthFlowUrlResult = authService.getAuthFlowUrl(authFlowSignUrlParam);
        if (getAuthFlowUrlResult.getErrCode() != 0) {
            LOGGER.info("获取授权流程签署链接失败：errCode={},msg={}",
                    getAuthFlowUrlResult.getErrCode(), getAuthFlowUrlResult.getMsg());
        } else {
            LOGGER.info("获取授权流程签署链接成功，授权原始长链接longUrl={}", getAuthFlowUrlResult.getLongUrl());
            System.out.println("获取授权流程签署链接成功" + JSONObject.fromObject(getAuthFlowUrlResult));
        }
        return getAuthFlowUrlResult.getLongUrl();
    }

    /**
     * description 【线下】发起企业授权书签署任务
     */
    public String createAuthOffline(OfflineCreateAuthParam offlineCreateAuthParam)  throws DefineException {
        OfflineCreateAuthResult offlineCreateAuthResult = authService.createAuthOffline(offlineCreateAuthParam);
        if (offlineCreateAuthResult.getErrCode() != 0) {
            LOGGER.info("发起企业静默签授权失败：errCode = {},msg = {}",
                    offlineCreateAuthResult.getErrCode(), offlineCreateAuthResult.getMsg());
        } else {
            LOGGER.info("发起企业静默签授权成功:authId={},请妥善保管authId以便后续签署场景关联，授权文件下载链接fileUrl={}", offlineCreateAuthResult.getAuthId(),offlineCreateAuthResult.getFileUrl());
            System.out.println("发起企业静默签授权" + JSONObject.fromObject(offlineCreateAuthResult));
        }
        return offlineCreateAuthResult.getAuthId();
    }

    /**
     * description 【线下】上传已盖章授权书文件
     */
    public void uploadAuthFile(UploadAuthFileParam uploadAuthFileParam)  throws DefineException {
        Result result = authService.uploadAuthFile(uploadAuthFileParam);
        if (result.getErrCode() != 0) {
            LOGGER.info("上传已盖章授权书文件失败：errCode = {},msg = {}",
                    result.getErrCode(), result.getMsg());
        } else {
            LOGGER.info("上传已盖章授权书成功：errCode = {},msg = {}");
            System.out.println("上传已盖章授权书成功" + JSONObject.fromObject(result));
        }
    }


    /**
     * description 获取用户授权结果（按授权ID查询）
     */
    public int queryAuth(String authId) throws DefineException {
        AuthInfoResult authInfoResult = authService.queryAuth(authId);
        if (authInfoResult.getErrCode() != 0) {
            LOGGER.info("获取用户授权结果（按授权ID查询）失败：errCode={},msg={}",
                    authInfoResult.getErrCode(), authInfoResult.getMsg());
        } else {
            LOGGER.info("获取用户授权结果（按授权ID查询）成功，授权状态status={},授权说明authDes={}", authInfoResult.getStatus(), authInfoResult.getAuthDes());
            System.out.println("获取用户授权结果（按授权ID查询）成功" + JSONObject.fromObject(authInfoResult));
        }
        return authInfoResult.getStatus();//授权状态，1-进行中 2-授权成功 3-授权失败 4-取消授权
    }


    /**
     * description 获取用户有效授权记录（按证件号查询）
     */
    public String queryAuthByIdno(AuthEffectiveInfoParam authEffectiveInfoParam) throws DefineException {
        AuthEffectiveInfoResult authEffectiveInfoResult = authService.queryEffectiveAuthInfo(authEffectiveInfoParam);
        if (authEffectiveInfoResult.getErrCode() != 0) {
            LOGGER.info("获取用户有效授权记录（按证件号查询）失败：errCode={},msg={}",
                    authEffectiveInfoResult.getErrCode(), authEffectiveInfoResult.getMsg());
        } else {
            LOGGER.info("获取用户有效授权记录（按证件号查询）成功，有效的授权ID={}，授权结束时间={}", authEffectiveInfoResult.getAuthId(), authEffectiveInfoResult.getAuthEndTime());
            System.out.println("获取用户有效授权记录（按证件号查询）成功" + JSONObject.fromObject(authEffectiveInfoResult));
        }
        return authEffectiveInfoResult.getAuthId();//有效的授权ID
    }


    /**
     * description 取消授权
     */
    public void cancelAuth(String authId) throws DefineException {
        Result result = authService.cancel(authId);
        if (result.getErrCode() != 0) {
            LOGGER.info("取消授权失败：code={},msg={}",
                    result.getErrCode(), result.getMsg());
        } else {
            LOGGER.info("取消授权成功");
            System.out.println("取消授权成功" + JSONObject.fromObject(result));
        }
    }


    // -----------------------------------公有方法  end---------------------------------------------

    // -----------------------------------getter setter start---------------------------------------
    public ServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }
    // -----------------------------------getter setter end----------------------------------------

}
