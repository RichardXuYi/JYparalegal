package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.sdk_core.FileHelper;
import cn.tsign.hz.sdk_core.PdfTemplateHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.*;
import com.timevale.esign.paas.tech.client.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/***
 * description: 模板文件服务
 */


public class TestPdfTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPdfTemplate.class);
    private static PdfTemplateHelper pdfTemplateHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            pdfTemplateHelper = new PdfTemplateHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

//--------------------------------公有方法 start-------------------------------------

    public static void main(String[] args) throws DefineException {

        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：填充PDF模板（文件路径方式）<=====");
                fillTemplateByPdfFile();
                break;
            case 1:
                LOGGER.info("====>场景演示：填充PDF模板（文件流方式）<=====");
                fillTemplateByPdfStream();
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
    private static final String PATH_PREFEX = ROOT_FOLDER + File.separator + "PDFTemplate" + File.separator;

    /**
     * 文件路径方式填充PDF模板
     */
    private static void fillTemplateByPdfFile() throws DefineException {
        //PDF文件信息
        SignFilePdfParam file = new SignFilePdfParam();
        file.setSrcPdfFile( PATH_PREFEX + "PDFTemplate.pdf");// 待填充PDF文件本地路径
        file.setDstPdfFile(PATH_PREFEX+"Filled_PDFTemplate.pdf");

        //填充信息
        Map<String, Object> txtFields = new HashMap<>();// 模板中包含待填充文本域时，文本域Key-Value组合
        txtFields.put("NameB", "杭州天谷公共测试企业");
        txtFields.put("NameA", "张三");

        pdfTemplateHelper.createFileFromTemplate(file,true, txtFields);
    }


    /**
     * 文件流方式填充PDF模板
     */
    private static void fillTemplateByPdfStream() throws DefineException {
        //PDF文件信息
        SignFilePdfParam file = new SignFilePdfParam();
        String srcPdfPath = PATH_PREFEX + "PDFTemplate.pdf";
        //获取PDF文件的字节流
		byte[] srcPdfBytes = FileHelper.getFileBytes(srcPdfPath);
        file.setStreamFile(srcPdfBytes);// 待填充PDF文件本地二进制数据
        //file.setDstPdfFile(PATH_PREFEX + "Filled_PDFTemplate.pdf");

        //填充信息
        Map<String, Object> txtFields = new HashMap<>();// 模板中包含待填充文本域时，文本域Key-Value组合
        txtFields.put("NameA", "杭州天谷公共测试企业");
        txtFields.put("NameB", "张三");

        pdfTemplateHelper.createFileFromTemplate(file,true, txtFields);
    }

//--------------------------------私有方法 end---------------------------------------
}

