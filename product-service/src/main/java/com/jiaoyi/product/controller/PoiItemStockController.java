package com.jiaoyi.product.controller;

import com.jiaoyi.product.dto.*;
import com.jiaoyi.product.entity.OversellRecord;
import com.jiaoyi.product.entity.PoiItemStock;
import com.jiaoyi.product.entity.PoiItemStockLog;
import com.jiaoyi.product.service.PoiItemStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品中心库存管理Controller
 * 
 * API列表：
 * POST /api/v1/poi/stock/sync-from-pos          - POS在线库存同步
 * POST /api/v1/poi/stock/update                  - 商品中心设置库存
 * POST /api/v1/poi/stock/detail                  - 查询库存详情
 * POST /api/v1/poi/stock/log                     - 查询库存变动记录
 * POST /api/v1/poi/stock/replay-offline           - POS离线事件回放
 * POST /api/v1/poi/stock/deduct-by-channel        - 渠道级别库存扣减
 * POST /api/v1/poi/stock/allocate-channel-quotas  - 按权重分配渠道额度
 * GET  /api/v1/poi/stock/oversell-records         - 查询超卖记录
 * POST /api/v1/poi/stock/resolve-oversell         - 处理超卖记录
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/poi/stock")
public class PoiItemStockController {
    
    @Autowired
    private PoiItemStockService stockService;
    
    /**
     * 接收POS上报库存变更
     */
    @PostMapping("/sync-from-pos")
    public ResponseEntity<?> syncFromPos(@RequestBody StockSyncFromPosRequest request) {
        try {
            stockService.syncFromPos(request);
            return ResponseEntity.ok(Map.of("success", true, "message", "同步成功"));
        } catch (Exception e) {
            log.error("POS库存同步失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 设置库存（商品中心）
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateStock(@RequestBody StockSyncFromPosRequest request) {
        try {
            stockService.updateStock(request);
            return ResponseEntity.ok(Map.of("success", true, "message", "设置成功"));
        } catch (Exception e) {
            log.error("设置库存失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 查询库存详情
     */
    @PostMapping("/detail")
    public ResponseEntity<?> getStockDetail(
        @RequestParam String brandId,
        @RequestParam String poiId,
        @RequestParam Long objectId) {
        try {
            PoiItemStock stock = stockService.getStockDetail(brandId, poiId, objectId);
            if (stock == null) {
                return ResponseEntity.ok(Map.of("success", false, "message", "库存不存在"));
            }
            return ResponseEntity.ok(Map.of("success", true, "data", stock));
        } catch (Exception e) {
            log.error("查询库存详情失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 查询库存变动记录
     */
    @PostMapping("/log")
    public ResponseEntity<?> getStockLogs(
        @RequestParam(required = false) String brandId,
        @RequestParam(required = false) String poiId,
        @RequestParam(required = false) Long stockId,
        @RequestParam(defaultValue = "100") Integer limit) {
        try {
            List<PoiItemStockLog> logs = stockService.getStockLogs(brandId, poiId, stockId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", logs));
        } catch (Exception e) {
            log.error("查询库存变动记录失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    // ========================= POS 离线事件回放 =========================
    
    /**
     * POS离线事件批量回放
     * 
     * POS恢复网络后，将离线期间的库存变更事件（订单扣减、手动调整等）按时间顺序上报。
     * 云端逐条回放，基于orderId幂等，回放后检测超卖并记录。
     */
    @PostMapping("/replay-offline")
    public ResponseEntity<?> replayOfflineEvents(@RequestBody PosOfflineReplayRequest request) {
        try {
            PosOfflineReplayResult result = stockService.replayOfflineEvents(request);
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "data", result,
                "message", result.isOversellDetected() 
                    ? "回放完成，检测到超卖（" + result.getOversellQuantity() + "份），请店长确认" 
                    : "回放完成"
            ));
        } catch (Exception e) {
            log.error("POS离线事件回放失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    // ========================= 渠道库存隔离扣减 =========================
    
    /**
     * 渠道级别库存扣减
     * 
     * 扣减逻辑：先扣渠道专属额度 → 不够则从共享池借 → 共享池也不够则售罄
     */
    @PostMapping("/deduct-by-channel")
    public ResponseEntity<?> deductByChannel(@RequestBody ChannelDeductRequest request) {
        try {
            ChannelDeductResult result = stockService.deductByChannel(request);
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "data", result,
                "message", result.getMessage()
            ));
        } catch (Exception e) {
            log.error("渠道库存扣减失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    // ========================= 共享池加权分配 =========================
    
    /**
     * 按权重重新分配渠道额度
     * 
     * 将库存按各渠道权重分配，余数放入共享池。
     * 适用场景：库存初始化、手动调整后重新分配。
     */
    @PostMapping("/allocate-channel-quotas")
    public ResponseEntity<?> allocateChannelQuotas(
        @RequestParam String brandId,
        @RequestParam String poiId,
        @RequestParam Long objectId) {
        try {
            stockService.allocateChannelQuotas(brandId, poiId, objectId);
            return ResponseEntity.ok(Map.of("success", true, "message", "渠道额度分配成功"));
        } catch (Exception e) {
            log.error("渠道额度分配失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    // ========================= 超卖检测和补偿 =========================
    
    /**
     * 查询超卖记录
     */
    @GetMapping("/oversell-records")
    public ResponseEntity<?> getOversellRecords(
        @RequestParam String brandId,
        @RequestParam String poiId,
        @RequestParam(required = false) String status) {
        try {
            List<OversellRecord> records = stockService.getOversellRecords(brandId, poiId, status);
            return ResponseEntity.ok(Map.of("success", true, "data", records));
        } catch (Exception e) {
            log.error("查询超卖记录失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 处理超卖记录（店长确认）
     * 
     * status: CONFIRMED-已确认可做, REFUND-需退款
     */
    @PostMapping("/resolve-oversell")
    public ResponseEntity<?> resolveOversell(
        @RequestParam Long recordId,
        @RequestParam String status,
        @RequestParam(required = false) String resolvedBy,
        @RequestParam(required = false) String remark) {
        try {
            stockService.resolveOversellRecord(recordId, status, resolvedBy, remark);
            return ResponseEntity.ok(Map.of("success", true, "message", "超卖记录已处理"));
        } catch (Exception e) {
            log.error("处理超卖记录失败", e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", e.getMessage()));
        }
    }
}
