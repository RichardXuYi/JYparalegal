package cn.tsign.hz.sdk_core;

import cn.tsign.hz.exception.DefineException;
import com.timevale.esign.paas.tech.bean.request.CreateEviParam;
import com.timevale.esign.paas.tech.bean.result.CreateEviResult;
import com.timevale.esign.paas.tech.bean.result.QueryEviResult;
import com.timevale.esign.paas.tech.client.ServiceClient;
import com.timevale.esign.paas.tech.service.EviService;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * description  SDK出证服务辅助类
 */

public class CreateEviHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateEviHelper.class);
    private ServiceClient serviceClient;
    private EviService eviService;

    public CreateEviHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
        this.eviService = serviceClient.eviService();
    }
    //--------------------------------公有方法 start-------------------------------------

    /**
     * description 申请出证
     **/
    public String createEvi(CreateEviParam createEviParam)
            throws DefineException{
        CreateEviResult createEviResult = eviService.createEvi(createEviParam);
        return eviResult(createEviResult);
    }

    /**
     * description 查询出证结果
     **/
    /**
     * description 申请出证
     **/
    public void queryEviResult(String evId)
            throws DefineException{
        QueryEviResult queryEviResult = eviService.queryEviResult(evId);
        queryResult(queryEviResult);
    }



    //--------------------------------公有方法 end---------------------------------------

    //--------------------------------私有方法 start-------------------------------------

    /**
     * description 申请出证返回结果处理
     */
    private String eviResult(CreateEviResult eviResult) throws DefineException {
        if (eviResult.getErrCode() != 0) {
            throw new DefineException(MessageFormat.format("申请出证失败：errCode = {0},msg = {1}",
                    eviResult.getErrCode(), eviResult.getMsg()));
        }
        LOGGER.info("申请出证成功:evId={},请妥善保管出证业务ID", eviResult.getEvId());
        System.out.println("申请出证成功" + JSONObject.fromObject(eviResult));
        return eviResult.getEvId();
    }

    /**
     * description 查询出证结果返回结果处理
     */
    private void queryResult(QueryEviResult queryEviResult) throws DefineException {
        if (queryEviResult.getErrCode() != 0) {
            throw new DefineException(MessageFormat.format("查询出证结果失败：errCode = {0},msg = {1}",
                    queryEviResult.getErrCode(), queryEviResult.getMsg()));
        }
        LOGGER.info("查询出证结果成功:出证状态status={}，出证报告fileUrl={}，出证失败原因reason={}",queryEviResult.getStatus(),queryEviResult.getFileUrl(), queryEviResult.getReason());
        System.out.println("查询出证结果成功" + JSONObject.fromObject(queryEviResult));
    }

    //--------------------------------私有方法 end---------------------------------------
    //--------------------------------getter setter start--------------------------------

    public ServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public EviService getEviService() {
        return eviService;
    }

    public void setEviService(EviService eviService) {
        this.eviService = eviService;
    }

    //--------------------------------getter setter end----------------------------------
}
