package com.junzhou.service;

import com.junzhou.entity.Orders;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.utils.WeChatPayUtil;
import com.junzhou.payment.VerifiedNotification;

public interface PaymentAccountService {
    /**
     * Return the current store's configured account ID for a new order.
     * @return immutable account ID
     * @throws IllegalStateException if account configuration is missing or inconsistent
     */
    String currentAccountId();
    /**
     * Resolve and validate the original account under the current order scope.
     * @param order persisted order carrying its original ownership and account
     * @return matching deployment credentials
     * @throws SecurityException if ownership differs from the current scope
     * @throws IllegalStateException if the original configuration is unavailable or changed
     */
    WeChatProperties properties(Orders order);
    /**
     * Resolve a provider using the order's original account without fallback.
     * @param order persisted order to pay, query, close or refund
     * @return provider authenticated with that account
     * @throws SecurityException if account ownership differs from the order scope
     * @throws IllegalStateException if the original credentials are unavailable
     */
    WeChatPayUtil provider(Orders order);
    /**
     * Locate ownership using a verified account and order number.
     * @param notice cryptographically verified and decrypted notification
     * @return matching persisted order, to establish system scope before business processing
     * @throws SecurityException if the account does not own the order
     */
    Orders locate(VerifiedNotification notice);
}
