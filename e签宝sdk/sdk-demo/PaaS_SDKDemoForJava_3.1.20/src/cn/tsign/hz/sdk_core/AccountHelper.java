package cn.tsign.hz.sdk_core;

import java.text.MessageFormat;
import com.timevale.esign.paas.tech.bean.request.*;
import com.timevale.esign.paas.tech.bean.result.AccountInfoResult;
import com.timevale.esign.paas.tech.bean.result.AddAccountResult;
import com.timevale.esign.paas.tech.bean.result.Result;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.AccountService;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.tsign.hz.exception.DefineException;

/**
 * description SDK签署账号服务辅助类
 */

public class AccountHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountHelper.class);
    private ServiceClient serviceClient;
    private AccountService acctService;

    public AccountHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
        this.acctService = serviceClient.accountService();
    }
    // -----------------------------------公有方法 start-------------------------------------------


    /**
     * description 创建个人签署账号
     */
    public String addPsnAccount(PersonParam person)
            throws DefineException {
        AddAccountResult acctRst = acctService.addAccount(person);
        return castAddAccount(acctRst, "个人");
    }


    /**
     * description 创建企业签署账号
     */
    public String addOrgAccount(OrganizeParam organize)
            throws DefineException {
        AddAccountResult acctRst = acctService.addAccount(organize);
        return castAddAccount(acctRst, "企业");
    }


    /**
     * description 更新个人签署账号信息
     */
    public void updatePsnAccount(UpdatePersonParam person)
            throws DefineException {
        Result rst = acctService.updateAccount(person);
        castUpdateAcct(rst, person.getAccountId(), "个人");
    }


    /**
     * description 更新企业签署账号信息
     */
    public void updateOrgAccount(UpdateOrganizeParam organize)
            throws DefineException {
        Result rst = acctService.updateAccount(organize);
        castUpdateAcct(rst, organize.getAccountId(), "企业");
    }


    /**
     * description 注销签署账号
     */
    public void deleteAccount(String accountId)
            throws DefineException {
        Result rst = acctService.deleteAccount(accountId);
        if (rst.getErrCode() != 0) {
            throw new DefineException(
                    MessageFormat.format("注销签署账号失败：errCode={0},msg={1}",
                            rst.getErrCode(), rst.getMsg()));
        }
        LOGGER.info("注销签署账号成功，accountId={}", accountId);
        System.out.println("注销成功" + JSONObject.fromObject(rst));
    }


    /**
     * description  根据证件号查询签署账号信息
     */
    public void getAccountInfoByIdNo(QueryAccountInfoParam accountInfo) throws DefineException {
        AccountInfoResult acctRst = acctService.getAccountInfo(accountInfo);

        if (acctRst.getErrCode() != 0) {
            throw new DefineException(
                    MessageFormat.format("查询账号信息失败：errCode={0},msg={1}", acctRst.getErrCode(), acctRst.getMsg()));
        }
        LOGGER.info("查询账号成功：账号accountId = {},名称name={},证件号idNo={},"
                        + "证件类型idNoType={},账号类型accountType={}",
                acctRst.getAccountId(), acctRst.getName(), acctRst.getIdNo(),
                acctRst.getIdNoType(), acctRst.getAccountType());
        System.out.println("查询成功" + JSONObject.fromObject(acctRst));
    }

    /**
     * description  根据账号ID查询签署账号信息
     */
    public void getAccountInfoByAccountId(String accountId, boolean encrypt) throws DefineException {
        AccountInfoResult acctRst = acctService.getAccountInfo(accountId, false);

        if (acctRst.getErrCode() != 0) {
            throw new DefineException(
                    MessageFormat.format("查询账号信息失败：errCode={0},msg={1}", acctRst.getErrCode(), acctRst.getMsg()));
        }
        LOGGER.info("查询账号成功：账号accountId = {},名称name={},证件号idNo={},"
                        + "证件类型idNoType={},账号类型accountType={}",
                acctRst.getAccountId(), acctRst.getName(), acctRst.getIdNo(),
                acctRst.getIdNoType(), acctRst.getAccountType());
        System.out.println("查询成功" + JSONObject.fromObject(acctRst));
    }


    // -----------------------------------公有方法  end---------------------------------------------

    // -----------------------------------私有方法  start-------------------------------------------


    /**
     * description 创建签署账号返回结果处理
     */
    private String castAddAccount(AddAccountResult acctRst, String typeMsg) throws DefineException {
        if (acctRst.getErrCode() != 0) {
            throw new DefineException(MessageFormat.format("创建账号失败：errCode = {0},msg = {1}", typeMsg,
                    acctRst.getErrCode(), acctRst.getMsg()));
        }
        LOGGER.info("创建{}账号成功:accountId={},请妥善保管AccountId以便后续签署场景使用", typeMsg, acctRst.getAccountId());
        System.out.println("创建成功" + JSONObject.fromObject(acctRst));
        return acctRst.getAccountId();
    }

    /**
     * description 更新签署账号返回结果处理
     */
    private void castUpdateAcct(Result rst, String accountId, String typeMsg) throws DefineException {
        if (rst.getErrCode() != 0) {
            throw new DefineException(
                    MessageFormat.format(
                            "更新{0}账号失败：errCode={0},msg={1}", typeMsg, rst.getErrCode(), rst.getMsg()));
        }
        LOGGER.info("更新{}账号成功,accountId = {},请妥善保管AccountId以便后续签署场景使用", typeMsg, accountId);
        System.out.println("更新成功" + JSONObject.fromObject(rst));
    }


    // -----------------------------------私有方法  end---------------------------------------------

    // -----------------------------------getter setter start---------------------------------------
    public ServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public AccountService getAcctService() {
        return acctService;
    }

    public void setAcctService(AccountService acctService) {
        this.acctService = acctService;
    }
    // -----------------------------------getter setter end----------------------------------------

}
