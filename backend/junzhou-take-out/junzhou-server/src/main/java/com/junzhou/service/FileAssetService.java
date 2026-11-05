package com.junzhou.service;

import com.junzhou.entity.FileAsset;
import org.springframework.web.multipart.MultipartFile;

/** 文件资产上传、归属查询与引用校验业务接口。 */
public interface FileAssetService {
    /** 上传当前租户的公开目录图片并登记归属。 */
    String upload(MultipartFile file);

    /** 根据公开地址查询当前租户拥有的文件。 */
    FileAsset getByUrl(String url);

    /** 校验新增或变更的文件引用是否属于当前租户。 */
    void validateReference(String proposed, String existing);
}
