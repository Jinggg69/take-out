package com.junzhou.security;

/** Range attached to one employee-role assignment, never to the employee globally. */
public enum DataScope {
    CURRENT_STORE, SPECIFIED_STORES, TENANT_STORES
}
