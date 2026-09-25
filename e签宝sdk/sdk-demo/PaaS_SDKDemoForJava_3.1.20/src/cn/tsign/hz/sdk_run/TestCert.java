package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.CertificateFileUrlByAccountIdParam;
import com.timevale.esign.paas.tech.bean.request.QueryLatestAvailableCertParam;
import com.timevale.esign.paas.tech.bean.result.CertificateFileUrlByAccountIdResult;
import com.timevale.esign.paas.tech.bean.result.QueryLatestAvailableCertResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.CertService;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/***
 * description: 证书服务
 */


public class TestCert {


    private static final Logger LOGGER = LoggerFactory.getLogger(TestCert.class);


    public static void main(String[] args) throws DefineException {
        //1、注册客户端，全局使用，只需注册一次
        ClientHelper.registClient();
        //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
        ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);
        CertService certService = serviceClient.certService();

        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：根据账号ID查询数字证书信息<=====");
                QueryLatestAvailableCertParam queryLatestAvailableCertParam = new QueryLatestAvailableCertParam();
                queryLatestAvailableCertParam.setAccountId("7B00560D5958411AB8B21B658651301A");
                QueryLatestAvailableCertResult queryLatestAvailableCertResult = certService.queryLatestAvailableCert(queryLatestAvailableCertParam);
                if (queryLatestAvailableCertResult.getErrCode() != 0)
                    System.out.println("查询数字证书信息失败" + JSONObject.fromObject(queryLatestAvailableCertResult));
                else
                    System.out.println("查询数字证书信息成功" + JSONObject.fromObject(queryLatestAvailableCertResult));
                break;
            case 1:
                LOGGER.info("====>场景演示：根据账号ID查询数字证书信息证明文件=====");
                CertificateFileUrlByAccountIdParam certificateFileUrlByAccountIdParam = new CertificateFileUrlByAccountIdParam();
                certificateFileUrlByAccountIdParam.setAccountId("7B00560D5958411AB8B21B658651301A");
                CertificateFileUrlByAccountIdResult certificateFileUrlByAccountIdResult =
                        certService.certificateFileUrl(certificateFileUrlByAccountIdParam);
                if (certificateFileUrlByAccountIdResult.getErrCode() != 0)
                    System.out.println("获取数字证书信息证明文件失败" + JSONObject.fromObject(certificateFileUrlByAccountIdResult));
                else
                    System.out.println("获取数字证书信息证明文件成功" + JSONObject.fromObject(certificateFileUrlByAccountIdResult));

                break;
            default:
                LOGGER.info("====>请选择应用场景<=====");
                break;
        }


    }



}
