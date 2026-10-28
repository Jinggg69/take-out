package com.junzhou.controller.admin;

import com.junzhou.constant.MessageConstant;
import com.junzhou.result.Result;
import com.junzhou.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 通用接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        String filename = file.getOriginalFilename();
        String extension = "";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && filename.substring(dot + 1).matches("[A-Za-z0-9]{1,10}")) {
                extension = filename.substring(dot);
            }
        }
        String objectName = UUID.randomUUID() + extension;

        try {
            String url = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(url);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
