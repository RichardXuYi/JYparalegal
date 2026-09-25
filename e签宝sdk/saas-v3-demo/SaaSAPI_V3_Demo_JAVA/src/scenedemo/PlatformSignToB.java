package scenedemo;

import cn.tsign.hz.comm.EsignHttpHelper;
import cn.tsign.hz.comm.EsignHttpResponse;
import cn.tsign.hz.enums.EsignRequestType;
import cn.tsign.hz.exception.EsignDemoException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import constant.EsignDemoConfig;
import fileAndTemplate.FileDemo;
import sign.SignDemo;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * description 平台方自动签署企业手动签署场景
 * @author 舞乐
 */
public class PlatformSignToB {
    private static String eSignHost= EsignDemoConfig.EsignHost;
    private static String eSignAppId=EsignDemoConfig.EsignAppId;
    private static String eSignAppSecret=EsignDemoConfig.EsignAppSecret;

    public static void main(String[] args) throws EsignDemoException, InterruptedException {
        Gson gson = new Gson();
        //无前置实名，客户需要到签署流程中实名
        //获取文件id以及文件上传地址
        String filePath="E:/合同模板/test-template1.pdf";
        EsignHttpResponse getUploadUrl = FileDemo.getUploadUrl(filePath);
        JsonObject getUploadUrlJsonObject = gson.fromJson(getUploadUrl.getBody(), JsonObject.class);
        JsonObject data = getUploadUrlJsonObject.getAsJsonObject("data");
        //文件id后续发起签署使用
        String fileId =data.get("fileId").getAsString();
        String fileUploadUrl =data.get("fileUploadUrl").getAsString();
        System.out.println("获取文件id以及文件上传地址成功，文件id:"+fileId);
        System.out.println("上传链接:"+fileUploadUrl);

        //文件上传
        EsignHttpResponse uploadFileResponse = FileDemo.uploadFile(fileUploadUrl,filePath);
        JsonObject uploadFileResponseJsonObject = gson.fromJson(uploadFileResponse.getBody(), JsonObject.class);
        int errCode = uploadFileResponseJsonObject.get("errCode").getAsInt();
        System.out.println("文件上传成功，状态码:"+errCode);


        //文件上传成功后文件会有一个异步处理过程，建议轮询文件状态，正常后发起签署
        //查询文件上传状态
        int i=0;
//        while(i<3) {
//            EsignHttpResponse fileStatus = FileDemo.getFileStatus(fileId);
//            JSONObject fileStatusJsonObject = JSONObject.parseObject(fileStatus.getBody());
//            String status = fileStatusJsonObject.getJSONObject("data").getString("fileStatus");
//            System.out.println(String.format("查询文件状态执行第%s次",i+1));
//            if("2".equalsIgnoreCase(status)||"5".equalsIgnoreCase(status)){//查询状态为2或者5代表文件准备完成
//                System.out.println("文件准备完成");
//                break;
//            }
//            System.out.println("文件未准备完成,等待两秒重新查询");
//            TimeUnit.SECONDS.sleep(2);
//            i++;
//        }
        //发起签署
//        EsignHttpResponse createByFile = createByFile();
//        JSONObject createByFileJsonObject = JSONObject.parseObject(createByFile.getBody());
//        String signFlowId = createByFileJsonObject.getJSONObject("data").getString("signFlowId");
//        System.out.println("signFlowId:" + signFlowId);

        //获取合同文件签署链接
        // SignDemo.signUrl(signFlowId);

        //下载已签署文件及附属材料
        //SignDemo.fileDownloadUrl(signFlowId);

    }

    /**
     * 发起签署
     *
     * @return
     * @throws EsignDemoException
     */
    public static EsignHttpResponse createByFile() throws EsignDemoException {
        String apiaddr = "/v3/sign-flow/create-by-file";
        //请求参数body体,json格式。get或者delete请求时jsonString传空json:"{}"或者null
        String jsonParm = "{\n" +
                "    \"docs\": [\n" +
                "        {\n" +
                "            \"fileId\": \"7ccce6f0e7b54f06b2e1df8f0ca84b87\",\n" +
                "            \"fileName\": \"1234.pdf\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"signFlowConfig\": {\n" +
                "        \"signFlowTitle\": \"横山供用水合同\",\n" +
                "        \"autoFinish\": true,\n" +
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
                "                \"orgId\": \"215453190bxxxxxx181668e1c\"\n" +
                "            },\n" +
                "            \"signConfig\": {\n" +
                "                \"signOrder\": 1\n" +
                "            },\n" +
                "            \"signFields\": [\n" +
                "                {\n" +
                "                    \"customBizNum\": \"62453252523\",\n" +
                "                    \"fileId\": \"7ccce6f0exxxxxxxe1df8f0ca84b87\",\n" +
                "                    \"normalSignFieldConfig\": {\n" +
                "                        \"assignedSealId\": \"\",\n" +
                "                        \"autoSign\": true,\n" +//自动盖章参数：true - 后台自动落章（无感知），false - 签署页手动签章
                "                        \"freeMode\": false,\n" +
                "                        \"movableSignField\": false,\n" +
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
                "        },\n" +
                "        {\n" +
                "            \"noticeConfig\": {\n" +
                "                \"noticeTypes\": \"1\"\n" +
                "            },\n" +
                "            \"orgSignerInfo\": {\n" +
                "                \"orgName\": \"xxx科技公司 \",\n" +
                "                \"orgInfo\":{\n" +
                "                    \"legalRepName\":\"孙x宏\",\n" +
                "                    \"legalRepIDCardNum\":\"34102xxxxxxx19533\",\n" +
                "                    \"legalRepIDCardType\":\"CRED_PSN_CH_IDCARD\"\n" +
                "                },\n" +
                "                \"transactorInfo\":{\n" +
                "                    \"psnAccount\":\"181xxxxx34\"\n" +
                "                }\n" +
                "            },\n" +
                "            \"signConfig\": {\n" +
                "                \"signOrder\": 1\n" +
                "            },\n" +
                "            \"signFields\": [\n" +
                "                {\n" +
                "                    \"customBizNum\": \"62453252523\",\n" +
                "                    \"fileId\": \"7ccce6f0xxxxxxe1df8f0ca84b87\",\n" +
                "                    \"normalSignFieldConfig\": {\n" +
                "                        \"assignedSealId\": \"\",\n" +
                "                        \"autoSign\": false,\n" +
                "                        \"freeMode\": false,\n" +
                "                        \"movableSignField\": false,\n" +
                "                        \"orgSealBizTypes\": \"\",\n" +
                "                        \"psnSealStyles\": \"\",\n" +
                "                        \"signFieldPosition\": {\n" +
                "                            \"positionPage\": \"1\",\n" +
                "                            \"positionX\": 320,\n" +
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
