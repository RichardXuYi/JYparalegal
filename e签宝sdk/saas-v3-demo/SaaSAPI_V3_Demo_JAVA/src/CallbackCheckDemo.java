import cn.tsign.hz.comm.EsignEncryption;

/**
 *
 * @author  澄泓
 * 回调通知验签方法
 * @date  2022/9/6 16:44
 * @version
 */
public class CallbackCheckDemo {
    public static void main(String[] args) {
        //回调地址中的query,根据key做排序后，拼接value
        String query="playIdentity";
        //请求头中的时间戳
        String timestamp="1662450988511";
        //回调body体
        String body="{\"action\":\"SIGN_FLOW_UPDATE\",\"flowId\":\"cd2e1eb5cdxxxxxx81cf4fc2b4ce\",\"accountId\":\"309681476xxxxxea9da9d65520\",\"authorizedAccountId\":\"3096814763b44e1890xxxx9d65520\",\"order\":1,\"signTime\":\"2022-09-06 15:54:17\",\"signResult\":2,\"resultDescription\":\"签署完成\",\"timestamp\":1662450857009}";
        //密钥
        String key="706fa89**************ba9d";
        //回调的签名值
        String sig="b65439df771329*******8da5a81a66d48a066781";
        boolean result= EsignEncryption.callBackCheck(timestamp,query,body,key,sig);
        System.out.println("验签结果:"+result);
    }
}
