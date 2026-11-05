package com.junzhou.security;
import lombok.Data;
import java.util.Set;
@Data public class RoleGrantInput {
 private String roleCode;
 private DataScope dataScope;
 private Set<Long> storeIds;
}
