package cn.tsign.hz.api_factory.response.indivIdentity;

import cn.tsign.hz.api_factory.response.Response;
import cn.tsign.hz.api_factory.response.data.IndividualTelecom3FactorsData;

/**
 * 核身认证发起运营商3要素核身认证响应

 */
public class IndividualTelecom3FactorsResponse extends Response {
    private IndividualTelecom3FactorsData data;

    public IndividualTelecom3FactorsData getData() {
        return data;
    }

    public void setData(IndividualTelecom3FactorsData data) {
        this.data = data;
    }
}
