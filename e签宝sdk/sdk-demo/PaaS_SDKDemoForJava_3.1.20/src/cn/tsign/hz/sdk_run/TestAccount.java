package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_core.AccountHelper;
import com.timevale.esign.paas.tech.bean.request.*;
import com.timevale.esign.paas.tech.enums.AccountTypeEnum;
import com.timevale.esign.paas.tech.enums.AllIdNoTypeEnum;
import com.timevale.esign.paas.tech.enums.IdNoTypeEnum;
import com.timevale.esign.paas.tech.enums.OrganRegTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.client.ServiceClient;


/***
 * description: 签署账号管理
 */


public class TestAccount {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAccount.class);
    private static AccountHelper accountHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            accountHelper = new AccountHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

//--------------------------------公有方法 start-------------------------------------

    public static void main(String[] args) throws DefineException {

        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：创建个人签署账号<=====");
                addPsnAccount();// 返回个人用户标识账号-accountId,可将该accountId保存到贵司数据库以便日后直接使用,只创建一次即可
                break;
            case 1:
                LOGGER.info("====>场景演示：创建企业签署账号<=====");
                addOrgAccount();// 返回企业用户标识账号-accountId,可将该accountId保存到贵司数据库以便日后直接使用,只创建一次即可
                break;
            case 2:
                LOGGER.info("====>场景演示：更新个人签署账号信息<=====");
                updatePsnAccount();// 更新个人姓名
                break;
            case 3:
                LOGGER.info("====>场景演示：更新企业签署账号信息<=====");
                updateOrgAccount();// 更新企业名称
                break;
            case 4:
                LOGGER.info("====>场景演示：根据证件号获取签署账号信息<=====");
                getAccountInfoByIdNo();//根据证件号查询个人/企业用户名字、accountId
                break;
            case 5:
                LOGGER.info("====>场景演示：根据账号ID查询签署账号信息<=====");
                getAccountInfoByAccountId();//根据accountId查询个人/企业用户名字、证件号
                break;
            case 6:
                LOGGER.info("====>场景演示：注销账号<=====");
                deleteAccount();//根据accountId注销个人/企业用户账号，注销后再次使用需要重新创建
                break;
            default:
                LOGGER.info("====>请选择应用场景<=====");
                break;
        }
    }
//--------------------------------公有方法 end---------------------------------------

//--------------------------------私有方法 start-------------------------------------


    /**
     * 创建个人签署账号
     */
    private static String addPsnAccount() throws DefineException {
        PersonParam person = new PersonParam();
        person.setName("测试张三");// 姓名，不可空
        person.setIdNo("210103*****7107");// 证件号码，不可空
        person.setIdNoType(IdNoTypeEnum.MAINLAND);// 个人身份证件类型，不可空
        // 支持的证件类型如下：
        // MAINLAND，大陆身份证，15位或者17位+一位校验位
        // HONGKONG，香港居民往来内地通行证，字母H或者h开头，后接8位或者10位数字
        // MACAO，澳门居民往来内地通行证，字母M或者m开头，后接8位或者10位数字
        // TAIWAN，台湾居民来往大陆通行证，8位或者10位数字
        // PASSPORT，护照，【GgEePpSsDd】中任一开头，后接1位0-9数字或者【.】，再+7位数字，总计9位
        // OTHER，其他，不校验
        person.setIdentityAuthId("383818111111104923");//个人认证流程ID（通过【个人核身认证服务】任意一种方式完成认证后的flowId）
        return accountHelper.addPsnAccount(person);
    }

    /**
     * 创建企业签署账号
     */
    private static String addOrgAccount() throws DefineException {
        OrganizeParam organize = new OrganizeParam();
        organize.setName("杭州天谷公共测试");//机构名称，不可空
        organize.setOrgCode("91*****0022");// 统一社会信用代码号或工商注册号
        organize.setRegType(OrganRegTypeEnum.MERGE);// 企业证件类型，MERGE：统一社会信用代码（多证合一）,REGCODE:企业工商注册码
        return accountHelper.addOrgAccount(organize);
    }

    /**
     * 更新个人签署账号信息
     */
    public static void updatePsnAccount() throws DefineException {
        UpdatePersonParam person = new UpdatePersonParam();
        person.setAccountId("E5A76B********BB76851DC");//账号ID
        person.setName("李四");// 姓名
       //person.setIdentityAuthId("3838184766290004923");//个人认证流程ID（通过【个人核身认证服务】任意一种方式完成认证后的flowId）
        accountHelper.updatePsnAccount(person);

    }

    /**
     * 更新企业签署账号信息
     */
    public static void updateOrgAccount() throws DefineException {
        UpdateOrganizeParam organize = new UpdateOrganizeParam();
        organize.setAccountId("FCA9********31E9C2");//账号ID
        organize.setName("天谷信息科技有限公司");//企业名称
        accountHelper.updateOrgAccount(organize);
    }


    /**
     * 根据证件号获取签署账号信息
     */
    public static void getAccountInfoByIdNo() throws DefineException {
        QueryAccountInfoParam accountInfo = new QueryAccountInfoParam();
        accountInfo.setIdNo("2311XXXXXXX329");
        accountInfo.setIdNoType(AllIdNoTypeEnum.MAINLAND);
        accountInfo.setType(AccountTypeEnum.PERSON);
        accountHelper.getAccountInfoByIdNo(accountInfo);
    }

    /**
     * 根据账号ID查询签署账号信息
     */
    public static void getAccountInfoByAccountId() throws DefineException {
        String accountID = "E5A90F*********76851DC";
        accountHelper.getAccountInfoByAccountId(accountID, false);
    }

    /**
     * 注销签署账号
     */
    public static void deleteAccount() throws DefineException {
        String accountID = "E5A90**********6851DC";
        accountHelper.deleteAccount(accountID);
    }

//--------------------------------私有方法 end---------------------------------------
}

