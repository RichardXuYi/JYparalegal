package members;
import cn.tsign.hz.comm.*;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;

import java.util.Map;

/**
 * description 企业机构成员服务
 * @author  古柯
 * @date  2022/8/20 10:42
 * @version
 */
public class MembersDemo {
    private static String eSignHost= EsignDemoConfig.EsignHost;
    private static String eSignAppId=EsignDemoConfig.EsignAppId;
    private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;

    //注意：使用企业成员管理服务之前需要调用认证授权口授予企业资源管理权限（manage_org_resource）
    public static void main(String[] args) throws EsignDemoException, InterruptedException {
        Gson gson = new Gson();
        //添加企业机构成员
        EsignHttpResponse createMembers = createMembers();
        JsonObject createMembersObject = gson.fromJson(createMembers.getBody(), JsonObject.class);
        System.out.println("请求返回："+createMembersObject);

        //查询企业成员列表
        EsignHttpResponse memberList = memberList();
        JsonObject memberListObject = gson.fromJson(memberList.getBody(), JsonObject.class);
        System.out.println("请求返回："+memberListObject);
        //查询企业管理员
        EsignHttpResponse administrators = administrators();
        JsonObject administratorsObject = gson.fromJson(administrators.getBody(), JsonObject.class);
        System.out.println("请求返回："+administratorsObject);

        //删除企业成员
        EsignHttpResponse deleteMembers = deleteMembers();
        JsonObject deleteMembersObject = gson.fromJson(deleteMembers.getBody(), JsonObject.class);
        System.out.println("请求返回："+deleteMembersObject);
    }



    /**
     *添加企业机构成员
     * @return
     */
    public static EsignHttpResponse createMembers() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";//调用认证服务查询企业认证授权状态接口获取orgId
        String psnId="319f513178f242e590c04b2fc2bb8ca5";//调用认证服务查询个人认证授权状态接口获取psnId
        String apiaddr="/v3/organizations/"+orgId+"/members";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null

        String jsonParm="{\n" +
                "  \"members\":[\n" +
                "    {\n" +
                "      \"psnId\":\""+psnId+"\",\n" +
                "      \"memberName\":\"黄阳\",\n" +
                "      \"employeeNum\":\"100\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询企业成员列表
     */

    public static EsignHttpResponse memberList() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";
        int pageNum=1;
        int pageSize=10;

        String apiaddr="/v3/organizations/"+orgId+"/member-list?pageNum="+pageNum+"&pageSize="+pageSize;

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
     * 查询企业管理员
     */

    public static EsignHttpResponse administrators() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";

        String apiaddr="/v3/organizations/"+orgId+"/administrators";

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
     * 移除企业成员
     */

    public static EsignHttpResponse deleteMembers() throws EsignDemoException {
        String orgId="89621ebc8fe64239b61001c46caa8de6";
        String memberPsnIds="319f513178f242e590c04b2fc2bb8ca5";

        String apiaddr="/v3/organizations/"+orgId+"/members";
        System.out.println(apiaddr);
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null

        String jsonParm="{\n" +
                "    \"memberPsnIds\":[\""+memberPsnIds+"\"]\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        System.out.println("2464654646456465");
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }






}
