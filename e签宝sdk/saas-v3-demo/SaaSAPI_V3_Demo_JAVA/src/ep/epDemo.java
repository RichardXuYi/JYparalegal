package ep;

import cn.tsign.hz.comm.EsignCoreSdkInfo;
import cn.tsign.hz.comm.EsignHttpHelper;
import cn.tsign.hz.comm.EsignHttpResponse;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;


import java.util.Map;

/**
 *
 * @author  古柯
 * @date  2022/8/20 10:42
 * @version
 */
public class epDemo {
    private static String eSignHost= EsignDemoConfig.EsignHost;
    private static String eSignAppId=EsignDemoConfig.EsignAppId;
    private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;


    //注意：使用之前需要调用认证授权口授予企业资源管理权限（manage_org_resource）
    public static void main(String[] args) throws EsignDemoException, InterruptedException {
        System.out.println(EsignCoreSdkInfo.getInfo());
        Gson gson = new Gson();
        //获取购买e签宝套餐链接
        EsignHttpResponse orgPlaceOrderUrl = orgPlaceOrderUrl();
        JsonObject orgPlaceOrderUrlObject = gson.fromJson(orgPlaceOrderUrl.getBody(), JsonObject.class);
        System.out.println("请求返回："+orgPlaceOrderUrlObject);

        //查询e签宝套餐余量
        EsignHttpResponse remainingQuantity = remainingQuantity();
        JsonObject remainingQuantityObject = gson.fromJson(remainingQuantity.getBody(), JsonObject.class);
        System.out.println("请求返回："+remainingQuantityObject);
        //查询套餐订单列表
        EsignHttpResponse orderList = orderList();
        JsonObject orderListObject = gson.fromJson(orderList.getBody(), JsonObject.class);
        System.out.println("请求返回："+orderListObject);
    }

    /**
     *获取购买e签宝套餐链接
     * @return
     */
    public static EsignHttpResponse orgPlaceOrderUrl() throws EsignDemoException {
        String orgId="97fb1647202xxxxxx8c58428c7";
        String transactorPsnId="217c64929xxxxxxx3eadd00cdd";
        String apiaddr="/v3/orders/org-place-order-url";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null

        String jsonParm="{\n" +
                "    \"orgId\": \""+orgId+"\",\n" +
                "    \"transactorPsnId\": \""+transactorPsnId+"\",    \n" +
                "    \"redirectUrl\": \"https://www.xxx.com\",\n" +
                "    \"notifyUrl\": \"http://xx.xx.xx.172:8081/CSTNotify/asyn/notify\",\n" +
                "    \"customBizNum\": \"123\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询e签宝套餐余量
     */

    public static EsignHttpResponse remainingQuantity() throws EsignDemoException {
        String orgId="97fb1647xxxxxx28c58428c7";

        String apiaddr="/v3/orders/remaining-quantity?orgId="+orgId;

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
     * 查询套餐订单列表
     */

    public static EsignHttpResponse orderList() throws EsignDemoException {
        String orgId="97fb1647xxxxxxb405a28c58428c7";

        String apiaddr="/v3/orders/order-list?orgId="+orgId;

        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.GET;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

}
