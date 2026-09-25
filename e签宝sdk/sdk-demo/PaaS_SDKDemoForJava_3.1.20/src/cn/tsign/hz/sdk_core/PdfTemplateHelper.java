package cn.tsign.hz.sdk_core;

import java.text.MessageFormat;
import java.util.Map;
import com.timevale.esign.paas.tech.bean.request.SignFilePdfParam;
import com.timevale.esign.paas.tech.bean.result.FileCreateFromTemplateResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.PdfDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import cn.tsign.hz.exception.DefineException;

/**
 * @Desciption SDK填充本地PDF模板文件辅助类
 */

public class PdfTemplateHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfTemplateHelper.class);
    private ServiceClient serviceClient;
    private PdfDocumentService pdfDocumentService;

    public PdfTemplateHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
        this.pdfDocumentService = serviceClient.pdfDocumentService();
    }
    //--------------------------------公有方法 start-------------------------------------

   /**
    * description 填充本地PDF模板文件
    **/
    public void createFileFromTemplate(SignFilePdfParam file,boolean isFlag,Map<String, Object> txtFields)throws DefineException {
        FileCreateFromTemplateResult rst = pdfDocumentService
                .createFileFromTemplate(file, isFlag, txtFields);
        castRst(rst,"本地PDF模板");
    }


    //--------------------------------公有方法 end---------------------------------------

    //--------------------------------私有方法 start-------------------------------------

    /**
     * description 处理返回结果
     * @param rst
     *          {@link FileCreateFromTemplateResult} 返回结果
     * @param typeMsg
     *          {@link String} 生成PDF模板的方式，仅用于日志打印使用
     * @return void
     **/
    private void castRst(FileCreateFromTemplateResult rst,String typeMsg)
            throws DefineException{
        if (rst.getErrCode() != 0) {
            throw new DefineException(MessageFormat.format("本地PDF模板({0})生成失败：errCode={1},errMsg={2}",
                    typeMsg, rst.getErrCode(), rst.getMsg()));
        }else if (rst.getDstPdfFile() != null) {
            LOGGER.info("填充({})成功，填充后PDF文件保存路径：{}", typeMsg, rst.getDstPdfFile());
            //System.out.println("填充模板成功" + JSONObject.fromObject(rst));
        }else if (rst.getStream() != null) {
            LOGGER.info("填充({})成功，填充后PDF文件流：{}", typeMsg, rst.getStream());
            //String outSignedPdfPath = "/Users/zhanghe/Downloads/Filled_PDFTemplate.pdf";
            //FileHelper.saveFileByStream(rst.getStream(), outSignedPdfPath);// 将PDF文件字节流保存为本地PDF文件
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

    public PdfDocumentService getPdfDocumentService() {
        return pdfDocumentService;
    }

    public void setPdfDocumentService(PdfDocumentService pdfDocumentService) {
        this.pdfDocumentService = pdfDocumentService;
    }

    //--------------------------------getter setter end----------------------------------

}


