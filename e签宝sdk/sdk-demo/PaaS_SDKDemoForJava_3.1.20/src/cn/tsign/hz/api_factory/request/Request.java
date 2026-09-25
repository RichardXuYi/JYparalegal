package cn.tsign.hz.api_factory.request;

import cn.tsign.hz.api_comm.HttpHelper;
import cn.tsign.hz.api_enums.RequestType;
import cn.tsign.hz.exception.DefineException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

/**
 * @description  常规请求对象父类

 */
public abstract class Request<T> {
    @JSONField(serialize = false)
    private String url;

    @JSONField(serialize = false)
    private RequestType requestType;

    public String getUrl() {
        return url;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    /**
     * 常规请求
     * @return
     * @throws Exception
     */
    public T execute() throws DefineException {
        try {
            Method build = this.getClass().getMethod("build");
            build.invoke(this);
        }catch (NoSuchMethodException e){
            DefineException ex = new DefineException("build方法不存在", e);
            ex.initCause(e);
            throw ex;
        } catch (IllegalAccessException e) {
            DefineException ex = new DefineException("build方法不存在", e);
            ex.initCause(e);
            throw ex;
        } catch (InvocationTargetException e) {
            DefineException ex = new DefineException("build方法不存在", e);
            ex.initCause(e);
            throw ex;
        }
        //生成json格式的请求数据body体
            String params = JSONObject.toJSONString(this);
        //发起http请求返回http状态码status和响应体resCtx
        /**
         * 发起hhtp请求部分代码参考该方法
         */
        Map map = HttpHelper.doCommHttp(requestType, url, params);
        //获取泛型实列
        ParameterizedType ptype = (ParameterizedType) this.getClass().getGenericSuperclass();
        Class clazz = (Class<T>) ptype.getActualTypeArguments()[0];
        //获取带参构造方法
//        Constructor resp = clazz.getConstructor(int.class,String.class);	//获取有参构造
        String resCtx = (String)map.get("resCtx");//响应体
        int status = (int) map.get("status");//响应状态码
        T response = null;
        try {
            response=(T) clazz.newInstance();
            response = (T) JSON.parseObject(resCtx, clazz);
            Method setStatus = response.getClass().getMethod("setStatus", int.class);
            setStatus.invoke(response,status);
            Method setBody = response.getClass().getMethod("setBody", String.class);
            setBody.invoke(response,resCtx);
        }catch (JSONException e){
            try {
                Method setBody = response.getClass().getMethod("setBody", String.class);
                setBody.invoke(response, resCtx);
            }catch (NoSuchMethodException |IllegalAccessException |InvocationTargetException exx){
                DefineException ex = new DefineException("响应对象不能实例化", exx);
                ex.initCause(exx);
                throw ex;
            }
        }catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException
                e){
            DefineException ex = new DefineException("响应对象不能实例化", e);
            ex.initCause(e);
            throw ex;
        }
        return response;
    }

    public abstract void build();
}
