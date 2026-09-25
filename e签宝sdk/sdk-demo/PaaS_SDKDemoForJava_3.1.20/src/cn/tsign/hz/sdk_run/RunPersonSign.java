package cn.tsign.hz.sdk_run;

import cn.tsign.hz.sdk_constant.ConfigConstant;
import cn.tsign.hz.sdk_core.ClientHelper;
import cn.tsign.hz.sdk_core.FileHelper;
import cn.tsign.hz.sdk_core.SignHelper;
import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.bean.PosBean;
import com.timevale.esign.paas.tech.bean.request.BatchBaseSignParam;
import com.timevale.esign.paas.tech.bean.request.BatchPersonSignParam;
import com.timevale.esign.paas.tech.bean.request.PersonSignParam;
import com.timevale.esign.paas.tech.bean.request.SignFilePdfParam;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.enums.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/***
 * description: 个人用户签
 */

public class RunPersonSign {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunPersonSign.class);
    private static SignHelper signHelper;

    static {
        try {
            if (true) {
                //1、注册客户端，全局使用，只需注册一次
                ClientHelper.registClient();
            }

            //2、获取已初始化的客户端，以便后续正常调用SDK提供的各种服务，全局使用，只需获取一次
            ServiceClient serviceClient = ClientHelper.getServiceClient(ConfigConstant.PROJECT_ID);

            //3、实例化辅助类
            signHelper = new SignHelper(serviceClient);
        } catch (DefineException e) {
            e.getE().printStackTrace();
        }
    }

//--------------------------------公有方法 start-------------------------------------

    public static void main(String[] args) throws DefineException {

        switch (0) {
            case 0:
                LOGGER.info("====>场景演示：个人用户PDF文件签署（文件路径方式）<=====");
                orgSignByPdfFile();
                break;
            case 1:
                LOGGER.info("====>场景演示：个人用户PDF文件签署（文件流方式）<=====");
                orgSignByPdfStream();
                break;
            case 2:
                LOGGER.info("====>场景演示：个人用户PDF文件批量签署（文件路径方式）<=====");
                perSignByBatchPdfFile();
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
     * 文件路径方式个人用户PDF文件签署
     */
    private static void orgSignByPdfFile() throws DefineException {
        /**
         * 个人用户PDF文件路径签署
         */
        // 签署文件信息
        SignFilePdfParam file = new SignFilePdfParam();
        file.setSrcPdfFile(PATH_PREFEX + "test.pdf");// 待签署PDF文件本地路径，含文件名（与bytes至少有一个不为空）
        file.setDstPdfFile(PATH_PREFEX + "Signed_Person.pdf");// 签署后PDF文件本地路径，含文件名（为空时返回签署后的文件流）
        file.setFileName("我的个人签署文件.pdf");

        // 签章位置信息
        List<PosBean> posBeans = new ArrayList<>();
        PosBean signPos = new PosBean();
        signPos.setPosPage("1");// 签署页码（除关键字签章外，页码均不可为空）若为多页签章，支持指定多个页码，例如格式：“1-3,5,8“
        signPos.setPosX(200);// 签署位置X坐标，若为关键字定位，相对于关键字的X坐标偏移量，默认0
        signPos.setPosY(0);// 签署位置Y坐标，若为关键字定位，相对于关键字的Y坐标偏移量，默认0
        signPos.setKeyWord("乙方签名");// 关键字，仅限关键字签章时有效，若为关键字定位时，不可空
        signPos.setWidth(96);// 印章展现宽度，将以此宽度对印章图片做同比缩放。
        signPos.setAddSignTime(true);// 是否显示本地签署时间，需要width设置92以上才可以看到时间
        posBeans.add(signPos);

        //个人用户印章Base64数据，需要和个人账号对应的个人名字一致
        String sealData = "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";

        //传入个人用户签署参数内
        PersonSignParam personSignParam = new PersonSignParam();
        personSignParam.setAccountId("1FE511*******02FBDA22D");//个人账号ID（创建个人签署账号接口返回）
        personSignParam.setWillingnessId("322*****368280");//个人认证流程ID（通过 身份核验认证服务-个人认证模块 选择任意一种方式认证完成）
        personSignParam.setSealData(sealData);//印章Base64数据
        personSignParam.setPosBeans(posBeans);//签章位置信息
        personSignParam.setSignType(SignType.Key);//签章类型：Single，单页签章； Multi，多页签章； Edges，签骑缝章； Key，关键字签章
        personSignParam.setFileBean(file);//签署文件信息
        signHelper.personSign(personSignParam);

    }


    /**
     * 文件流方式个人用户PDF文件签署
     */
    private static void orgSignByPdfStream() throws DefineException {

        /**
         * 个人用户PDF文件流签署
         */
        // 签署文件信息
        SignFilePdfParam file = new SignFilePdfParam();
        //PDF文件信息
        String srcPdfPath = PATH_PREFEX + "test.pdf";
        //获取PDF文件的字节流
        byte[] srcPdfBytes = FileHelper.getFileBytes(srcPdfPath);
        file.setStreamFile(srcPdfBytes);// 待填充PDF文件本地二进制数据
        //file.setDstPdfFile(PATH_PREFEX + "Signed_Platform.pdf");// 签署后PDF文件本地路径，含文件名（为空时返回签署后的文件流）

        // 签章位置信息
        List<PosBean> posBeans = new ArrayList<>();
        PosBean signPos = new PosBean();
        signPos.setPosPage("1");// 签署页码（除关键字签章外，页码均不可为空）若为多页签章，支持指定多个页码，例如格式：“1-3,5,8“
        signPos.setPosX(150);// 签署位置X坐标，若为关键字定位，相对于关键字的X坐标偏移量，默认0
        signPos.setPosY(0);// 签署位置Y坐标，若为关键字定位，相对于关键字的Y坐标偏移量，默认0
        signPos.setKeyWord("乙方签名");// 关键字，仅限关键字签章时有效，若为关键字定位时，不可空
        signPos.setWidth(96);// 印章展现宽度，将以此宽度对印章图片做同比缩放。
        signPos.setAddSignTime(true);// 是否显示本地签署时间，需要width设置92以上才可以看到时间
        posBeans.add(signPos);

        //印章数据二选一传入
        //个人用户印章Base64数据，需要和个人账号对应的个人名字一致
        String sealData = "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";

        //传入个人用户签署参数内
        PersonSignParam personSignParam = new PersonSignParam();
        personSignParam.setAccountId("CCCDF8******E2D5A");//个人账号ID（创建个人签署账号接口返回）
        personSignParam.setWillingnessId("123****6");//个人认证流程ID（通过 身份核验认证服务-个人认证模块 选择任意一种方式认证完成）
        personSignParam.setSealData(sealData);//印章Base64数据
        personSignParam.setPosBeans(posBeans);//签章位置信息
        personSignParam.setSignType(SignType.Key);//签章类型：Single，单页签章； Multi，多页签章； Edges，签骑缝章； Key，关键字签章
        personSignParam.setFileBean(file);//签署文件信息
        signHelper.personSign(personSignParam);
    }


    /**
     * 文件路径方式个人用户PDF文件批量签署
     */
    private static void perSignByBatchPdfFile() throws DefineException {
        /**
         * 个人用户PDF文件路径批量签署
         */
        // 签署文件1信息
        SignFilePdfParam fileA = new SignFilePdfParam();
        fileA.setSrcPdfFile(PATH_PREFEX + "test.pdf");// 待签署PDF文件本地路径，含文件名（与bytes至少有一个不为空）
        fileA.setDstPdfFile(PATH_PREFEX + "Signed_PersonA.pdf");// 签署后PDF文件本地路径，含文件名（为空时返回签署后的文件流）
        fileA.setFileName("我的个人签署文件A.pdf");
        fileA.setFileNo("文件标识AAAAAAAA");

        // 签署文件2信息
        SignFilePdfParam fileB = new SignFilePdfParam();
        fileB.setSrcPdfFile(PATH_PREFEX + "授权.pdf");// 待签署PDF文件本地路径，含文件名（与bytes至少有一个不为空）
        fileB.setDstPdfFile(PATH_PREFEX + "Signed_PersonB.pdf");// 签署后PDF文件本地路径，含文件名（为空时返回签署后的文件流）
        fileB.setFileName("我的个人签署文件B.pdf");
        fileB.setFileNo("文件标识BBBBBBBBB");


        // 签章位置信息
        List<PosBean> posBeans = new ArrayList<>();
        PosBean signPos = new PosBean();
        signPos.setPosPage("1");// 签署页码（除关键字签章外，页码均不可为空）若为多页签章，支持指定多个页码，例如格式：“1-3,5,8“
        signPos.setPosX(100);// 签署位置X坐标，若为关键字定位，相对于关键字的X坐标偏移量，默认0
        signPos.setPosY(10);// 签署位置Y坐标，若为关键字定位，相对于关键字的Y坐标偏移量，默认0
        signPos.setKeyWord("乙方签字");// 关键字，仅限关键字签章时有效，若为关键字定位时，不可空
        //signPos.setWidth(96);// 印章展现宽度，将以此宽度对印章图片做同比缩放。
        signPos.setAddSignTime(true);// 是否显示本地签署时间，需要width设置92以上才可以看到时间
        posBeans.add(signPos);

        //个人用户印章Base64数据，需要和个人账号对应的个人名字一致
        String sealData = "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";

        //传入个人用户签署参数内
        BatchBaseSignParam batchBaseSignParamA = new  BatchBaseSignParam();
        batchBaseSignParamA.setSealData(sealData);//印章Base64数据
        batchBaseSignParamA.setPosBeans(posBeans);//签章位置信息
        batchBaseSignParamA.setSignType(SignType.Key);//签章类型：Single，单页签章； Multi，多页签章； Edges，签骑缝章； Key，关键字签章
        batchBaseSignParamA.setFileBean(fileA);//签署文件A信息
        //batchBaseSignParamA.setSealSpec(SealSpecEnum.IMAGE);

        BatchBaseSignParam batchBaseSignParamB = new  BatchBaseSignParam();
        batchBaseSignParamB.setSealData(sealData);//印章Base64数据
        batchBaseSignParamB.setPosBeans(posBeans);//签章位置信息
        batchBaseSignParamB.setSignType(SignType.Key);//签章类型：Single，单页签章； Multi，多页签章； Edges，签骑缝章； Key，关键字签章
        batchBaseSignParamB.setFileBean(fileB);//签署文件B信息
        //batchBaseSignParamB.setSealSpec(SealSpecEnum.IMAGE);

        //构造签署文件数组
        List<BatchBaseSignParam> signInfoParams = new ArrayList<>();
        signInfoParams.add(batchBaseSignParamA);
        signInfoParams.add(batchBaseSignParamB);

        //传入数据封装类
        BatchPersonSignParam batchPersonSignParam = new BatchPersonSignParam();
        batchPersonSignParam.setAccountId("37655E43BDB*****20C4559B689E");//个人账号ID（创建个人签署账号接口返回）
        batchPersonSignParam.setWillingnessId("3963991349103428232");//个人认证流程ID（通过 身份核验认证服务-个人认证模块 选择任意一种方式认证完成）
        batchPersonSignParam.setSignInfoParams(signInfoParams);
        //System.out.println("参数打印" + JSONObject.fromObject(batchPersonSignParam));
        signHelper.personBatchSign(batchPersonSignParam);

    }

//--------------------------------私有方法 end---------------------------------------
}

