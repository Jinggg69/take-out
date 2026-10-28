package com.junzhou.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 门店及百度地图配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.shop")
public class ShopProperties {
    //门店完整地址
    private String address;
    //百度地图服务端应用AK
    private String baiduAk;
}
