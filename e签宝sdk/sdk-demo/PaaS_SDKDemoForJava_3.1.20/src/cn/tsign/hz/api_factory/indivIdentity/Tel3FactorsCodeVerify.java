package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.Tel3FactorsCodeVerifyResponse;
import com.alibaba.fastjson.annotation.JSONField;

/**
 * 核身认证运营商短信验证码校验

 */
public class Tel3FactorsCodeVerify extends Request<Tel3FactorsCodeVerifyResponse> {
    @JSONField(serialize = false)
    private String flowId;
    private String authcode;

    private Tel3FactorsCodeVerify(){};
    public Tel3FactorsCodeVerify(String flowId, String authcode) {
        this.flowId = flowId;
        this.authcode = authcode;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getAuthcode() {
        return authcode;
    }

    public void setAuthcode(String authcode) {
        this.authcode = authcode;
    }

    @Override
    public void build() {
        super.setUrl("/v2/identity/auth/pub/individual/"+flowId+"/telecom3Factors");
        super.setRequestType(RequestType.PUT);
    }
}
