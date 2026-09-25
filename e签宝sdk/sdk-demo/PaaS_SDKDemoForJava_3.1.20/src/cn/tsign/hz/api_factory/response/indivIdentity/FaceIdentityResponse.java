package cn.tsign.hz.api_factory.response.indivIdentity;

import cn.tsign.hz.api_factory.response.Response;
import cn.tsign.hz.api_factory.response.data.FaceIdentityData;

/**
 * 核身认证发起个人刷脸核身认证响应

 */
public class FaceIdentityResponse extends Response {
    private FaceIdentityData data;

    public FaceIdentityData getData() {
        return data;
    }

    public void setData(FaceIdentityData data) {
        this.data = data;
    }
}
