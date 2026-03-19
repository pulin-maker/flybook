package com.bytedance.modules.upload;

import com.bytedance.common.Result; // 假设你有一个通用的 Result 返回类
import com.bytedance.modules.upload.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Autowired
    private StorageService storageService;

    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 调用 Service 保存文件
            String url = storageService.uploadFile(file);

            // 构造返回结果
            Map<String, String> map = new HashMap<>();
            map.put("url", url); // 返回完整的 URL

            return Result.success(map);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("上传失败: " + e.getMessage());
        }
    }
}

