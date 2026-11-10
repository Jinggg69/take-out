package com.junzhou.security;
import lombok.Data;
@Data public class RbacGrantRow {
 private String roleCode, dataScope, permissionCode, name;
 private Long storeId;
}
