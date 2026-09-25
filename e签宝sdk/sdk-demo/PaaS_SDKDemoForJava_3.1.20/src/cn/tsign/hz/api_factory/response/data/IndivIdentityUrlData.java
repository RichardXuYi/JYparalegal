package cn.tsign.hz.api_factory.response.data;
/**
 * 核身认证获取个人核身认证地址data

 */
public class IndivIdentityUrlData {
    private String flowId;
    private String shortLink;
    private String url;

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getShortLink() {
        return shortLink;
    }

    public void setShortLink(String shortLink) {
        this.shortLink = shortLink;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
