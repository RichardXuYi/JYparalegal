package cn.tsign.hz.api_factory.response.indivIdentity;

import cn.tsign.hz.api_factory.response.Response;
import cn.tsign.hz.api_factory.response.data.IndivIdentityUrlData;

/**
 * 核身认证
 */
public class IndivAuthUrlResponse extends Response {
    private IndivIdentityUrlData data;

    public IndivIdentityUrlData getData() {
        return data;
    }

    public void setData(IndivIdentityUrlData data) {
        this.data = data;
    }
}
