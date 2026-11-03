package com.junzhou.controller.admin;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.FileAsset;
import com.junzhou.result.Result;
import com.junzhou.service.FileAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/admin/common")
public class CommonController {
    @Autowired private FileAssetService files;
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        RequestScope.requireEmployeeTenantId();
        return Result.success(files.upload(file));
    }
    @GetMapping("/file")
    public Result<FileAsset> file(@RequestParam("url") String url) {
        return Result.success(files.getByUrl(url));
    }
}
