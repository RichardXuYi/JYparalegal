package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.sdk_core.CreateEviHelper;
import cn.tsign.hz.sdk_core.FileHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.CreateEviParam;
import com.timevale.esign.paas.tech.client.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/***
 * description: 出证服务
 */


public class ToCreateEvi {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToCreateEvi.class);
    private static CreateEviHelper createEviHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            createEviHelper = new CreateEviHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

//--------------------------------公有方法 start-------------------------------------

    public static void main(String[] args) throws DefineException {

        LOGGER.info("====>1、需要联系e签宝商务经理申请开通该服务，e签宝内部流程审核通过后才可调用；");
        LOGGER.info("====>2、需要联系e签宝商务经理购买对应的出证套餐，才能在正式生产环境使用。（沙箱模拟环境可联系e签宝交付经理申请赠送）<=====");


        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：申请出证（文件路径方式）<=====");
                createEviByFile();//当合同签署后根据本地存放的文件路径方式申请e签宝证据报告（需要购买对应的套餐后才可以使用）
                break;
            case 1:
                LOGGER.info("====>场景演示：申请出证（文件流方式）<=====");
                createEviByStream();//当合同签署后根据存放的文件流方式申请e签宝证据报告（需要购买对应的套餐后才可以使用）
                break;
            case 2:
                LOGGER.info("====>场景演示：查询出证结果<=====");
                queryEviResult();//申请出证后，可以根据出证业务ID获取出证结果，包含出证报告
                //出证是异步的，如果没有使用回调通知服务接收出证结果，建议等待30分钟后查询报告。
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
     * 文件路径方式申请出证
     */
    private static void createEviByFile() throws DefineException {
        //PDF文件信息
        CreateEviParam eviParam = new CreateEviParam();
        eviParam.setSrcPdfFile(PATH_PREFEX + "Signed_Platform.pdf");
        eviParam.setFileName("李四签署的文件.pdf");
        createEviHelper.createEvi(eviParam);
    }

    /**
     * 文件流方式申请出证
     */
    private static void createEviByStream() throws DefineException {
        //PDF文件信息
        CreateEviParam eviParam = new CreateEviParam();
        String srcPdfPath = PATH_PREFEX + "Signed_Platform.pdf";
        //获取PDF文件的字节流
        byte[] srcPdfBytes = FileHelper.getFileBytes(srcPdfPath);
        eviParam.setStreamFile(srcPdfBytes);
        eviParam.setFileName("张三签署的文件.pdf");
        createEviHelper.createEvi(eviParam);
    }

    /**
     * 查询出证结果
     */
    private static void queryEviResult() throws DefineException {
        String evId ="20240*****0612";
        createEviHelper.queryEviResult(evId);
    }

//--------------------------------私有方法 end---------------------------------------
}
