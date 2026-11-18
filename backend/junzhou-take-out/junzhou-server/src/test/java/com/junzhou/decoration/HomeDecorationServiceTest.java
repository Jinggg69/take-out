package com.junzhou.decoration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.security.AuthorizationSnapshot;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.FileAssetService;
import com.junzhou.service.RbacService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeDecorationServiceTest {
    private JdbcTemplate db;
    private ObjectMapper json;
    private HomeDecorationService decoration;
    private FileAssetService files;

    @BeforeEach
    void setup() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE tenant(id BIGINT PRIMARY KEY, status INT NOT NULL)");
        db.execute("CREATE TABLE store(id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, status INT NOT NULL)");
        db.execute("CREATE TABLE tenant_home_decoration(tenant_id BIGINT PRIMARY KEY, draft_json CLOB, "
            + "published_json CLOB, version BIGINT NOT NULL DEFAULT 0)");
        db.update("INSERT INTO tenant VALUES (1,1),(2,1),(3,0)");
        db.update("INSERT INTO store VALUES (11,1,1),(12,1,1),(21,2,1),(22,2,0),(31,3,1)");
        json = new ObjectMapper();
        files = mock(FileAssetService.class);
        decoration = new HomeDecorationService(db, json, files, mock(RbacService.class));
        RequestScope.set(new RequestScope(71L, null, 1L, 11L, Collections.singleton(11L)));
    }

    @AfterEach
    void cleanup() { RequestScope.clear(); }

    @Test
    void draftPublishAndResetKeepTenantAndVisibilityBoundaries() {
        HomeDecorationService.AdminState initial = decoration.read();
        assertEquals(0, initial.getVersion());
        assertNull(initial.getPublished());
        ObjectNode draft = initial.getDraft().deepCopy();
        draft.put("theme", "TEAL");
        HomeDecorationService.AdminState saved = decoration.save(0, draft);
        assertNull(decoration.publicHome("11").getConfig());
        assertEquals(409, assertThrows(MiniappApiException.class, () -> decoration.publish(0)).getStatus());
        assertEquals(draft, decoration.publish(saved.getVersion()).getPublished());
        assertEquals(draft, decoration.publicHome("11").getConfig());
        assertEquals(draft, decoration.publicHome("12").getConfig());
        assertEquals("2", decoration.publicHome("21").getMerchantId());
        assertNull(decoration.publicHome("21").getConfig());
        assertEquals(404, assertThrows(MiniappApiException.class, () -> decoration.publicHome("22")).getStatus());
        assertEquals(404, assertThrows(MiniappApiException.class, () -> decoration.publicHome("31")).getStatus());
        assertEquals(draft, decoration.reset(2).getDraft());
        assertNull(decoration.publicHome("12").getConfig());
    }

    @Test
    void rejectsInvalidTargetsAndUnownedImagesAndRechecksOwnershipAtPublication() {
        ObjectNode draft = decoration.read().getDraft().deepCopy();
        ObjectNode banner = (ObjectNode) draft.path("modules").get(0);
        banner.put("enabled", true);
        ObjectNode slide = banner.withArray("items").addObject()
            .put("imageUrl", "https://example.com/a.png").put("title", "新品").put("target", "EXTERNAL");
        assertEquals(400, assertThrows(MiniappApiException.class, () -> decoration.save(0, draft)).getStatus());
        slide.put("target", "MENU");
        decoration.save(0, draft);
        verify(files).getByUrl("https://example.com/a.png");
        when(files.getByUrl("https://example.com/a.png")).thenThrow(new SecurityException());
        assertThrows(SecurityException.class, () -> decoration.publish(1));
        assertNull(decoration.publicHome("11").getConfig());
        assertTrue(AuthorizationSnapshot.tenantShared(PermissionAction.DECORATION_MANAGE));
    }
}
