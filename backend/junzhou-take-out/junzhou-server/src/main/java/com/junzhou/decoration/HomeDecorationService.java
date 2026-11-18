package com.junzhou.decoration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.FileAssetService;
import com.junzhou.service.RbacService;
import lombok.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HomeDecorationService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final FileAssetService files;
    private final RbacService rbac;

    public HomeDecorationService(JdbcTemplate db, ObjectMapper json, FileAssetService files, RbacService rbac) {
        this.db = db;
        this.json = json;
        this.files = files;
        this.rbac = rbac;
    }

    public AdminState read() {
        return read(RequestScope.requireEmployeeTenantId());
    }

    private AdminState read(long tenant) {
        return db.query("SELECT version,draft_json,published_json FROM tenant_home_decoration WHERE tenant_id=?",
            (rs, row) -> new AdminState(rs.getLong("version"), parse(rs.getString("draft_json")),
                parse(rs.getString("published_json"))), tenant)
            .stream().findFirst().orElseGet(() -> new AdminState(0, HomeDecorationConfig.defaults(), null));
    }

    @Transactional
    public AdminState save(long expectedVersion, JsonNode config) {
        long tenant = lockAuthorizedTenant();
        AdminState current = requireVersion(tenant, expectedVersion);
        JsonNode draft = HomeDecorationConfig.validate(config, false, files);
        return write(tenant, current, draft, current.getPublished());
    }

    @Transactional
    public AdminState publish(long expectedVersion) {
        long tenant = lockAuthorizedTenant();
        AdminState current = requireVersion(tenant, expectedVersion);
        if (current.getVersion() == 0) throw new MiniappApiException(400, "请先保存装修草稿");
        JsonNode published = HomeDecorationConfig.validate(current.getDraft(), true, files);
        return write(tenant, current, current.getDraft(), published);
    }

    @Transactional
    public AdminState reset(long expectedVersion) {
        long tenant = lockAuthorizedTenant();
        AdminState current = requireVersion(tenant, expectedVersion);
        if (current.getPublished() == null) return current;
        return write(tenant, current, current.getDraft(), null);
    }

    public PublicState publicHome(String storeId) {
        List<PublicState> result = db.query(
            "SELECT t.id AS tenant_id,d.published_json FROM store s JOIN tenant t ON t.id=s.tenant_id "
                + "LEFT JOIN tenant_home_decoration d ON d.tenant_id=t.id WHERE s.id=? AND s.status=1 AND t.status=1",
            (rs, row) -> {
                JsonNode config = parse(rs.getString("published_json"));
                return new PublicState(rs.getString("tenant_id"),
                    config == null ? null : HomeDecorationConfig.publicCopy(config));
            }, positiveStoreId(storeId));
        if (result.isEmpty()) throw new MiniappApiException(404, "门店不存在或已停用");
        return result.get(0);
    }

    private long lockAuthorizedTenant() {
        long tenant = RequestScope.requireEmployeeTenantId();
        // Serialize the first write too. The lock helper invalidates previously loaded RBAC state.
        rbac.lock();
        rbac.require(PermissionAction.DECORATION_MANAGE);
        return tenant;
    }

    private AdminState requireVersion(long tenant, long expectedVersion) {
        AdminState current = read(tenant);
        if (current.getVersion() != expectedVersion) throw conflict();
        return current;
    }

    private AdminState write(long tenant, AdminState current, JsonNode draft, JsonNode published) {
        long version = current.getVersion() + 1;
        String publishedJson = published == null ? null : published.toString();
        if (current.getVersion() == 0) {
            db.update("INSERT INTO tenant_home_decoration(tenant_id,draft_json,published_json,version) VALUES(?,?,?,?)",
                tenant, draft.toString(), publishedJson, version);
        } else if (db.update("UPDATE tenant_home_decoration SET draft_json=?,published_json=?,version=? "
                + "WHERE tenant_id=? AND version=?", draft.toString(), publishedJson, version,
                tenant, current.getVersion()) != 1) {
            throw conflict();
        }
        return new AdminState(version, draft, published);
    }

    private JsonNode parse(String source) {
        if (source == null) return null;
        try {
            return json.readTree(source);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid persisted home decoration", ex);
        }
    }

    private static long positiveStoreId(String value) {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Route parameters are validated before reaching SQL.
        }
        throw new MiniappApiException(400, "门店 ID 须为有效正整数");
    }

    private static MiniappApiException conflict() {
        return new MiniappApiException(409, "装修配置已更新，请重新加载后再操作");
    }

    @Value
    public static class AdminState {
        long version;
        JsonNode draft;
        JsonNode published;
    }

    @Value
    public static class PublicState {
        String merchantId;
        JsonNode config;
    }
}
