package com.junzhou.security;

/** Exact action catalogue. No wildcard or implicit administrator bypass. */
public enum PermissionAction {
    DISH_READ("dish:read"),
    DISH_CREATE("dish:create"),
    DISH_UPDATE("dish:update"),
    DISH_DELETE("dish:delete"),
    DISH_STATUS("dish:status"),
    CATEGORY_READ("category:read"),
    CATEGORY_CREATE("category:create"),
    CATEGORY_UPDATE("category:update"),
    CATEGORY_DELETE("category:delete"),
    SETMEAL_READ("setmeal:read"),
    SETMEAL_CREATE("setmeal:create"),
    SETMEAL_UPDATE("setmeal:update"),
    SETMEAL_DELETE("setmeal:delete"),
    SETMEAL_STATUS("setmeal:status"),
    DINING_READ("dining:read"),
    DINING_MANAGE("dining:manage"),
    DINING_CLOSE("dining:close"),
    ORDER_DINE_COMPLETE("order:dine:complete"),
    ORDER_READ("order:read"),
    ORDER_ACCEPT("order:accept"),
    ORDER_REJECT("order:reject"),
    ORDER_CANCEL("order:cancel"),
    ORDER_DELIVER("order:deliver"),
    ORDER_COMPLETE("order:complete"),
    ORDER_REFUND("order:refund"),
    ORDER_PICKUP_READY("order:pickup:ready"),
    ORDER_PICKUP_VERIFY("order:pickup:verify"),
    REPORT_READ("report:read"),
    REPORT_EXPORT("report:export"),
    EMPLOYEE_READ("employee:read"),
    EMPLOYEE_CREATE("employee:create"),
    EMPLOYEE_UPDATE("employee:update"),
    EMPLOYEE_STATUS("employee:status"),
    EMPLOYEE_MANAGE("employee:manage"),
    SHOP_READ("shop:read"),
    SHOP_UPDATE("shop:update"),
    WORKSPACE_READ("workspace:read"),
    FILE_UPLOAD("file:upload");
    private final String code;
    PermissionAction(String code) { this.code = code; }
    public String getCode() { return code; }
    public static PermissionAction fromCode(String code) {
        for (PermissionAction action : values()) if (action.code.equals(code)) return action;
        throw new IllegalArgumentException("Unknown permission action");
    }
}
