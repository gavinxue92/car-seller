package com.carseller.inventory.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.result.PageResult;
import com.carseller.common.result.ResultCode;
import com.carseller.inventory.constant.InventoryConstants;
import com.carseller.inventory.dto.InventoryDeductItem;
import com.carseller.inventory.dto.InventoryDeductRequest;
import com.carseller.inventory.dto.InventoryLogQuery;
import com.carseller.inventory.entity.Inventory;
import com.carseller.inventory.entity.InventoryDeductRecord;
import com.carseller.inventory.entity.InventoryLog;
import com.carseller.inventory.mapper.InventoryDeductRecordMapper;
import com.carseller.inventory.mapper.InventoryLogMapper;
import com.carseller.inventory.mapper.InventoryMapper;
import com.carseller.inventory.service.InventoryService;
import com.carseller.inventory.vo.InventoryChangeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 库存服务实现。
 *
 * <p>扣减/回补在单个本地事务内完成「条件更新库存 + 变动日志 + 扣减记录」；
 * 幂等双保险：Redis SETNX + 唯一键 {@code uk_order_product} / 状态条件更新。</p>
 *
 * @author car-seller
 */
@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final InventoryDeductRecordMapper deductRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public InventoryServiceImpl(InventoryMapper inventoryMapper,
                                InventoryLogMapper inventoryLogMapper,
                                InventoryDeductRecordMapper deductRecordMapper,
                                StringRedisTemplate stringRedisTemplate) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
        this.deductRecordMapper = deductRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryChangeResult deduct(InventoryDeductRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "扣减请求不能为空");
        }
        Long orderId = request.getOrderId();
        List<InventoryDeductItem> items = normalizeItems(request.getItems());
        String idempotentKey = InventoryConstants.DEDUCT_IDEMPOTENT_KEY_PREFIX + orderId;
        // 第一道幂等：Redis SETNX
        acquireIdempotentKey(idempotentKey, "订单[" + orderId + "]已扣减库存，忽略重复扣减请求");
        try {
            List<InventoryChangeResult.Item> details = new ArrayList<>(items.size());
            for (InventoryDeductItem item : items) {
                details.add(deductSingle(orderId, item));
            }
            log.info("库存扣减成功 orderId={} details={}", orderId, details);
            return InventoryChangeResult.changed(orderId, "库存扣减成功", details);
        } catch (RuntimeException ex) {
            // 事务回滚后释放幂等键，允许消息重投或用户重试再次扣减
            releaseIdempotentKey(idempotentKey);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryChangeResult restore(Long orderId) {
        if (orderId == null || orderId <= 0L) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单号不能为空");
        }
        String idempotentKey = InventoryConstants.RESTORE_IDEMPOTENT_KEY_PREFIX + orderId;
        // 第一道幂等：Redis SETNX
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, InventoryConstants.IDEMPOTENT_FLAG, InventoryConstants.IDEMPOTENT_TTL))) {
            log.info("库存恢复命中幂等键（Redis SETNX） orderId={}", orderId);
            return InventoryChangeResult.skipped(orderId, "订单[" + orderId + "]库存已恢复，忽略重复取消事件");
        }
        try {
            List<InventoryDeductRecord> records = deductRecordMapper.selectList(
                    Wrappers.<InventoryDeductRecord>lambdaQuery()
                            .eq(InventoryDeductRecord::getOrderId, orderId)
                            .eq(InventoryDeductRecord::getStatus, InventoryConstants.RECORD_STATUS_DEDUCTED)
                            .orderByAsc(InventoryDeductRecord::getProductId));
            if (records.isEmpty()) {
                // 无待回补记录（如扣减失败后关单）：释放幂等键，避免后续补投消息被误判为重复
                releaseIdempotentKey(idempotentKey);
                log.info("订单无待恢复的扣减记录 orderId={}", orderId);
                return InventoryChangeResult.skipped(orderId, "订单[" + orderId + "]无待恢复的扣减记录");
            }
            List<InventoryChangeResult.Item> details = new ArrayList<>(records.size());
            for (InventoryDeductRecord record : records) {
                InventoryChangeResult.Item item = restoreSingle(orderId, record);
                if (item != null) {
                    details.add(item);
                }
            }
            log.info("库存恢复成功 orderId={} details={}", orderId, details);
            return InventoryChangeResult.changed(orderId, "库存恢复成功", details);
        } catch (RuntimeException ex) {
            releaseIdempotentKey(idempotentKey);
            throw ex;
        }
    }

    @Override
    public PageResult<InventoryLog> pageLogs(InventoryLogQuery query) {
        InventoryLogQuery condition = query == null ? new InventoryLogQuery() : query;
        long pageNum = condition.getPageNum() > 0 ? condition.getPageNum() : InventoryConstants.DEFAULT_PAGE_NUM;
        long pageSize = condition.getPageSize() > 0 ? condition.getPageSize() : InventoryConstants.DEFAULT_PAGE_SIZE;
        pageSize = Math.min(pageSize, InventoryConstants.MAX_PAGE_SIZE);
        Page<InventoryLog> page = inventoryLogMapper.selectPage(Page.of(pageNum, pageSize),
                Wrappers.<InventoryLog>lambdaQuery()
                        .eq(condition.getProductId() != null, InventoryLog::getProductId, condition.getProductId())
                        .orderByDesc(InventoryLog::getId));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public Map<Long, Integer> currentStock(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> stock = new HashMap<>();
        inventoryMapper.selectList(Wrappers.<Inventory>lambdaQuery().in(Inventory::getProductId, productIds))
                .forEach(inventory -> stock.put(inventory.getProductId(), inventory.getQuantity()));
        return stock;
    }

    private InventoryChangeResult.Item deductSingle(Long orderId, InventoryDeductItem item) {
        Long productId = item.getProductId();
        Integer quantity = item.getQuantity();
        Integer before = inventoryMapper.selectQuantityForUpdate(productId);
        if (before == null || before < quantity) {
            throw new BusinessException(ResultCode.INVENTORY_NOT_ENOUGH,
                    "商品[" + productId + "]库存不足，可用 " + (before == null ? 0 : before) + "，需要 " + quantity);
        }
        insertDeductRecord(orderId, productId, quantity);
        if (inventoryMapper.deductQuantity(productId, quantity) == 0) {
            throw new BusinessException(ResultCode.INVENTORY_NOT_ENOUGH, "商品[" + productId + "]库存不足");
        }
        saveLog(productId, -quantity, InventoryConstants.CHANGE_TYPE_DECREASE, "订单扣减库存 orderId=" + orderId);
        return new InventoryChangeResult.Item(productId, quantity, before, before - quantity);
    }

    private InventoryChangeResult.Item restoreSingle(Long orderId, InventoryDeductRecord record) {
        if (deductRecordMapper.markRestored(record.getId()) == 0) {
            log.info("扣减记录已回补，幂等跳过 recordId={} orderId={}", record.getId(), orderId);
            return null;
        }
        Integer before = inventoryMapper.selectQuantityForUpdate(record.getProductId());
        if (before == null) {
            throw new BusinessException(ResultCode.FAILURE, "商品[" + record.getProductId() + "]库存记录不存在，无法回补");
        }
        if (inventoryMapper.restoreQuantity(record.getProductId(), record.getQuantity()) == 0) {
            throw new BusinessException(ResultCode.FAILURE, "商品[" + record.getProductId() + "]库存回补失败");
        }
        saveLog(record.getProductId(), record.getQuantity(), InventoryConstants.CHANGE_TYPE_INCREASE,
                "订单取消回补库存 orderId=" + orderId);
        return new InventoryChangeResult.Item(record.getProductId(), record.getQuantity(), before,
                before + record.getQuantity());
    }

    private void insertDeductRecord(Long orderId, Long productId, Integer quantity) {
        InventoryDeductRecord record = new InventoryDeductRecord();
        record.setOrderId(orderId);
        record.setProductId(productId);
        record.setQuantity(quantity);
        record.setStatus(InventoryConstants.RECORD_STATUS_DEDUCTED);
        try {
            deductRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.INVENTORY_DUPLICATE_DEDUCT,
                    "订单[" + orderId + "]商品[" + productId + "]已扣减库存");
        }
    }

    private void saveLog(Long productId, Integer changeQuantity, String changeType, String remark) {
        InventoryLog inventoryLog = new InventoryLog();
        inventoryLog.setProductId(productId);
        inventoryLog.setChangeQuantity(changeQuantity);
        inventoryLog.setChangeType(changeType);
        inventoryLog.setRemark(remark);
        inventoryLogMapper.insert(inventoryLog);
    }

    // 合并同商品明细并按商品 ID 升序处理（固定加锁顺序，降低死锁概率）
    private List<InventoryDeductItem> normalizeItems(List<InventoryDeductItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "扣减明细不能为空");
        }
        Map<Long, Integer> merged = new TreeMap<>();
        for (InventoryDeductItem item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "扣减明细非法：商品ID与数量必填，数量需大于 0");
            }
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        List<InventoryDeductItem> normalized = new ArrayList<>(merged.size());
        merged.forEach((productId, quantity) -> normalized.add(new InventoryDeductItem(productId, quantity)));
        return normalized;
    }

    private void acquireIdempotentKey(String key, String duplicatedMessage) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key,
                InventoryConstants.IDEMPOTENT_FLAG, InventoryConstants.IDEMPOTENT_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ResultCode.INVENTORY_DUPLICATE_DEDUCT, duplicatedMessage);
        }
    }

    // 释放幂等键：失败仅记录日志
    private void releaseIdempotentKey(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.error("释放库存幂等键失败 key={}", key, ex);
        }
    }
}

