package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.sdk_core.FileHelper;
import cn.tsign.hz.sdk_core.VerifyPDFHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.FilePdfParam;
import com.timevale.esign.paas.tech.client.ServiceClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

/***
 * description: 验签服务
 */


public class TestVerifyPDF {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestVerifyPDF.class);
    private static VerifyPDFHelper verifyPDFHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            verifyPDFHelper = new VerifyPDFHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

//--------------------------------公有方法 start-------------------------------------

    public static void main(String[] args) throws DefineException {

        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：核验文件签名有效性（文件路径方式）<=====");
                localVerifyPdfByFile();
                break;
            case 1:
                LOGGER.info("====>场景演示：核验文件签名有效性（文件流方式）<=====");
                localVerifyPdfByStream();
                break;
            default:
                LOGGER.info("====>请选择应用场景<=====");
                break;
        }
    }
    //--------------------------------公有方法 end---------------------------------------

    //--------------------------------私有方法 start-------------------------------------

    // 当前程序所在文件目录
    private static final String ROOT_FOLDER = new File("").getAbsolutePath();
    //文件地址前缀拼接（可根据实际场景自定义）
    private static final String PATH_PREFEX = ROOT_FOLDER + File.separator + "pdf" + File.separator;


    /**
     * 核验文件签名有效性（文件路径方式）
     */
    private static void localVerifyPdfByFile() throws DefineException {
        //PDF文件信息
        FilePdfParam filePdfParam = new FilePdfParam();
        filePdfParam.setSrcPdfFile(PATH_PREFEX + "Signed_Platform.pdf");
        verifyPDFHelper.localVerifyPdf(filePdfParam);
    }

    /**
     * 核验文件签名有效性（文件流方式）
     */
    private static void localVerifyPdfByStream() throws DefineException {
        //PDF文件信息
        FilePdfParam filePdfParam = new FilePdfParam();
        String srcPdfPath = PATH_PREFEX + "Signed_Platform.pdf";
        //获取PDF文件的字节流
        byte[] srcPdfBytes = FileHelper.getFileBytes(srcPdfPath);
        filePdfParam.setStreamFile(srcPdfBytes);
        verifyPDFHelper.localVerifyPdf(filePdfParam);
    }

//--------------------------------私有方法 end---------------------------------------
}
