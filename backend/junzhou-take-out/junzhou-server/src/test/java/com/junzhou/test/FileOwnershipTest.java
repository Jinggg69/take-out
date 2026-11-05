package com.junzhou.test;
import com.junzhou.controller.admin.CommonController;
import com.junzhou.utils.AliOssUtil;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.FileAsset;
import com.junzhou.mapper.FileAssetMapper;
import com.junzhou.service.FileAssetService;
import com.junzhou.service.impl.FileAssetServiceImpl;
import com.junzhou.exception.BaseException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileOwnershipTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private FileAssetService files;
    private AliOssUtil oss;
    private CommonController controller;
    @BeforeEach void setup() throws Exception {
        JdbcDataSource db=new JdbcDataSource(); db.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");
        jdbc=new JdbcTemplate(db);
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),db));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(FileAssetMapper.class);
        session=new SqlSessionFactoryBuilder().build(config).openSession(true); session.getConnection();
        jdbc.execute("create table tenant(id bigint primary key,status int)");
        jdbc.execute("create table employee(id bigint primary key,tenant_id bigint,status int)");
        jdbc.execute("insert into tenant values(101,1),(102,1)");
        jdbc.execute("insert into employee values(42,101,1),(84,102,1)");
        String migration=new String(Files.readAllBytes(Paths.get("../../../database/migrations/20261103_01_file_asset.sql")),java.nio.charset.StandardCharsets.UTF_8);
        migration=migration.replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin","");
        jdbc.execute(migration); jdbc.execute(migration);
        oss=mock(AliOssUtil.class);
        when(oss.upload(any(),anyString())).thenAnswer(i -> "https://bucket.test/"+i.getArgument(1));
        files=new FileAssetServiceImpl(session.getMapper(FileAssetMapper.class),oss);
        controller=new CommonController(); ReflectionTestUtils.setField(controller,"files",files);
    }
    @AfterEach void clear() { RequestScope.clear(); if(session!=null) session.close(); }
    private void scope(long employee,long tenant,long store) {
        RequestScope.set(new RequestScope(employee,null,tenant,store,Collections.singleton(store)));
    }
    private MockMultipartFile image() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("file","../../forged.html","text/html",bytes.toByteArray());
    }
    @Test void uploadRejectsMissingScopeBeforeWritingAnObject() throws Exception {
        MockMultipartFile file=image();
        assertThrows(SecurityException.class,()->controller.upload(file)); verifyNoInteractions(oss);
    }
    @Test void ownedMetadataAndReferenceAreTenantScopedAndLegacyImagesStayUnchanged() throws Exception {
        scope(42,101,1001);
        String url=controller.upload(image()).getData();
        assertTrue(url.startsWith("https://bucket.test/tenants/101/public-catalog/")); assertTrue(url.endsWith(".png"));
        FileAsset asset=controller.file(url).getData();
        assertEquals(101L,asset.getTenantId()); assertEquals(42L,asset.getUploaderEmployeeId());
        assertEquals("PUBLIC_CATALOG",asset.getPurpose());
        scope(42,101,1002); files.validateReference(url,null);
        scope(84,102,2001);
        assertThrows(SecurityException.class,()->controller.file(url));
        assertThrows(SecurityException.class,()->files.validateReference(url,null));
        assertDoesNotThrow(()->files.validateReference("https://legacy.test/a.png","https://legacy.test/a.png"));
        assertThrows(SecurityException.class,()->files.validateReference("https://legacy.test/a.png",null));
        assertEquals(1,jdbc.queryForObject("select count(*) from file_asset",Integer.class));
    }
    @Test void invalidImageAndUnauthorisedStoreCannotWriteObjects() {
        scope(42,101,1001);
        assertThrows(BaseException.class,()->files.upload(new MockMultipartFile("file","a.png","image/png","<svg/>".getBytes())));
        RequestScope.set(new RequestScope(42L,null,101L,1002L,Collections.singleton(1001L)));
        assertThrows(SecurityException.class,()->files.upload(null));
        verifyNoInteractions(oss);
    }
    @Test void databaseFailureCompensatesAndDisabledEmployeeCannotRegisterAnUpload() throws Exception {
        scope(42,101,1001); jdbc.update("update employee set status=0 where id=42");
        MockMultipartFile file=image();
        assertThrows(SecurityException.class,()->files.upload(file));
        verify(oss).delete(argThat(key -> key.startsWith("tenants/101/public-catalog/")));
        assertEquals(0,jdbc.queryForObject("select count(*) from file_asset",Integer.class));
    }

    @Test void catalogCreateAndUpdateRejectForeignImageBeforeAnyBusinessWrite() throws Exception {
        scope(42,101,1001); String foreign=files.upload(image());
        scope(84,102,2001);
        com.junzhou.mapper.CategoryMapper categories=mock(com.junzhou.mapper.CategoryMapper.class);
        when(categories.lockOwned(2L,102L)).thenReturn(2L);
        com.junzhou.mapper.DishMapper dishes=mock(com.junzhou.mapper.DishMapper.class);
        when(dishes.getByIdForUpdate(1L)).thenReturn(com.junzhou.entity.Dish.builder().id(1L).image("legacy").build());
        com.junzhou.service.impl.DishServiceImpl dishService=new com.junzhou.service.impl.DishServiceImpl();
        ReflectionTestUtils.setField(dishService,"categoryMapper",categories);
        ReflectionTestUtils.setField(dishService,"dishMapper",dishes);
        ReflectionTestUtils.setField(dishService,"fileAssets",files);
        com.junzhou.dto.DishDTO dish=new com.junzhou.dto.DishDTO(); dish.setId(1L); dish.setCategoryId(2L); dish.setImage(foreign);
        assertThrows(SecurityException.class,()->dishService.saveWithFlavor(dish));
        assertThrows(SecurityException.class,()->dishService.updateWithFlavor(dish));
        verify(dishes,never()).insert(any()); verify(dishes,never()).updateById(any());
        com.junzhou.mapper.SetmealMapper meals=mock(com.junzhou.mapper.SetmealMapper.class);
        when(meals.getByIdForUpdate(1L)).thenReturn(com.junzhou.entity.Setmeal.builder().id(1L).image("legacy").build());
        com.junzhou.service.impl.SetmealServiceImpl mealService=new com.junzhou.service.impl.SetmealServiceImpl();
        ReflectionTestUtils.setField(mealService,"categoryMapper",categories);
        ReflectionTestUtils.setField(mealService,"setmealMapper",meals);
        ReflectionTestUtils.setField(mealService,"setmealDishMapper",mock(com.junzhou.mapper.SetmealDishMapper.class));
        ReflectionTestUtils.setField(mealService,"fileAssets",files);
        com.junzhou.dto.SetmealDTO meal=new com.junzhou.dto.SetmealDTO(); meal.setId(1L); meal.setCategoryId(2L); meal.setImage(foreign);
        assertThrows(SecurityException.class,()->mealService.saveWithDish(meal));
        assertThrows(SecurityException.class,()->mealService.updateWithDish(meal));
        verify(meals,never()).insert(any()); verify(meals,never()).updateById(any());
    }
}
