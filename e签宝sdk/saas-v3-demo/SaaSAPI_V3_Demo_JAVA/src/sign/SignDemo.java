package sign;

import cn.tsign.hz.comm.EsignHttpHelper;
import cn.tsign.hz.comm.EsignHttpResponse;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;

import java.util.Map;

public class SignDemo {
    private static String eSignHost= EsignDemoConfig.EsignHost;
    private static String eSignAppId=EsignDemoConfig.EsignAppId;
    private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;
    public static String signFlowId = "";
    public static String fileId = "";


    public static void main(String[] args) throws EsignDemoException {
        Gson gson = new Gson();
        /* 发起签署*/
        EsignHttpResponse createByFile = createByFile();
        //JSONObject getPsnAuthUrlObject = JSONObject.parseObject(getPsnAuthUrl.getBody());
        JsonObject createByFileJsonObject = gson.fromJson(createByFile.getBody(), JsonObject.class);
        JsonObject createByFileData = createByFileJsonObject.getAsJsonObject("data");
        String authUrl = createByFileData.get("signFlowId").getAsString();
        System.err.println("流程id:"+signFlowId);

        //获取合同文件签署链接
        EsignHttpResponse signUrl = signUrl(signFlowId);
        JsonObject signUrlJsonObject = gson.fromJson(signUrl.getBody(), JsonObject.class);
        JsonObject signUrlData = signUrlJsonObject.getAsJsonObject("data");
        String shortUrl = signUrlData.get("shortUrl").getAsString();
        String url = signUrlData.get("url").getAsString();
        System.out.println("签署长链接:" +shortUrl);
        System.out.println("签署短链接:"+url);

        //获取批量签页面链接（多流程）
        EsignHttpResponse batchSignUrl = batchSignUrl();
        JsonObject batchSignUrlJsonObject = gson.fromJson(batchSignUrl.getBody(),JsonObject.class);
        System.out.println(batchSignUrlJsonObject);

        //下载已签署文件及附属材料
        EsignHttpResponse fileDownloadUrl = fileDownloadUrl(signFlowId);
        JsonObject fileDownloadUrlJsonObject = gson.fromJson(fileDownloadUrl.getBody(),JsonObject.class);
        JsonObject fileDownloadUrlArray = fileDownloadUrlJsonObject.getAsJsonObject("data");
        System.out.println(fileDownloadUrlArray);

        //开启签署流程
        EsignHttpResponse signFlowStart = signFlowStart(signFlowId);
        JsonObject signFlowStartJsonObject = gson.fromJson(signFlowStart.getBody(),JsonObject.class);
        System.out.println(signFlowStartJsonObject);

        //完结签署流程
        EsignHttpResponse signFlowFinish = signFlowFinish(signFlowId);
        JsonObject signFlowFinishJsonObject = gson.fromJson(signFlowFinish.getBody(),JsonObject.class);
        System.out.println(signFlowFinishJsonObject);

        //撤销签署流程
        EsignHttpResponse signFlowRevoke = signFlowRevoke(signFlowId);
        JsonObject signFlowRevokeJsonObject = gson.fromJson(signFlowRevoke.getBody(),JsonObject.class);
        System.out.println(signFlowRevokeJsonObject);

        //延期签署截止时间
        EsignHttpResponse signFlowDelay = signFlowDelay(signFlowId);
        JsonObject signFlowDelayJsonObject = gson.fromJson(signFlowDelay.getBody(),JsonObject.class);
        System.out.println(signFlowDelayJsonObject);

        //催签流程中签署人
        EsignHttpResponse signFlowUrge= signFlowUrge(signFlowId);
        JsonObject signFlowUrgeJsonObject = gson.fromJson(signFlowUrge.getBody(),JsonObject.class);
        System.out.println(signFlowUrgeJsonObject);

        //查询签署流程详情
        EsignHttpResponse signFlowDetail = signFlowDetail(signFlowId);
        JsonObject signFlowDetailJsonObject = gson.fromJson(signFlowDetail.getBody(),JsonObject.class);
        System.out.println(signFlowDetailJsonObject);

        //查询签署流程列表
        EsignHttpResponse signFlowList = signFlowList();
        JsonObject signFlowListJsonObject = gson.fromJson(signFlowList.getBody(),JsonObject.class);
        System.out.println(signFlowListJsonObject);

        //追加签署区
        EsignHttpResponse signFieldsAdd = signFieldsAdd(signFlowId);
        JsonObject signFieldsAddJsonObject = gson.fromJson(signFieldsAdd.getBody(),JsonObject.class);
        System.out.println(signFieldsAddJsonObject);

        //删除签署区
        EsignHttpResponse signFieldsDelete = signFieldsDelete(signFlowId);
        JsonObject signFieldsDeleteJsonObject = gson.fromJson(signFieldsDelete.getBody(),JsonObject.class);
        System.out.println(signFieldsDeleteJsonObject);

        //追加待签文件
        EsignHttpResponse unsignedFilesAdd = unsignedFilesAdd(signFlowId);
        JsonObject unsignedFilesAddJsonObject = gson.fromJson(unsignedFilesAdd.getBody(),JsonObject.class);
        System.out.println(unsignedFilesAddJsonObject);

        //删除待签文件
        EsignHttpResponse unsignedFilesDelete = unsignedFilesDelete(signFlowId);
        JsonObject unsignedFilesDeleteJsonObject = gson.fromJson(unsignedFilesDelete.getBody(),JsonObject.class);
        System.out.println(unsignedFilesDeleteJsonObject);

        //追加附属材料
        EsignHttpResponse attachmentsAdd = attachmentsAdd(signFlowId);
        JsonObject attachmentsAddJsonObject = gson.fromJson(attachmentsAdd.getBody(),JsonObject.class);
        System.out.println(attachmentsAddJsonObject);

        //删除附属材料
        EsignHttpResponse attachmentsDelete = attachmentsDelete(signFlowId);
        JsonObject attachmentsDeleteJsonObject = gson.fromJson(attachmentsDelete.getBody(),JsonObject.class);
        System.out.println(attachmentsDeleteJsonObject);

        //添加抄送方
        EsignHttpResponse copiersAdd = copiersAdd(signFlowId);
        JsonObject copiersAddJsonObject = gson.fromJson(copiersAdd.getBody(),JsonObject.class);
        System.out.println(copiersAddJsonObject);

        //添加抄送方
        EsignHttpResponse copiersDelete = copiersDelete(signFlowId);
        JsonObject copiersDeleteJsonObject = gson.fromJson(copiersDelete.getBody(),JsonObject.class);
        System.out.println(copiersDeleteJsonObject);

        //核验合同文件签名有效性
        EsignHttpResponse filesVerify = filesVerify(fileId);
        JsonObject filesVerifyJsonObject = gson.fromJson(filesVerify.getBody(),JsonObject.class);
        System.out.println(filesVerifyJsonObject);

        //获取区块链存证信息
        EsignHttpResponse antchainFileInfo = antchainFileInfo(fileId);
        JsonObject antchainFileInfoJsonObject = gson.fromJson(antchainFileInfo.getBody(),JsonObject.class);
        System.out.println(antchainFileInfoJsonObject);

        //核验区块链存证文件
        EsignHttpResponse antchainFileInfoVerify = antchainFileInfoVerify();
        JsonObject antchainFileInfoVerifyJsonObject = gson.fromJson(antchainFileInfoVerify.getBody(),JsonObject.class);
        System.out.println(antchainFileInfoVerifyJsonObject);

        //获取合同解约链接
        EsignHttpResponse signflowRescissionUrl = signflowRescissionUrl(signFlowId);
        JsonObject signflowRescissionUrlJsonObject = gson.fromJson(signflowRescissionUrl.getBody(),JsonObject.class);
        System.out.println(signflowRescissionUrlJsonObject);

    }

    /**
     * 获取合同解约链接
     * @return
     */
    public static EsignHttpResponse signflowRescissionUrl(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/rescission-url";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"rescissionInitiator\": {\n" +
                "        \"orgInitiator\": {\n" +
                "            \"orgId\": \"0c5bd49248***5648bfbf\",\n" +
                "            \"transactor\": {\n" +
                "                \"psnId\": \"c7e002947***310541e7\"\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"signFlowConfig\": {\n" +
                "        \"chargeConfig\": {\n" +
                "            \"chargeMode\": 0\n" +
                "        },\n" +
                "        \"noticeConfig\": {\n" +
                "            \"noticeTypes\": \"1,2\"\n" +
                "        },\n" +
                "        \"notifyUrl\": \"https://xx.xx.xx/callback\"\n" +
                "    }\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 核验区块链存证文件
     * @return
     */
    public static EsignHttpResponse antchainFileInfoVerify() throws EsignDemoException {
        String apiaddr= "/v3/antchain-file-info/verify";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"fileHash\": \"bcdc3c***0477\",\n" +
                "    \"antTxHash\": \"11111111222222226c***fd1ed2f4\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 获取区块链存证信息
     * @return
     */
    public static EsignHttpResponse antchainFileInfo(String fileId) throws EsignDemoException {
        String apiaddr= "/v3/antchain-file-info";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"signFlowId\": \"\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 核验合同文件签名有效性
     * @return
     */
    public static EsignHttpResponse filesVerify(String fileId) throws EsignDemoException {
        String apiaddr= "/v3/files/"+ fileId + "verify";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"signFlowId\": \"\"\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 删除抄送方
     * @return
     */
    public static EsignHttpResponse copiersDelete(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/copiers/delete";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"copiers\": [\n" +
                "        {\n" +
                "            \"copierPsnInfo\": {\n" +
                "                \"psnId\": \"\",\n" +
                "                \"psnAccount\": \"151****0101\"\n" +
                "            },\n" +
                "            \"copierOrgInfo\": {\n" +
                "                \"orgId\": \"\",\n" +
                "                \"orgName\": \"这是个抄送方的企业名称\"\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 添加抄送方
     * @return
     */
    public static EsignHttpResponse copiersAdd(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/copiers";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"copiers\": [\n" +
                "        {\n" +
                "            \"copierOrgInfo\": {\n" +
                "                \"orgName\": \"这是个抄送通知企业的名称\",\n" +
                "                \"orgId\": \"\"\n" +
                "            },\n" +
                "            \"copierPsnInfo\": {\n" +
                "                \"psnAccount\": \"153****7650\",\n" +
                "                \"psnId\": \"\"\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 删除附属材料
     * @return
     */
    public static EsignHttpResponse attachmentsDelete(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/attachments?fileIds=xxx1,xxx2";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = null;
        //请求方法
        EsignRequestType requestType = EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 追加附属材料
     * @return
     */
    public static EsignHttpResponse attachmentsAdd(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/attachments";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"attachmentList\": [\n" +
                "        {\n" +
                "            \"fileId\": \"ea3151***a53d3f4c\",\n" +
                "            \"fileName\": \"入职手册.pdf\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 删除待签文件
     * @return
     */
    public static EsignHttpResponse unsignedFilesDelete(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/unsigned-files?fileIds=xxx1,xxx2";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = null;
        //请求方法
        EsignRequestType requestType = EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 追加待签文件
     * @return
     */
    public static EsignHttpResponse unsignedFilesAdd(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/unsigned-files";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"unsignedFiles\": [\n" +
                "        {\n" +
                "            \"fileId\": \"ea3151a8d***a53d3f4c\",\n" +
                "            \"fileName\": \"入职证明.pdf\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 删除签署区
     * @return
     */
    public static EsignHttpResponse signFieldsDelete(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/signers/sign-fields?signFieldIds=xxx1,xxx2";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = null;
        //请求方法
        EsignRequestType requestType = EsignRequestType.DELETE;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 追加签署区
     * @return
     */
    public static EsignHttpResponse signFieldsAdd(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/signers/sign-fields";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"signers\": [\n" +
                "        {\n" +
                "            \"noticeConfig\": {\n" +
                "                \"noticeTypes\": \"1\"\n" +
                "            },\n" +
                "            \"psnSignerInfo\": {\n" +
                "                \"psnAccount\": \"153****0000\"\n" +
                "            },\n" +
                "            \"signConfig\": {\n" +
                "                \"forcedReadingTime\": 10,\n" +
                "                \"signOrder\": 2\n" +
                "            },\n" +
                "            \"signFields\": [\n" +
                "                {\n" +
                "                    \"customBizNum\": \"202201010001\",\n" +
                "                    \"fileId\": \"0e99de7c***09db2cd69\",\n" +
                "                    \"remarkSignFieldConfig\": {\n" +
                "                        \"aiCheck\": 0,\n" +
                "                        \"freeMode\": true,\n" +
                "                        \"movableSignField\": true,\n" +
                "                        \"remarkContent\": \"我已阅读并知悉\",\n" +
                "                        \"remarkFontSize\": 20,\n" +
                "                        \"signFieldHeight\": 100,\n" +
                "                        \"signFieldPosition\": {\n" +
                "                            \"acrossPageMode\": \"\",\n" +
                "                            \"positionPage\": \"1\",\n" +
                "                            \"positionX\": 100,\n" +
                "                            \"positionY\": 100\n" +
                "                        },\n" +
                "                        \"signFieldWidth\": 100\n" +
                "                    },\n" +
                "                    \"signFieldType\": 1\n" +
                "                }\n" +
                "            ],\n" +
                "            \"signerType\": 0\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询签署流程列表
     * @return
     */
    public static EsignHttpResponse signFlowList() throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/sign-flow-list";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"operator\": {\n" +
                "        \"psnAccount\": \"18******34\",\n" +
                "        \"psnId\": \"\"\n" +
                "    },\n" +
                "    \"pageNum\": 1,\n" +
                "    \"pageSize\": 20,\n" +
                "    \"signFlowStartTimeFrom\": 1648801671000,\n" +
                "    \"signFlowStartTimeTo\": 1651393671000,\n" +
                "    \"signFlowStatus\": [\n" +
                "        1,\n" +
                "        2\n" +
                "    ]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 查询签署流程详情
     * @return
     */
    public static EsignHttpResponse signFlowDetail(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/detail";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = null;
        //请求方法
        EsignRequestType requestType = EsignRequestType.GET;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 催签流程中签署人
     * @return
     */
    public  static EsignHttpResponse signFlowUrge(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/urge";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm="{\n" +
                "    \"noticeTypes\": \"1\",\n" +
                "    \"urgedOperator\": {\n" +
                "        \"psnAccount\": \"183****0101\"\n" +
                "    }\n" +
                "}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 延期签署截止时间
     * @return
     */
    public static EsignHttpResponse signFlowDelay(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/delay";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm=null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 撤销签署流程
     * @return
     */
    public static EsignHttpResponse signFlowRevoke(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ signFlowId + "/revoke";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm="{}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 完结签署流程
     * @return
     * @param signFlowId
     */
    public static EsignHttpResponse signFlowFinish(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ SignDemo.signFlowId + "/finish";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm="{}";
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 开启签署流程     *
     * @return
     * @param signFlowId
     */
    public static EsignHttpResponse signFlowStart(String signFlowId) throws EsignDemoException {
        String apiaddr= "/v3/sign-flow/"+ SignDemo.signFlowId +"/start";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm= null;
        //请求方法
        EsignRequestType requestType= EsignRequestType.POST;
        //生成签名鉴权方式的的header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId,eSignAppSecret,jsonParm,requestType.name(),apiaddr,true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr,requestType , jsonParm, header,true);
    }

    /**
     * 下载已签署文件及附属材料     *
     * @return
     * @throws EsignDemoException
     */
    public static EsignHttpResponse fileDownloadUrl(String signFlowId) throws EsignDemoException {
        String apiaddr = "/v3/sign-flow/"+ signFlowId +"/file-download-url";
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
     * 获取批量签页面链接（多流程）
     * @return
     */
    public static EsignHttpResponse batchSignUrl() throws EsignDemoException {
        String apiaddr = "/v3/sign-flow/batch-sign-url";
        String jsonParm = "{\n" +
                "    \"operatorId\":\"0f375***********5738d976\",\n" +
                "    \"redirectUrl\":\"https://www.esign.cn\",\n" +
                "    \"signFlowIds\":[\"1fc79b***********1f2d5fd\"]\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成请求签名鉴权方式的Header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId, eSignAppSecret, jsonParm, requestType.name(), apiaddr, true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr, requestType, jsonParm, header, true);
    }

    /**
     * 获取合同文件签署链接
     * @return
     * @throws EsignDemoException
     */
    public static EsignHttpResponse signUrl(String signFlowId) throws EsignDemoException {
        String apiaddr = "/v3/sign-flow/" + signFlowId + "/sign-url";
        String jsonParm = "{\n" +
                "    \"clientType\": \"ALL\",\n" +
                "    \"needLogin\": true,\n" +
                "    \"operator\": {\n" +
                "        \"psnAccount\":\"181xxxxxx\"\n" +
                "    },\n" +
                "    \"organization\": {\n" +
                "        \"orgName\": \"**科技信息有限公司\"\n" +
                "    },\n" +
                "    \"urlType\": 1\n" +
                "}";
        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成请求签名鉴权方式的Header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId, eSignAppSecret, jsonParm, requestType.name(), apiaddr, true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr, requestType, jsonParm, header, true);
    }

    /**
     * 发起签署
     * @return
     * @throws EsignDemoException
     */
    public static EsignHttpResponse createByFile() throws EsignDemoException {
        String apiaddr = "/v3/sign-flow/create-by-file";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"docs\": [\n" +
                "        {\n" +
                "            \"fileId\": \"c3c22a9***********fa9c482\",\n" +
                "            \"fileName\": \"1234.pdf\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"signFlowConfig\": {\n" +
                "        \"signFlowTitle\": \"测试合同\",\n" +
                "        \"chargeConfig\": {\n" +
                "            \"chargeMode\": 0\n" +
                "        }\n" +
                "    },\n" +
                "    \"signers\": [\n" +
                "        {\n" +
                "            \"noticeConfig\": {\n" +
                "                \"noticeTypes\": \"1\"\n" +
                "            },\n" +
                "            \"orgSignerInfo\": {\n" +
                "                \"orgId\": \"\",\n" +
                "                \"orgInfo\": {\n" +
                "                    \"legalRepIDCardNum\": \"\",\n" +
                "                    \"legalRepIDCardType\": \"\",\n" +
                "                    \"legalRepName\": \"孙xx\",\n" +
                "                    \"orgIDCardNum\": \"915******52355\",\n" +
                "                    \"orgIDCardType\": \"CRED_ORG_USCC\"\n" +
                "                },\n" +
                "                \"orgName\": \"**科技信息有限公司\",\n" +
                "                \"transactorInfo\": {\n" +
                "                    \"psnAccount\": \"18********79\",\n" +
                "                    \"psnId\": \"\",\n" +
                "                    \"psnInfo\": {\n" +
                "                        \"bankCardNum\": \"\",\n" +
                "                        \"psnIDCardNum\": \"\",\n" +
                "                        \"psnIDCardType\": \"\",\n" +
                "                        \"psnName\": \"\"\n" +
                "                    }\n" +
                "                }\n" +
                "            },\n" +
                "            \"signConfig\": {\n" +
                "                \"signOrder\": 1\n" +
                "            },\n" +
                "            \"signFields\": [\n" +
                "                {\n" +
                "                    \"customBizNum\": \"62453252523\",\n" +
                "                    \"fileId\": \"c3c22a9***********87cfa9c482\",\n" +
                "                    \"normalSignFieldConfig\": {\n" +
                "                        \"assignedSealId\": \"\",\n" +
                "                        \"autoSign\": false,\n" +
                "                        \"freeMode\": true,\n" +
                "                        \"movableSignField\": true,\n" +
                "                        \"orgSealBizTypes\": \"\",\n" +
                "                        \"psnSealStyles\": \"\",\n" +
                "                        \"signFieldPosition\": {\n" +
                "                            \"positionPage\": \"1\",\n" +
                "                            \"positionX\": 120,\n" +
                "                            \"positionY\": 460\n" +
                "                        },\n" +
                "                        \"signFieldStyle\": 1\n" +
                "                    },\n" +
                "                    \"signFieldType\": 0\n" +
                "                }\n" +
                "            ],\n" +
                "            \"signerType\": 1\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        //请求方法
        EsignRequestType requestType = EsignRequestType.POST;
        //生成请求签名鉴权方式的Header
        Map<String, String> header = EsignHttpHelper.signAndBuildSignAndJsonHeader(eSignAppId, eSignAppSecret, jsonParm, requestType.name(), apiaddr, true);
        //发起接口请求
        return EsignHttpHelper.doCommHttp(eSignHost, apiaddr, requestType, jsonParm, header, true);
    }


}
