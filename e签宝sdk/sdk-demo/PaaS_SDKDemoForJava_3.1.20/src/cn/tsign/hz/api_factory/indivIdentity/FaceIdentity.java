package cn.tsign.hz.api_factory.indivIdentity;

import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.api_factory.request.Request;
import cn.tsign.hz.api_factory.response.indivIdentity.FaceIdentityResponse;

/**
 * 核身认证发起个人刷脸核身认证

 */
public class FaceIdentity extends Request<FaceIdentityResponse> {
    private String name;
    private String idNo;
    private String faceauthMode;
    private String callbackUrl;
    private String contextId;
    private String notifyUrl;

    private FaceIdentity(){};
    public FaceIdentity(String name, String idNo, String faceauthMode, String callbackUrl) {
        this.name = name;
        this.idNo = idNo;
        this.faceauthMode = faceauthMode;
        this.callbackUrl = callbackUrl;
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

    public String getFaceauthMode() {
        return faceauthMode;
    }

    public void setFaceauthMode(String faceauthMode) {
        this.faceauthMode = faceauthMode;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
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

    @Override
    public void build() {
        super.setUrl("/v2/identity/auth/api/individual/face");
        super.setRequestType(RequestType.POST);
    }
}
