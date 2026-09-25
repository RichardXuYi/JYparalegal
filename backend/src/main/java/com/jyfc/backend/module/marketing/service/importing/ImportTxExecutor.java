package com.jyfc.backend.module.marketing.service.importing;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 导入落库的<b>唯一事务边界</b>：一次导入的成/败要么全写要么全滚。
 *
 * <p>之所以单独成 bean：{@code @Transactional} 靠代理生效，同类方法自调用会绕过代理。
 * 此前 {@code @Async} 的 {@code startImportAsync} 直接调本类的 {@code runImport}，
 * 注解形同虚设，整个导入其实一直没有事务。</p>
 */
@Service
public class ImportTxExecutor {

    @Transactional
    public ExcelImportResult importFile(ExcelImportHandler handler, File file, Long operatorId) throws Exception {
        try (InputStream in = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            return handler.importWorkbook(workbook, operatorId);
        }
    }
}
