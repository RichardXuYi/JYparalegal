package cn.tsign.hz.api_factory.response.indivIdentity;

import cn.tsign.hz.api_factory.response.Response;
import cn.tsign.hz.api_factory.response.data.QryFaceStatusData;

/**
 * 核身认证查询个人刷脸状态响应

 */
public class QryFaceStatusResponse extends Response {
    private QryFaceStatusData data;

    public QryFaceStatusData getData() {
        return data;
    }

    public void setData(QryFaceStatusData data) {
        this.data = data;
    }
}
