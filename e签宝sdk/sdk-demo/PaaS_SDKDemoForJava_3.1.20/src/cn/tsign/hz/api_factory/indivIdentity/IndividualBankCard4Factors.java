package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.IndividualBankCard4FactorsResponse;

/**
 * 核身认证发起银行4要素核身认证

 */
public class IndividualBankCard4Factors extends Request<IndividualBankCard4FactorsResponse> {
    private String name;
    private String certType;
    private String idNo;
    private String mobileNo;
    private String bankCardNo;
    private String contextId;
    private String notifyUrl;
    private int source;

    private IndividualBankCard4Factors(){};
    public IndividualBankCard4Factors(String name, String certType, String idNo, String mobileNo, String bankCardNo, int source) {
        this.name = name;
        this.certType = certType;
        this.idNo = idNo;
        this.mobileNo = mobileNo;
        this.bankCardNo = bankCardNo;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCertType() {
        return certType;
    }

    public void setCertType(String certType) {
        this.certType = certType;
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

    public String getBankCardNo() {
        return bankCardNo;
    }

    public void setBankCardNo(String bankCardNo) {
        this.bankCardNo = bankCardNo;
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
        super.setUrl("/v2/identity/auth/api/individual/bankCard4Factors");
        super.setRequestType(RequestType.POST);
    }
}
