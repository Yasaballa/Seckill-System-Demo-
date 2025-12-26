package com.seckill.service;

import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.repository.OrderRepository;
import com.seckill.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class SeckillService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Redis 库存 key 前缀
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    
    // Redis 已售数量 key 前缀
    private static final String SOLD_KEY_PREFIX = "seckill:sold:";

    /**
     * 初始化商品库存到 Redis
     */
    public void initStock(Long productId, Integer stock) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String soldKey = SOLD_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, stock);
        redisTemplate.opsForValue().set(soldKey, 0);
        log.info("初始化商品 {} 库存到 Redis，库存数量：{}", productId, stock);
    }

    /**
     * 秒杀下单 - 使用 Lua 脚本保证原子性，解决超卖问题
     */
    @Transactional(rollbackFor = Exception.class)
    public Order seckill(Long productId, Long userId, Integer quantity) {
        // 检查商品是否存在
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        // 检查秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getStartTime()) || now.isAfter(product.getEndTime())) {
            throw new RuntimeException("不在秒杀时间内");
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        String soldKey = SOLD_KEY_PREFIX + productId;

        // 使用 Lua 脚本原子性地扣减库存
        // Lua 脚本保证操作的原子性，解决超卖问题
        String luaScript = 
            "local stock = tonumber(redis.call('get', KEYS[1]) or 0)\n" +
            "local quantity = tonumber(ARGV[1])\n" +
            "if stock >= quantity then\n" +
            "    redis.call('decrby', KEYS[1], quantity)\n" +
            "    redis.call('incrby', KEYS[2], quantity)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, 
            java.util.Arrays.asList(stockKey, soldKey), 
            quantity.toString());

        if (result == null || result == 0) {
            throw new RuntimeException("库存不足");
        }

        // 创建订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setPrice(product.getPrice());
        order.setQuantity(quantity);
        order.setTotalAmount(product.getPrice().multiply(new BigDecimal(quantity)));
        order.setStatus(Order.OrderStatus.PENDING);

        order = orderRepository.save(order);
        log.info("用户 {} 秒杀商品 {} 成功，订单号：{}", userId, productId, order.getOrderNo());

        return order;
    }

    /**
     * 获取 Redis 中的库存
     */
    public Integer getStock(Long productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        Object stock = redisTemplate.opsForValue().get(stockKey);
        return stock == null ? 0 : Integer.parseInt(stock.toString());
    }

    /**
     * 获取 Redis 中的已售数量
     */
    public Integer getSold(Long productId) {
        String soldKey = SOLD_KEY_PREFIX + productId;
        Object sold = redisTemplate.opsForValue().get(soldKey);
        return sold == null ? 0 : Integer.parseInt(sold.toString());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "SK" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

