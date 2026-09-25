package cn.tsign.hz.api_factory.response.data;
/**
 * 核身认证查询个人刷脸状态data

 */
public class QryFaceStatusData {
    private String status;
    private String message;
    private String similarity;
    private String livingScore;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSimilarity() {
        return similarity;
    }

    public void setSimilarity(String similarity) {
        this.similarity = similarity;
    }

    public String getLivingScore() {
        return livingScore;
    }

    public void setLivingScore(String livingScore) {
        this.livingScore = livingScore;
    }
}
