package com.junzhou.decoration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.service.FileAssetService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class HomeDecorationConfig {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String[] MODULES = {"BANNER", "NOTICE", "ACTIVITY"};
    private static final String[] THEMES = {"DEFAULT", "BURGUNDY", "NAVY", "TEAL"};
    private static final String[] TARGETS = {"NONE", "MENU", "MEMBER", "COUPONS"};

    private HomeDecorationConfig() {}

    static ObjectNode defaults() {
        ObjectNode config = JSON.objectNode().put("theme", "DEFAULT");
        ArrayNode modules = config.putArray("modules");
        for (String type : MODULES) {
            ObjectNode module = modules.addObject().put("type", type).put("enabled", false);
            clearContent(module);
        }
        return config;
    }

    static ObjectNode validate(JsonNode input, boolean publishing, FileAssetService files) {
        if (!input.isObject()) throw invalid("装修配置格式无效");
        ObjectNode clean = JSON.objectNode().put("theme", choice(input.path("theme"), THEMES, "首页主题无效"));
        JsonNode modules = input.path("modules");
        if (!modules.isArray() || modules.size() != MODULES.length) throw invalid("首页必须包含三种装修模块");
        Set<String> seen = new HashSet<>();
        ArrayNode cleanModules = clean.putArray("modules");
        for (JsonNode module : modules) {
            String type = choice(module.path("type"), MODULES, "装修模块类型无效");
            if (!seen.add(type)) throw invalid("同一种装修模块只能出现一次");
            if (!module.path("enabled").isBoolean()) throw invalid("请指定装修模块是否开启");
            boolean enabled = module.path("enabled").booleanValue();
            ObjectNode cleanModule = cleanModules.addObject().put("type", type).put("enabled", enabled);
            boolean complete = publishing && enabled;
            if ("NOTICE".equals(type)) {
                cleanModule.put("text", text(module.path("text"), 100, complete, "公告"));
            } else {
                addItems(module.path("items"), cleanModule.putArray("items"), "BANNER".equals(type), complete, files);
            }
        }
        return clean;
    }

    static ObjectNode publicCopy(JsonNode config) {
        ObjectNode copy = config.deepCopy();
        for (JsonNode module : copy.path("modules")) {
            if (!module.path("enabled").booleanValue()) clearContent((ObjectNode) module);
        }
        return copy;
    }

    private static void clearContent(ObjectNode module) {
        if ("NOTICE".equals(module.path("type").asText())) module.put("text", "");
        else module.putArray("items");
    }

    private static void addItems(JsonNode items, ArrayNode clean, boolean banner, boolean complete, FileAssetService files) {
        int max = banner ? 5 : 4;
        String label = banner ? "轮播图" : "活动入口";
        if (!items.isArray() || items.size() > max) throw invalid(label + "最多可添加" + max + "项");
        if (complete && items.isEmpty()) throw invalid("请为已开启的" + label + "添加内容");
        for (JsonNode item : items) {
            String title = text(item.path("title"), 30, complete, label + "标题");
            String target = choice(item.path("target"), TARGETS, "跳转目标无效");
            ObjectNode cleanItem = clean.addObject().put("title", title).put("target", target);
            if (banner) {
                String image = text(item.path("imageUrl"), 255, complete, "轮播图片");
                if (!image.isEmpty()) files.getByUrl(image);
                cleanItem.put("imageUrl", image);
            }
        }
    }

    private static String text(JsonNode node, int max, boolean required, String label) {
        if (!node.isTextual()) throw invalid(label + "格式无效");
        String value = node.textValue().trim();
        if (required && value.isEmpty()) throw invalid("请填写" + label);
        if (value.codePointCount(0, value.length()) > max) throw invalid(label + "不能超过" + max + "字");
        return value;
    }

    private static String choice(JsonNode node, String[] options, String message) {
        if (!node.isTextual() || !Arrays.asList(options).contains(node.textValue())) throw invalid(message);
        return node.textValue();
    }

    private static MiniappApiException invalid(String message) { return new MiniappApiException(400, message); }
}
