package com.junzhou.security;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface RequireActions { PermissionAction[] value(); }
