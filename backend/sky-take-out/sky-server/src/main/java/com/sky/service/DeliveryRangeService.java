package com.sky.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.OrderBusinessException;
import com.sky.properties.ShopProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;

/** 百度地图地理编码与骑行路线配送距离校验。 */
@Service
public class DeliveryRangeService {
    private final ShopProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public DeliveryRangeService(ShopProperties properties) {
        this(properties, createRestTemplate());
    }

    public DeliveryRangeService(ShopProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    public void validate(String deliveryAddress) {
        if (blank(properties.getAddress()) || blank(properties.getBaiduAk())) {
            throw new OrderBusinessException("配送服务暂不可用，请联系商家");
        }
        if (blank(deliveryAddress)) {
            throw new OrderBusinessException("收货地址不能为空");
        }
        try {
            String origin = geocode(properties.getAddress());
            String destination = geocode(deliveryAddress);
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/directionlite/v1/riding")
                    .queryParam("ak", properties.getBaiduAk()).queryParam("origin", origin)
                    .queryParam("destination", destination).queryParam("coord_type", "bd09ll")
                    .build().encode().toUri();
            JSONObject response = request(uri);
            JSONObject result = response.getJSONObject("result");
            JSONArray routes = result == null ? null : result.getJSONArray("routes");
            if (routes == null || routes.isEmpty() || routes.getJSONObject(0) == null) {
                throw new OrderBusinessException("无法获取配送路线，请检查收货地址");
            }
            BigDecimal distance = routes.getJSONObject(0).getBigDecimal("distance");
            if (distance == null || distance.signum() < 0) {
                throw new OrderBusinessException("无法获取配送距离，请稍后重试");
            }
            if (distance.compareTo(new BigDecimal("5000")) > 0) {
                throw new OrderBusinessException("收货地址超出配送范围（5公里内）");
            }
        } catch (OrderBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // 不向客户端泄露包含 AK 的请求地址或第三方错误细节。
            throw new OrderBusinessException("配送距离校验失败，请稍后重试");
        }
    }

    private String geocode(String address) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3/")
                .queryParam("address", address).queryParam("output", "json")
                .queryParam("ak", properties.getBaiduAk()).build().encode().toUri();
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

    private JSONObject request(URI uri) {
        JSONObject response = JSONObject.parseObject(restTemplate.getForObject(uri, String.class));
        if (response == null || !Integer.valueOf(0).equals(response.getInteger("status"))) {
            throw new OrderBusinessException("地图服务暂不可用，请稍后重试");
        }
        return response;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
