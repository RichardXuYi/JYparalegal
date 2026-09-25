package seal;

import cn.tsign.hz.comm.*;
import cn.tsign.hz.enums.EsignHeaderConstant;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;


import java.util.Map;

/**
 * description 印章服务
 * @author  古柯
 * @date  2022/8/20 10:42
 * @version
 */
public class SealDemo {

    private static String eSignHost= EsignDemoConfig.EsignHost;
    private static String eSignAppId=EsignDemoConfig.EsignAppId;
    private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;

    //注意：使用印章服务之前需要调用认证授权口授予资源管理权限（manage_org_resource）
    public static void main(String[] args) throws EsignDemoException, InterruptedException {
        Gson gson = new Gson();
     /*   //创建个人模板印章
        EsignHttpResponse createByTemplate = createByTemplate();
        JsonObject createByTemplateObject = gson.fromJson(createByTemplate.getBody(), JsonObject.class);
        System.out.println("请求返回："+createByTemplateObject);


        //查询个人印章列表
        EsignHttpResponse psnSealList = psnSealList();
        JsonObject psnSealListObject = gson.fromJson(psnSealList.getBody(), JsonObject.class);
        System.out.println("请求返回："+psnSealListObject);

        //删除个人印章
        EsignHttpResponse deletePsnSeal = deletePsnSeal();
        JsonObject deletePsnSealObject = gson.fromJson(deletePsnSeal.getBody(), JsonObject.class);
        System.out.println("请求返回："+deletePsnSealObject);
   */


    //企业印章场景
        //创建机构模板印章
        EsignHttpResponse createOrgByTemplate = createOrgByTemplate();
        JsonObject createOrgByTemplateObject = gson.fromJson(createOrgByTemplate.getBody(), JsonObject.class);
        System.out.println("请求返回："+createOrgByTemplateObject);
        //查询企业内部印章
        EsignHttpResponse orgOwnSealList = orgOwnSealList();
        JsonObject orgOwnSealListObject = gson.fromJson(orgOwnSealList.getBody(), JsonObject.class);
        System.out.println("请求返回："+orgOwnSealListObject);
        //查询被外部企业授权印章
        EsignHttpResponse orgAuthorizedSealList = orgAuthorizedSealList();
        JsonObject orgAuthorizedSealListObject = gson.fromJson(orgAuthorizedSealList.getBody(), JsonObject.class);
        System.out.println("请求返回："+orgAuthorizedSealListObject);
        //跨企业印章授权
        EsignHttpResponse orgSealsExternalAuth = orgSealsExternalAuth();
        JsonObject orgSealsExternalAuthObject = gson.fromJson(orgSealsExternalAuth.getBody(), JsonObject.class);
        System.out.println("请求返回："+orgSealsExternalAuthObject);
        //解除印章授权
        EsignHttpResponse orgSealsauthDelete = orgSealsauthDelete();
        JsonObject orgSealsauthDeleteObject = gson.fromJson(orgSealsauthDelete.getBody(), JsonObject.class);
        System.out.println("请求返回："+orgSealsauthDeleteObject);
        //删除企业印章
        EsignHttpResponse deleteOrgSeal = deleteOrgSeal();
        JsonObject deleteOrgSealObject = gson.fromJson(deleteOrgSeal.getBody(), JsonObject.class);
        System.out.println("请求返回："+deleteOrgSealObject);


    }
    /**
     *创建个人模板印章
     * @return
     */
    public static EsignHttpResponse createByTemplate() throws EsignDemoException {
        String psnId="319f513178f242e590c04b2fc2bb8ca5";
        String apiaddr="/v3/seals/psn-seals/create-by-template";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm="{\n" +
                "  \"psnId\": \""+psnId+"\",\n" +
                "  \"sealName\": \"这是一个自定义的名称2\",\n" +
                "  \"sealTemplateStyle\": \"RECTANGLE_NO_BORDER\",\n" +
                "  \"sealSize\":\"20_10\",\n" +
                "  \"sealColor\": \"RED\",\n" +
                "  \"sealSuffix\": \"2\",\n" +
                "  \"sealOldStyle\": \"OLD_1\",\n" +
                "  \"sealOpacity\": \"100\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询个人印章列表
     */

    public static EsignHttpResponse psnSealList() throws EsignDemoException {
        String psnId="319f513178f242e590c04b2fc2bb8ca5";
        String apiaddr="/v3/seals/psn-seal-list?psnId="+psnId+"&pageNum=1&pageSize=20";

        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.GET;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }


    /**
     * 删除个人印章
     */

    public static EsignHttpResponse deletePsnSeal() throws EsignDemoException {
        String psnId="319f513178f242e590c04b2fc2bb8ca5";
        String sealId="a7b4ca07-ee51-446e-a3c5-3a7e85d117c4";
        String apiaddr="/v3/seals/psn-seal?psnId="+psnId+"&sealId="+sealId;
        System.out.println(apiaddr);
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }
    /**
     *创建机构模板印章
     * @return
     */
    public static EsignHttpResponse createOrgByTemplate() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";

        String apiaddr="/v3/seals/org-seals/create-by-template";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm="{\n" +
                "  \"orgId\": \""+orgId+"\",\n" +
                "  \"sealName\": \"企业公章1\",\n" +
                "  \"sealTemplateStyle\": \"PERSONNEL_ROUND_NO_STAR\",\n" +
                "  \"sealOpacity\": \"100\",\n" +
                "  \"sealColor\": \"RED\",\n" +
                "  \"sealOldStyle\": \"OLD_12\",\n" +
                "  \"sealSize\":\"38_38\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询企业内部印章
     */

    public static EsignHttpResponse orgOwnSealList() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";
        int pageNum=1;
        int pageSize=10;

        String apiaddr="/v3/seals/org-own-seal-list?orgId="+orgId+"&pageNum="+pageNum+"&pageSize="+pageSize;

        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.GET;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }
    /**
     * 查询被外部企业授权印章
     */

    public static EsignHttpResponse orgAuthorizedSealList() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";
        int pageNum=1;
        int pageSize=10;
        String apiaddr="/v3/seals/org-authorized-seal-list?orgId="+orgId+"&pageNum="+pageNum+"&pageSize="+pageSize;

        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.GET;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 删除机构印章
     */

    public static EsignHttpResponse deleteOrgSeal() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";
        String sealId="";
        String apiaddr="/v3/seals/org-seal?orgId="+orgId+"&sealId="+sealId;
        System.out.println(apiaddr);
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 跨企业授权
     */

    public static EsignHttpResponse orgSealsExternalAuth() throws EsignDemoException {

        String orgId="89621ebc8fe64239b61001c46caa8de6";//授权企业账号id
        String transactorPsnId="319f513178f242e590c04b2fc2bb8ca5 ";//操作人的账号id
        String orgName="esigntest-guke";//被授权的企业名称
        String orgIDCardNum="913301087458302417";//被授权企业证件号
        String sealId="475f2ebc-dc17-xxxx-8xx5-714ded5cbb0a";

        String apiaddr="/v3/seals/org-seals/external-auth";
        System.out.println(apiaddr);
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null



        String jsonParm="{\n" +
                "    \"orgId\": \""+orgId+"\",\n" +
                "    \"effectiveTime\": 1636525541000,\n" +
                "    \"expireTime\": 1668061541000,\n" +
                "    \"transactorPsnId\":\""+transactorPsnId+"\",\n" +
                "    \"authorizedOrgInfo\":{\n" +
                "        \"orgName\":\""+orgName+"\",\n" +
                "        \"orgIDCardNum\":\""+orgIDCardNum+"\"\n" +
                "    },\n" +
                "    \"sealId\": \""+sealId+"\",\n" +
                "    \"redirectUrl\": \"https://www.xx.cn/\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }


    /**
     * 解除印章授权
     */

    public static EsignHttpResponse orgSealsauthDelete() throws EsignDemoException {

        String orgId="97fb16472026xxxx405a28c58428c7";//授权企业账号id
        String sealId="475f2ebc-dc17-xxxx-89xxx-714ded5cbb0a";
        String apiaddr="/v3/seals/org-seals/auth-delete";
        System.out.println(apiaddr);
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null

        String jsonParm="{   \n" +
                "     \"orgId\": \""+orgId+"\",\n" +
                "     \"deleteType\":\"sealIds\",\n" +
                "     \"sealIds\":[\""+sealId+"\"]\n" +
                "} ";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }





























}
