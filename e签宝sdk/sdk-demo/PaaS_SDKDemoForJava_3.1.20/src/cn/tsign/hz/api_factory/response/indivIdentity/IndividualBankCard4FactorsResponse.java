package cn.tsign.hz.api_factory.response.indivIdentity;

import cn.tsign.hz.api_factory.response.Response;
import cn.tsign.hz.api_factory.response.data.IndividualBankCard4FactorsData;

/**
 * 核身认证发起银行4要素认证响应

 */
public class IndividualBankCard4FactorsResponse extends Response {
    private IndividualBankCard4FactorsData data;

    public IndividualBankCard4FactorsData getData() {
        return data;
    }

    public void setData(IndividualBankCard4FactorsData data) {
        this.data = data;
    }
}
