package com.junzhou.service;

import com.junzhou.entity.DiningTable;
import java.util.List;

/** 扫码堂食桌台与桌次。无效输入或过期桌次抛出 OrderBusinessException。 */
public interface DiningService {
    /** @return 当前授权门店的桌台 */
    List<DiningTable> tables();
    /** @param table 仅接受 id、name、enabled；归属由服务端确定 @return 保存的桌台 */
    DiningTable save(DiningTable table);
    /** @param id 当前店桌台 ID @return 桌码 payload；rotate 为 true 时撤销旧码 */
    String code(Long id, boolean rotate);
    /** @param token 桌码中的随机 token @return 服务端解析的门店和桌台，不含顾客信息 */
    DiningTable resolve(String token);
    /** @param token 经顾客确认的桌码 @return 顾客加入的当前桌次 */
    DiningTable join(String token);
    /** 在订单事务内锁桌台并验证成员、桌码和当前桌次。@return 有效桌台快照 */
    DiningTable validate(Long tableId,String session,String token);
    /** @param id 当前店桌台 ID；存在未完成订单时拒绝结束桌次 */
    void close(Long id);
    /** @param id 当前门店已支付并已接单的堂食订单 ID */
    void complete(Long id);
}
