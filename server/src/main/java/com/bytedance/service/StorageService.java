package com.bytedance.service;

import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    // 从 application.yml 读取物理存储路径 (例如 D:/im-upload/)
    @Value("${storage.upload-path}")
    private String uploadPath;

    // 从 application.yml 读取访问前缀 (例如 /files/)
    @Value("${storage.access-prefix}")
    private String accessPrefix;

    // 从 application.yml 读取域名 (例如 http://localhost:8080)
    @Value("${storage.domain}")
    private String domain;

    /**
     * 上传文件方法
     * @param file 前端传来的文件对象
     * @return 可以在浏览器访问的完整 HTTP URL
     */
    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }

        // 1. 生成新文件名 (防止中文乱码和同名覆盖)
        // 获取原后缀，如 .jpg
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 生成 UUID 文件名，如 550e8400-e29b... .jpg
        String newFileName = UUID.randomUUID().toString() + suffix;

        // 2. 创建目标文件对象
        // 最终物理路径: D:/im-upload/uuid.jpg
        File dest = new File(uploadPath + newFileName);

        // 确保父目录存在（如果 D:/im-upload/ 不存在则自动创建）
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }

        try {
            // 3. 核心操作：把内存里的文件写入磁盘
            file.transferTo(dest);

            // 4. 拼接返回给前端的 URL
            // 格式: http://localhost:8080/files/uuid.jpg
            return domain + accessPrefix + newFileName;

        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BizException(ErrorCode.PARAM_INVALID, "文件保存失败");
        }
    }
}

