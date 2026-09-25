package cn.tsign.hz.comm;

import cn.tsign.hz.exception.EsignDemoException;

import java.io.File;

/**
 * @description  文件基础信息封装类
 * @author  澄泓
 * @date  2020/10/26 14:54
 * @version JDK1.7
 */
public class EsignFileBean {
    //文件名称
    private String fileName;
    //文件大小
    private int fileSize;
    //文件内容MD5
    private String fileContentMD5;
    //文件地址
    private String filePath;


    public EsignFileBean(String filePath) throws EsignDemoException {
            this.filePath=filePath;
            this.fileContentMD5 = FileTransformation.getFileContentMD5(filePath);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new EsignDemoException("文件不存在");
            }
            this.fileName = file.getName();
            this.fileSize = (int) file.length();
    }

    public String getFileName() {
        return fileName;
    }

    public int getFileSize() {
        return fileSize;
    }

    public String getFileContentMD5() {
        return fileContentMD5;
    }

    /**
     * 传入本地文件地址获取二进制数据
     * @return
     * @throws EsignDemoException
     */
    public byte[] getFileBytes() throws EsignDemoException {
        return FileTransformation.fileToBytes(filePath);
    }
}
