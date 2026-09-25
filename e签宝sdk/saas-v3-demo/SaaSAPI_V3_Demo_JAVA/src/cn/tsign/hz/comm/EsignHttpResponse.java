package cn.tsign.hz.comm;
/**
 * 网络请求的response类
 * @author  澄泓
 * @date  2022/2/21 17:28
 * @version
 */
public class EsignHttpResponse {
    private int status;
    private String body;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
