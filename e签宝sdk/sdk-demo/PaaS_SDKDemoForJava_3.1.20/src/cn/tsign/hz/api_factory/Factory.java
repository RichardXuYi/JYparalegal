package cn.tsign.hz.api_factory;

/**
 * @description  基础信息初始化类
 */
public class Factory {
    // 请求域名
    private static String host;
    // 项目Id(应用Id）
    private static String project_id;
    // 项目密钥(应用密钥）
    private static String project_scert;

    private static boolean debug=false;

    private Factory(){};
    public static void init(String host,String project_id,String project_scert) {
        Factory.setHost(host);
        Factory.setProject_id(project_id);
        Factory.setProject_scert(project_scert);
    }

    public static String getHost() {
        return host;
    }

    public static void setHost(String host) {
        Factory.host = host;
    }

    public static String getProject_id() {
        return project_id;
    }

    public static void setProject_id(String project_id) {
        Factory.project_id = project_id;
    }

    public static String getProject_scert() {
        return project_scert;
    }

    public static void setProject_scert(String project_scert) {
        Factory.project_scert = project_scert;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean debug) {
        Factory.debug = debug;
    }
}
