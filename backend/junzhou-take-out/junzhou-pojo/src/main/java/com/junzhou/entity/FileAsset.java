package com.junzhou.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class FileAsset {
    private String id;
    private Long tenantId;
    private Long uploaderEmployeeId;
    private String purpose;
    private String objectKey;
    private String publicUrl;
    private LocalDateTime createdAt;
}
