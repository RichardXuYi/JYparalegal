package cn.tsign.hz.api_enums;

import org.apache.http.client.methods.*;

/**
 * @description API请求类型

 */
public enum RequestType {

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
