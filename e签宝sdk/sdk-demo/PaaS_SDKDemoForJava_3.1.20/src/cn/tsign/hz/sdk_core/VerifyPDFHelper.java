package cn.tsign.hz.sdk_core;

import com.timevale.esign.paas.tech.bean.bean.SignBean;
import com.timevale.esign.paas.tech.bean.bean.SignatureBean;
import com.timevale.esign.paas.tech.bean.request.FilePdfParam;
import com.timevale.esign.paas.tech.bean.result.VerifyPdfResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.SignVerifyService;
import cn.tsign.hz.exception.DefineException;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;
import java.util.List;

/**
 * description  SDK签署后PDF文件验签服务辅助类
 */
public class VerifyPDFHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyPDFHelper.class);
    private ServiceClient serviceClient;
    private SignVerifyService signVerifyService;

    public VerifyPDFHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
        this.signVerifyService = serviceClient.signVerifyService();
    }
    //--------------------------------公有方法 start-------------------------------------

    /**
     * description 签署后PDF文件验签
     **/
    public void localVerifyPdf(FilePdfParam filePdfParam)
            throws DefineException{
        VerifyPdfResult verifyRst = signVerifyService.localVerifyPdf(filePdfParam);
        castVerifyRst(verifyRst);
    }

    //--------------------------------公有方法 end---------------------------------------

    //--------------------------------私有方法 start-------------------------------------

    /**
     * description 格式化验签结果
     **/
    private void castVerifyRst(VerifyPdfResult verifyRst)
            throws DefineException{

        if (verifyRst.getErrCode() != 0) {
            throw new DefineException(MessageFormat.format("PDF文件验签失败：errCode={0},errMsg={1}",
                    verifyRst.getErrCode(),verifyRst.getMsg()));
        }

        List<SignBean> signBeans = verifyRst.getSignatures();
        //获取PDF文件中的所有签名信息
        for(int i = 0; CollectionUtils.isNotEmpty(signBeans)
                && i < signBeans.size(); i++){
            SignBean signBean = signBeans.get(i);
            String cn = signBean.getCert().getCn();
            String certSN = signBean.getCert().getSn();
            String issuerCn = signBean.getCert().getIssuerCN();
            String startDate = signBean.getCert().getStartDate();
            String endDate = signBean.getCert().getEndDate();

            SignatureBean signatureBean = signBean.getSignature();
            boolean isValid = signatureBean.isValidate();
            String signDate = signatureBean.getSignDate();
            String timeFrom = signatureBean.getTimeFrom();

            LOGGER.info("签署人证书名称:{},签署人证书序列号:{},证书发布者名称:{}," +
                    "签署人证书有效期开始时间:{},签署人证书有效期结束时间:{}，该PDF中签名的验证结果:{}，文档签署时间:{}," +
                    "签名数据来源:{}",cn,certSN,issuerCn,startDate,endDate,isValid,signDate,timeFrom);
            System.out.println("验签完成" + JSONObject.fromObject(verifyRst));
        }
    }
    //--------------------------------私有方法 end---------------------------------------
    //--------------------------------getter setter start--------------------------------

    public ServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public SignVerifyService getSignVerifyService() {
        return signVerifyService;
    }

    public void setSignVerifyService(SignVerifyService signVerifyService) {
        this.signVerifyService = signVerifyService;
    }

    //--------------------------------getter setter end----------------------------------
}
