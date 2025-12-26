package com.seckill.controller;

import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.repository.ProductRepository;
import com.seckill.service.SeckillService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private ProductRepository productRepository;

    /**
     * 初始化商品库存到 Redis
     */
    @PostMapping("/init/{productId}")
    public ResponseEntity<Map<String, Object>> initStock(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        
        seckillService.initStock(productId, product.getSeckillStock());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "库存初始化成功");
        result.put("productId", productId);
        result.put("stock", product.getSeckillStock());
        return ResponseEntity.ok(result);
    }

    /**
     * 秒杀下单
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> seckill(@RequestBody SeckillRequest request) {
        try {
            Order order = seckillService.seckill(
                request.getProductId(), 
                request.getUserId(), 
                request.getQuantity()
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "秒杀成功");
            result.put("order", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 查询商品库存
     */
    @GetMapping("/stock/{productId}")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable Long productId) {
        Integer stock = seckillService.getStock(productId);
        Integer sold = seckillService.getSold(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("productId", productId);
        result.put("stock", stock);
        result.put("sold", sold);
        return ResponseEntity.ok(result);
    }

    @Data
    static class SeckillRequest {
        private Long productId;
        private Long userId;
        private Integer quantity;
    }
}

