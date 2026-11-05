package com.junzhou.service.impl;

import com.junzhou.service.FileAssetService;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.FileAsset;
import com.junzhou.mapper.FileAssetMapper;
import com.junzhou.utils.AliOssUtil;
import com.junzhou.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

/** Only public catalog images are accepted; private attachments require a separate policy. */
@Service @Slf4j
public class FileAssetServiceImpl implements FileAssetService {
    private final FileAssetMapper files;
    private final AliOssUtil oss;
    public FileAssetServiceImpl(FileAssetMapper files, AliOssUtil oss) { this.files=files; this.oss=oss; }
    @Override
    public String upload(MultipartFile file) {
        Long tenant=RequestScope.requireEmployeeTenantId();
        if(file==null || file.isEmpty() || file.getSize()>=2*1024*1024) throw new BaseException("请上传小于2MB的PNG或JPEG图片");
        byte[] bytes; String extension;
        try (ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IOException("Unsupported image");
            ImageReader reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Arrays.asList("png","jpeg","jpg").contains(format)) throw new IOException("Unsupported image");
                reader.setInput(input,true,true);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || width*height>16000000) throw new IOException("Image too large");
                extension=format.equals("png") ? "png" : "jpg";
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(!ImageIO.write(reader.read(0),extension,out)) throw new IOException("Unsupported image");
                bytes=out.toByteArray();
                if(bytes.length>=2*1024*1024) throw new IOException("Image too large");
            } finally { reader.dispose(); }
        } catch(IOException | RuntimeException ex) { throw new BaseException("请上传有效的PNG或JPEG图片"); }
        String id=UUID.randomUUID().toString();
        String key="tenants/"+tenant+"/public-catalog/"+id+"."+extension;
        try {
            String url=oss.upload(bytes,key);
            if(url==null || url.length()>255 || !url.startsWith("https://")) throw new IllegalStateException("Invalid catalog image URL");
            FileAsset asset=new FileAsset(); asset.setId(id); asset.setTenantId(tenant);
            asset.setUploaderEmployeeId(RequestScope.current().getEmployeeId()); asset.setPurpose("PUBLIC_CATALOG");
            asset.setObjectKey(key); asset.setPublicUrl(url); asset.setCreatedAt(LocalDateTime.now());
            if(files.insert(asset)!=1) throw new SecurityException("Upload ownership changed");
            return url;
        } catch(RuntimeException ex) {
            try { oss.delete(key); } catch(RuntimeException cleanup) { log.warn("上传补偿清理失败，objectKey={}",key); }
            if(ex instanceof SecurityException) throw ex;
            log.warn("文件上传或归属登记失败，objectKey={}",key);
            throw new BaseException("文件上传失败");
        }
    }
    @Override
    public FileAsset getByUrl(String url) {
        RequestScope.requireEmployeeTenantId();
        FileAsset asset=files.findOwnedByUrl(url);
        if(asset==null) throw new SecurityException("File not owned by current tenant");
        return asset;
    }
    @Override
    public void validateReference(String proposed, String existing) {
        RequestScope.requireEmployeeTenantId();
        if(proposed==null || proposed.trim().isEmpty() || proposed.equals(existing)) return;
        getByUrl(proposed);
    }
}
