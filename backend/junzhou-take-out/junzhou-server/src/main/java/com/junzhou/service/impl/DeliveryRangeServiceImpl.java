package com.junzhou.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.properties.ShopProperties;
import com.junzhou.service.DeliveryRangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;

/**
 * 配送范围业务实现类
 */
@Service
public class DeliveryRangeServiceImpl implements DeliveryRangeService {
    //配送范围上限，单位：米
    private static final BigDecimal MAX_DELIVERY_DISTANCE = new BigDecimal("5000");
    //地图接口连接和读取超时时间，单位：毫秒
    private static final int TIMEOUT_MILLIS = 5000;

    private final ShopProperties shopProperties;
    private final RestTemplate restTemplate;

    /**
     * 创建配送范围业务对象，使用带超时配置的HTTP客户端
     * @param shopProperties 门店及百度地图配置
     */
    @Autowired
    public DeliveryRangeServiceImpl(ShopProperties shopProperties) {
        this(shopProperties, createRestTemplate());
    }

    /**
     * 使用指定的HTTP客户端创建配送范围业务对象
     * @param shopProperties 门店及百度地图配置
     * @param restTemplate 地图接口HTTP客户端
     */
    public DeliveryRangeServiceImpl(ShopProperties shopProperties, RestTemplate restTemplate) {
        this.shopProperties = shopProperties;
        this.restTemplate = restTemplate;
    }

    /**
     * 创建地图接口HTTP客户端，设置连接和读取超时时间
     * @return HTTP客户端
     */
    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MILLIS);
        factory.setReadTimeout(TIMEOUT_MILLIS);
        return new RestTemplate(factory);
    }

    /**
     * 校验收货地址是否在门店5公里配送范围内
     * @param deliveryAddress 完整收货地址
     */
    @Override
    public void validate(String deliveryAddress) {
        //1、校验门店配置和收货地址
        if (blank(shopProperties.getAddress()) || blank(shopProperties.getBaiduAk())) {
            throw new OrderBusinessException("配送服务暂不可用，请联系商家");
        }
        if (blank(deliveryAddress)) {
            throw new OrderBusinessException("收货地址不能为空");
        }
        try {
            //2、将门店地址和收货地址解析为经纬度坐标
            String origin = geocode(shopProperties.getAddress());
            String destination = geocode(deliveryAddress);
            //3、查询门店到收货地址的骑行路线
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/directionlite/v1/riding")
                    .queryParam("ak", shopProperties.getBaiduAk()).queryParam("origin", origin)
                    .queryParam("destination", destination).queryParam("coord_type", "bd09ll")
                    .build().encode().toUri();
            JSONObject response = request(uri);
            JSONObject result = response.getJSONObject("result");
            JSONArray routes = result == null ? null : result.getJSONArray("routes");
            if (routes == null || routes.isEmpty() || routes.getJSONObject(0) == null) {
                throw new OrderBusinessException("无法获取配送路线，请检查收货地址");
            }
            //4、校验路线距离，5000米以内允许配送
            BigDecimal distance = routes.getJSONObject(0).getBigDecimal("distance");
            if (distance == null || distance.signum() < 0) {
                throw new OrderBusinessException("无法获取配送距离，请稍后重试");
            }
            if (distance.compareTo(MAX_DELIVERY_DISTANCE) > 0) {
                throw new OrderBusinessException("收货地址超出配送范围（5公里内）");
            }
        } catch (OrderBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // 不向客户端泄露包含 AK 的请求地址或第三方错误细节。
            throw new OrderBusinessException("配送距离校验失败，请稍后重试");
        }
    }

    /**
     * 调用百度地理编码接口，将地址转换为纬度、经度坐标
     * @param address 待解析的完整地址
     * @return 纬度、经度字符串，保留6位小数
     */
    private String geocode(String address) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3/")
                .queryParam("address", address).queryParam("output", "json")
                .queryParam("ak", shopProperties.getBaiduAk()).build().encode().toUri();
        JSONObject response = request(uri);
        JSONObject result = response.getJSONObject("result");
        JSONObject location = result == null ? null : result.getJSONObject("location");
        if (location == null) {
            throw new OrderBusinessException("无法解析地址，请检查门店或收货地址");
        }
        BigDecimal latitude = location.getBigDecimal("lat");
        BigDecimal longitude = location.getBigDecimal("lng");
        if (latitude == null || longitude == null || latitude.abs().compareTo(new BigDecimal("90")) > 0
                || longitude.abs().compareTo(new BigDecimal("180")) > 0) {
            throw new OrderBusinessException("无法解析地址坐标，请检查地址");
        }
        return latitude.setScale(6, RoundingMode.HALF_UP).toPlainString() + ","
                + longitude.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 发送地图查询请求并校验百度接口返回状态
     * @param uri 地图接口请求地址
     * @return 地图接口返回的数据
     */
    private JSONObject request(URI uri) {
        JSONObject response = JSONObject.parseObject(restTemplate.getForObject(uri, String.class));
        if (response == null || !Integer.valueOf(0).equals(response.getInteger("status"))) {
            throw new OrderBusinessException("地图服务暂不可用，请稍后重试");
        }
        return response;
    }

    /**
     * 判断字符串是否为空或只包含空白字符
     * @param value 待判断的字符串
     * @return 是否为空
     */
    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
