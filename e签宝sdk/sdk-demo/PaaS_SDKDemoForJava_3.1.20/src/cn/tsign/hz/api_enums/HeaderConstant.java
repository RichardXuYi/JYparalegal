package cn.tsign.hz.api_enums;
/**
 * @description  API请求头部信息常量

 */
public enum HeaderConstant {
    ACCEPT("*/*"),
    DATE(""),
    HEADERS( ""),
    CONTENTTYPE_JSON("application/json; charset=UTF-8"),
    AUTHMODE("Signature");

    private String value;
    private HeaderConstant(String value) {
        this.value=value;
    }

    public String VALUE(){
        return this.value;
    }
}
