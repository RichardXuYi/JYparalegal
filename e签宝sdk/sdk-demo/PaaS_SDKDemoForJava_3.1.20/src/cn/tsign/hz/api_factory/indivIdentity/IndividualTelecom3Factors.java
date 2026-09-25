package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.IndividualTelecom3FactorsResponse;

/**
 * 核身认证发起运营商3要素核身认证

 */
public class IndividualTelecom3Factors extends Request<IndividualTelecom3FactorsResponse> {
    private String name;
    private String idNo;
    private String mobileNo;
    private String contextId;
    private String notifyUrl;
    private int source;

    public IndividualTelecom3Factors(String name, String idNo, String mobileNo, int source) {
        this.name = name;
        this.idNo = idNo;
        this.mobileNo = mobileNo;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdNo() {
        return idNo;
    }

    public void setIdNo(String idNo) {
        this.idNo = idNo;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    @Override
    public void build() {
        super.setUrl("/v2/identity/auth/api/individual/telecom3Factors");
        super.setRequestType(RequestType.POST);
    }
}
