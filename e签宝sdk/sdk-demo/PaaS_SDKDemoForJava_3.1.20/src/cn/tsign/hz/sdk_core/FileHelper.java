package cn.tsign.hz.sdk_core;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;

import cn.tsign.hz.exception.DefineException;

/**
 * desciption SDK文件辅助类
 */
public class FileHelper {

    //--------------------------------公有方法 start-------------------------------------

    /**
     * description 获取文件字节流
     **/
    public static byte[] getFileBytes(String filePath) throws DefineException {
        File file = new File(filePath);
        FileInputStream fis = null;
        byte[] buffer;

        try {
            fis = new FileInputStream(file);
            buffer = new byte[(int) file.length()];
            fis.read(buffer);
        } catch (Exception e) {
            throw new DefineException(
                    MessageFormat.format("获取文件字节流异常：{0}", e.getMessage()));
        }finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    throw new DefineException(
                            MessageFormat.format("关闭字节流异常：{0}", e.getMessage()));
                }
            }
        }
        return buffer;
    }

    /**
     * description 文件字节流保存为本地文件
     **/
    public static void saveFileByStream(byte[] bytes, String dstFilePath)
            throws DefineException{

        BufferedOutputStream bos = null;
        File dstFile = new File(dstFilePath);
        //文件目录
        File directory = new File(dstFile.getParent());
        if (!directory.exists() && directory.isDirectory()) {
            directory.mkdirs();
        }

        try {
            bos = new BufferedOutputStream(new FileOutputStream(dstFile));
            bos.write(bytes);
            bos.flush();
        } catch (Exception e) {
            throw new DefineException(
                    MessageFormat.format("文件字节流保存为本地文件异常：{0}", e.getMessage()));
        }finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    throw new DefineException(
                            MessageFormat.format("关闭文件字节流异常：{0}", e.getMessage()));
                }
            }
        }
    }

    //--------------------------------公有方法 end---------------------------------------

}


