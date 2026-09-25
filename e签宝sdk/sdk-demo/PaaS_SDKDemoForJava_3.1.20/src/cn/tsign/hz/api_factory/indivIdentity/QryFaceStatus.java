package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.QryFaceStatusResponse;
import com.alibaba.fastjson.annotation.JSONField;

/**
 * 核身认证查询个人刷脸状态

 */
public class QryFaceStatus extends Request<QryFaceStatusResponse> {
    @JSONField(serialize = false)
    private String flowId;

    private QryFaceStatus(){};
    public QryFaceStatus(String flowId) {
        this.flowId = flowId;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    @Override
    public void build() {
        super.setUrl("/v2/identity/auth/pub/individual/"+flowId+"/face");
        super.setRequestType(RequestType.GET);
    }
}
