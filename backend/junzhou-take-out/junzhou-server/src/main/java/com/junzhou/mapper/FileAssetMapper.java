package com.junzhou.mapper;
import com.junzhou.entity.FileAsset;
import org.apache.ibatis.annotations.*;
@Mapper
public interface FileAssetMapper {
    @Insert("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/>insert into file_asset(id,tenant_id,uploader_employee_id,purpose,object_key,public_url,created_at) select #{id},#{tenant},#{uploaderEmployeeId},#{purpose},#{objectKey},#{publicUrl},#{createdAt} from employee e join tenant t on t.id=e.tenant_id where e.id=#{uploaderEmployeeId} and e.tenant_id=#{tenant} and e.status=1 and t.status=1</script>")
    int insert(FileAsset asset);
    @Select("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/>select * from file_asset where public_url=#{url} and tenant_id=#{tenant} and purpose='PUBLIC_CATALOG'</script>")
    FileAsset findOwnedByUrl(String url);
}
