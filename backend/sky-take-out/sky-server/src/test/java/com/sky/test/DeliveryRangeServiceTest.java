package com.sky.test;

import com.sky.exception.OrderBusinessException;
import com.sky.properties.ShopProperties;
import com.sky.service.DeliveryRangeService;
import com.sky.service.impl.DeliveryRangeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeliveryRangeServiceTest {
    private ShopProperties properties;
    private DeliveryRangeService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new ShopProperties();
        properties.setAddress("北京市海淀区上地十街10号");
        properties.setBaiduAk("test-ak");
        RestTemplate client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
        service = new DeliveryRangeServiceImpl(properties, client);
    }

    @Test
    void exactlyFiveKilometersIsAcceptedAndCoordinatesUseLatitudeFirst() {
        geocoding();
        route("5000");
        assertDoesNotThrow(() -> service.validate("北京市海淀区测试路1号"));
        server.verify();
    }

    @Test
    void overFiveKilometersIsRejected() {
        geocoding();
        route("5001");
        assertThrows(OrderBusinessException.class, () -> service.validate("北京市海淀区测试路1号"));
        server.verify();
    }

    @Test
    void missingCredentialsAndEmptyAddressNeverBypassValidation() {
        properties.setBaiduAk("");
        assertThrows(OrderBusinessException.class, () -> service.validate("地址"));
        properties.setBaiduAk("test-ak");
        assertThrows(OrderBusinessException.class, () -> service.validate(" "));
        properties.setAddress("");
        assertThrows(OrderBusinessException.class, () -> service.validate("地址"));
    }

    @Test
    void geocodingErrorAndMissingStatusAreRejected() {
        server.expect(anything()).andRespond(withSuccess("{\"status\":1,\"msg\":\"无效AK\"}", MediaType.APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.validate("地址"));
        server.verify();
    }

    @Test
    void malformedGeocodingResponseIsRejected() {
        server.expect(anything()).andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.validate("地址"));
        server.verify();
    }

    @Test
    void networkFailureIsABusinessError() {
        server.expect(anything()).andRespond(withException(new IOException("timeout")));
        assertThrows(OrderBusinessException.class, () -> service.validate("地址"));
        server.verify();
    }

    @Test
    void emptyRoutesAndMissingDistanceAreRejected() {
        geocoding();
        server.expect(anything()).andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[]}}", MediaType.APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.validate("北京市海淀区测试路1号"));
        server.verify();
    }

    @Test
    void negativeDistanceIsRejected() {
        geocoding();
        route("-1");
        assertThrows(OrderBusinessException.class, () -> service.validate("北京市海淀区测试路1号"));
        server.verify();
    }

    private void geocoding() {
        server.expect(request -> assertTrue(java.net.URLDecoder.decode(request.getURI().getRawQuery(), "UTF-8")
                        .contains("address=北京市海淀区上地十街10号")))
                .andExpect(queryParam("ak", "test-ak"))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"location\":{\"lat\":40.01116,\"lng\":116.339303}}}", MediaType.APPLICATION_JSON));
        server.expect(request -> assertTrue(java.net.URLDecoder.decode(request.getURI().getRawQuery(), "UTF-8")
                        .contains("address=北京市海淀区测试路1号")))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"location\":{\"lat\":39.936404,\"lng\":116.452562}}}", MediaType.APPLICATION_JSON));
    }

    private void route(String distance) {
        server.expect(queryParam("origin", "40.011160,116.339303"))
                .andExpect(queryParam("destination", "39.936404,116.452562"))
                .andExpect(queryParam("coord_type", "bd09ll"))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[{\"distance\":" + distance + "}]}}", MediaType.APPLICATION_JSON));
    }
}
