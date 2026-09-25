/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package cn.tsign.hz.comm;

import cn.tsign.hz.exception.EsignDemoException;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 澄泓
 * @version JDK1.7
 * @description 文件转换类
 * @date 2020/10/26 10:47
 */
public class FileTransformation {

    /**
     * 传入本地文件路径转二进制byte
     *
     * @param srcFilePath 本地文件路径
     * @return
     * @throws EsignDemoException
     */
    public static byte[] fileToBytes(String srcFilePath) throws EsignDemoException {
        return getBytes(srcFilePath);
    }

    /**
     * 图片转base64
     *
     * @param filePath 本地文件路径
     * @return
     * @throws EsignDemoException
     */
    public static String fileToBase64(String filePath) throws EsignDemoException {
        byte[] bytes;
        String base64 = null;
        bytes = fileToBytes(filePath);
        base64 = Base64.encodeBase64String(bytes);
        base64 = base64.replaceAll("\r\n", "");
        return base64;
    }

    public static void main(String[] args) throws EsignDemoException {
        System.out.println(getFileContentMD5("D:\\文档\\PLT2022-02124CT.pdf"));
    }

    /***
     * 计算文件内容的Content-MD5
     * @param filePath 文件路径
     * @return
     */
    public static String getFileContentMD5(String filePath) throws EsignDemoException {
        // 获取文件MD5的二进制数组（128位）
        byte[] bytes = getFileMD5Bytes128(filePath);
        // 对文件MD5的二进制数组进行base64编码
        return new String(Base64.encodeBase64String(bytes));
    }

    /**
     * 下载文件
     *
     * @param httpUrl 网络文件地址url
     * @return
     */
    public static boolean downLoadFileByUrl(String httpUrl, String dir) throws EsignDemoException {
        InputStream fis = null;
        FileOutputStream fileOutputStream = null;
        try {
            URL url = new URL(httpUrl);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.connect();
            fis = httpConn.getInputStream();
            fileOutputStream = new FileOutputStream(new File(dir));
            byte[] md5Bytes = null;

            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                fileOutputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            EsignDemoException ex = new EsignDemoException("获取文件流异常", e);
            ex.initCause(e);
            throw ex;
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                EsignDemoException ex = new EsignDemoException("关闭文件流异常", e);
                ex.initCause(e);
                throw ex;
            }
        }
        return true;
    }


    /**
     * 网络文件转二进制MD5数组并获取文件大小
     *
     * @param fileUrl 网络文件地址url
     * @return
     */
    public static Map fileUrlToBytes(String fileUrl) throws EsignDemoException {
        HashMap<String, Object> map = new HashMap<String, Object>();
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.connect();
            InputStream fis = httpConn.getInputStream();
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            outStream.close();
            map.put("fileSize", fis.available());
            byte[] md5Bytes = null;
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                md5.update(buffer, 0, length);
                outStream.write(buffer, 0, length);
            }
            md5Bytes = md5.digest();
            byte[] fileData = outStream.toByteArray();
            map.put("fileData", fileData);
            outStream.close();
            fis.close();
            map.put("md5Bytes", md5Bytes);
        } catch (IOException e) {
            EsignDemoException ex = new EsignDemoException("获取文件流异常", e);
            ex.initCause(e);
            throw ex;
        } catch (NoSuchAlgorithmException e) {
            EsignDemoException ex = new EsignDemoException("文件计算异常", e);
            ex.initCause(e);
            throw ex;
        }
        return map;
    }

    /***
     * 获取文件MD5的二进制数组（128位）
     * @param filePath
     * @return
     * @throws EsignDemoException
     */
    public static byte[] getFileMD5Bytes128(String filePath) throws EsignDemoException {
        FileInputStream fis = null;
        byte[] md5Bytes = null;
        try {
            File file = new File(filePath);
            fis = new FileInputStream(file);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                md5.update(buffer, 0, length);
            }
            md5Bytes = md5.digest();
            fis.close();
        } catch (FileNotFoundException e) {
            EsignDemoException ex = new EsignDemoException("文件找不到", e);
            ex.initCause(e);
            throw ex;
        } catch (NoSuchAlgorithmException e) {
            EsignDemoException ex = new EsignDemoException("不支持此算法", e);
            ex.initCause(e);
            throw ex;
        } catch (IOException e) {
            EsignDemoException ex = new EsignDemoException("输入流或输出流异常", e);
            ex.initCause(e);
            throw ex;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    EsignDemoException ex = new EsignDemoException("关闭文件输入流失败", e);
                    ex.initCause(e);
                    throw ex;
                }
            }
        }
        return md5Bytes;
    }

    /**
     * @param path
     * @return
     * @throws EsignDemoException
     * @description 根据文件路径，获取文件base64
     * @author 宫清
     * @date 2019年7月21日 下午4:22:08
     */
    public static String getBase64Str(String path) throws EsignDemoException {
        InputStream is = null;
        try {
            is = new FileInputStream(new File(path));
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            return Base64.encodeBase64String(bytes);
        } catch (Exception e) {
            EsignDemoException ex = new EsignDemoException("获取文件输入流失败", e);
            ex.initCause(e);
            throw ex;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    EsignDemoException ex = new EsignDemoException("关闭文件输入流失败", e);
                    ex.initCause(e);
                    throw ex;
                }
            }
        }
    }

    /**
     * @param path 文件路径
     * @return
     * @description 获取文件名称
     * @author 宫清
     * @date 2019年7月21日 下午8:21:16
     */
    public static String getFileName(String path) {
        return new File(path).getName();
    }

    /**
     * @param filePath {@link String} 文件地址
     * @return
     * @throws EsignDemoException
     * @description 获取文件字节流
     * @date 2019年7月10日 上午9:17:00
     * @author 宫清
     */
    public static byte[] getBytes(String filePath) throws EsignDemoException {
        File file = new File(filePath);
        FileInputStream fis = null;
        byte[] buffer = null;
        try {
            fis = new FileInputStream(file);
            buffer = new byte[(int) file.length()];
            fis.read(buffer);
        } catch (Exception e) {
            EsignDemoException ex = new EsignDemoException("获取文件字节流失败", e);
            ex.initCause(e);
            throw ex;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    EsignDemoException ex = new EsignDemoException("关闭文件字节流失败", e);
                    ex.initCause(e);
                    throw ex;
                }
            }
        }
        return buffer;
    }
}
