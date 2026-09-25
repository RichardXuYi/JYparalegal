package cn.tsign.hz.enums;

import org.apache.http.client.methods.*;

/**
 * @description 请求类型
 * @author 澄泓
 * @since JDK1.7
 */
public enum EsignRequestType {

	POST{
		@Override
		public HttpRequestBase getHttpType(String url) {
			return new HttpPost(url);
		}
	},
	GET{
		@Override
		public HttpRequestBase getHttpType(String url) {
			return new HttpGet(url);
		}
	},
	DELETE{
		@Override
		public HttpRequestBase getHttpType(String url) {
			return new HttpDelete(url);
		}
	},
	PUT{
		@Override
		public HttpRequestBase getHttpType(String url) {
			return new HttpPut(url);
		}
	},
	;

   public abstract HttpRequestBase getHttpType(String url);
}
