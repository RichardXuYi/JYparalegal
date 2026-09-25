package cn.tsign.hz.api_bean;
/**
 * 核身认证获取个人核身认证地址-contextInfo

 */
public class ContextInfo {
    private String contextId;
    private String notifyUrl;
    private String origin;
    private String redirectUrl;
    private boolean showResultPage;

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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public boolean getShowResultPage() {
        return showResultPage;
    }

    public void setShowResultPage(boolean showResultPage) {
        this.showResultPage = showResultPage;
    }
}
