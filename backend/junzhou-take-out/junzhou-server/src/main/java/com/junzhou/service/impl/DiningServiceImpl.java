package com.junzhou.service.impl;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.DiningTable;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.DiningMapper;
import com.junzhou.service.DiningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class DiningServiceImpl implements DiningService {
    private final DiningMapper mapper;
    private final com.junzhou.mapper.OrderMapper orders;
    public DiningServiceImpl(DiningMapper mapper,com.junzhou.mapper.OrderMapper orders){this.mapper=mapper;this.orders=orders;}
    /** 按类型、支付和履约前置条件完成堂食订单。 */
    @Override public void complete(Long id){
        RequestScope.requireEmployeeTenantId();
        if(id==null || id<=0 || orders.dineComplete(id)!=1)throw new OrderBusinessException("堂食订单尚未接单、已完成或支付退款状态异常");
    }
    private Long tenant(){return RequestScope.requireTenantId();}
    private Long store(){return RequestScope.requireStoreId();}
    private String random(){return UUID.randomUUID().toString().replace("-","");}
    private OrderBusinessException invalid(){return new OrderBusinessException("桌码或桌次已失效，请重新扫码确认");}
    private DiningTable lock(Long id){
        if(id==null || id<=0)throw invalid();
        DiningTable table=mapper.lock(tenant(),store(),id);if(table==null)throw invalid();return table;
    }
    /** 只查询当前授权门店。 */
    @Override public List<DiningTable> tables(){RequestScope.requireEmployeeTenantId();return mapper.list(tenant(),store());}
    /** 忽略客户端归属、桌码及桌次字段；已有桌台修改受行锁保护。 */
    @Override @Transactional public DiningTable save(DiningTable input){
        RequestScope.requireEmployeeTenantId();
        if(input==null || input.getName()==null || input.getName().trim().isEmpty() || input.getName().length()>64
                || input.getEnabled()==null || (input.getEnabled()!=0 && input.getEnabled()!=1))throw new OrderBusinessException("请填写有效桌号与启用状态");
        DiningTable row=input.getId()==null?new DiningTable():lock(input.getId());
        row.setTenantId(tenant());row.setStoreId(store());row.setName(input.getName().trim());row.setEnabled(input.getEnabled());
        if(row.getId()==null){row.setQrToken(random());mapper.create(row);}else mapper.update(row);
        return row;
    }
    /** 轮换使旧码立即失效；桌码仅供获授权的店员制作桌牌。 */
    @Override @Transactional public String code(Long id,boolean rotate){
        RequestScope.requireEmployeeTenantId();DiningTable table=lock(id);
        if(rotate || table.getQrToken()==null){table.setQrToken(random());mapper.update(table);}
        return "JZ:"+table.getQrToken();
    }
    /** 仅随机桌码可解析归属，响应不包含当前桌次或其他顾客资料。 */
    @Override public DiningTable resolve(String token){
        RequestScope.requireUserId();
        if(token==null || !token.matches("[0-9a-f]{32}"))throw invalid();
        DiningTable row=mapper.resolve(token);if(row==null)throw invalid();
        row.setCurrentSessionId(null);return row;
    }
    /** 加入桌次前核对请求门店；同桌顾客仍各自下单。 */
    @Override @Transactional public DiningTable join(String token){
        DiningTable found=resolve(token);
        if(!tenant().equals(found.getTenantId()) || !store().equals(found.getStoreId()))throw invalid();
        DiningTable row=lock(found.getId());
        if(!Integer.valueOf(1).equals(row.getEnabled()) || !token.equals(row.getQrToken()))throw invalid();
        if(row.getCurrentSessionId()==null){row.setCurrentSessionId(random());mapper.open(row);mapper.update(row);}
        mapper.join(row.getCurrentSessionId(),RequestScope.requireUserId());return row;
    }
    /** 与关闭、停用和轮换共用桌台行锁，保证验证与下单原子性。 */
    @Override public DiningTable validate(Long tableId,String session,String token){
        Long user=RequestScope.requireUserId();DiningTable row=lock(tableId);
        if(session==null || token==null || !session.equals(row.getCurrentSessionId()) || !token.equals(row.getQrToken())
                || !Integer.valueOf(1).equals(row.getEnabled()) || mapper.member(session,tenant(),store(),user)!=1)throw invalid();
        return row;
    }
    /** 活跃订单处理完毕后结束桌次，下一批顾客重新加入新桌次。 */
    @Override @Transactional public void close(Long id){
        RequestScope.requireEmployeeTenantId();DiningTable row=lock(id);String session=row.getCurrentSessionId();
        if(session==null)return;
        if(mapper.unfinished(session,tenant(),store())!=0)throw new OrderBusinessException("请先完成或取消本桌次的全部订单");
        mapper.close(session,tenant(),store());row.setCurrentSessionId(null);mapper.update(row);
    }
}
