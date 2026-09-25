package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.BankCard4FactorsCodeVerifyResponse;
import com.alibaba.fastjson.annotation.JSONField;

/**
 * 核身认证银行预留手机号验证码校验


 */
public class BankCard4FactorsCodeVerify extends Request<BankCard4FactorsCodeVerifyResponse> {
    @JSONField(serialize = false)
    private String flowId;
    private String authcode;

    private BankCard4FactorsCodeVerify(){};
    public BankCard4FactorsCodeVerify(String flowId, String authcode) {
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
			super.setUrl("/v2/identity/auth/pub/individual/"+flowId+"/bankCard4Factors");
			super.setRequestType(RequestType.PUT);
    }
}
