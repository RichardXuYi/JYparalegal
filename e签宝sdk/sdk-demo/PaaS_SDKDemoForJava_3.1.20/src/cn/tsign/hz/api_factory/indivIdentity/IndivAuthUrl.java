package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_bean.AvailableAuthTypes;
import cn.tsign.hz.api_bean.ContextInfo;
import cn.tsign.hz.api_bean.IndivInfo;
import cn.tsign.hz.api_bean.PsnConfigParams;
import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.IndivAuthUrlResponse;

/**
 * 核身认证获取个人核身认证地址

 */
public class IndivAuthUrl extends Request<IndivAuthUrlResponse> {
    private String authType;
    private AvailableAuthTypes availableAuthTypes;
    private String receiveUrlMobileNo;
    private ContextInfo contextInfo;
    private IndivInfo indivInfo;
    private PsnConfigParams configParams;

    public IndivAuthUrl(){};
    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public AvailableAuthTypes getAvailableAuthTypes() {
        return availableAuthTypes;
    }

    public void setAvailableAuthTypes(AvailableAuthTypes availableAuthTypes) {
        this.availableAuthTypes = availableAuthTypes;
    }

    public String getReceiveUrlMobileNo() {
        return receiveUrlMobileNo;
    }

    public void setReceiveUrlMobileNo(String receiveUrlMobileNo) {
        this.receiveUrlMobileNo = receiveUrlMobileNo;
    }

    public ContextInfo getContextInfo() {
        return contextInfo;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public IndivInfo getIndivInfo() {
        return indivInfo;
    }

    public void setIndivInfo(IndivInfo indivInfo) {
        this.indivInfo = indivInfo;
    }

    public PsnConfigParams getConfigParams() {
        return configParams;
    }

    public void setConfigParams(PsnConfigParams configParams) {
        this.configParams = configParams;
    }

    @Override
    public void build() {
        super.setUrl("/v2/identity/auth/web/indivAuthUrl");
        super.setRequestType(RequestType.POST);
    }
}
