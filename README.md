# 商品秒杀系统

基于 Spring Boot 的商品秒杀后端系统，使用 Redis 处理库存扣减，MySQL 记录订单，并通过 Lua 脚本解决超卖问题。

## 技术栈

- Spring Boot 2.7.14
- Spring Data JPA
- MySQL 8.0
- Redis
- Jedis

## 核心特性

1. **Redis 库存管理**：使用 Redis 存储秒杀库存，提高并发性能
2. **原子性操作**：使用 Lua 脚本保证库存扣减的原子性，解决超卖问题
3. **MySQL 订单存储**：订单信息持久化到 MySQL 数据库
4. **事务保证**：使用 Spring 事务确保数据一致性

## 项目结构

```
src/main/java/com/seckill/
├── SeckillApplication.java          # 主应用类
├── config/
│   └── RedisConfig.java             # Redis 配置
├── controller/
│   ├── SeckillController.java       # 秒杀接口
│   └── ProductController.java       # 商品接口
├── entity/
│   ├── Product.java                 # 商品实体
│   └── Order.java                   # 订单实体
├── repository/
│   ├── ProductRepository.java       # 商品仓储
│   └── OrderRepository.java         # 订单仓储
└── service/
    └── SeckillService.java          # 秒杀服务（核心逻辑）
```

## 配置说明

### 1. 数据库配置

修改 `application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seckill_db
    username: root
    password: root
```

### 2. Redis 配置

修改 `application.yml` 中的 Redis 连接信息：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
```

## 使用说明

### 1. 创建数据库

```sql
CREATE DATABASE seckill_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 启动项目

```bash
mvn spring-boot:run
```

### 3. API 接口

#### 创建商品
```bash
POST http://localhost:8080/api/product
Content-Type: application/json

{
  "name": "iPhone 15",
  "price": 5999.00,
  "stock": 1000,
  "seckillStock": 100,
  "startTime": "2024-01-01T10:00:00",
  "endTime": "2024-01-01T12:00:00"
}
```

#### 初始化商品库存到 Redis
```bash
POST http://localhost:8080/api/seckill/init/{productId}
```

#### 秒杀下单
```bash
POST http://localhost:8080/api/seckill/order
Content-Type: application/json

{
  "productId": 1,
  "userId": 1001,
  "quantity": 1
}
```

#### 查询库存
```bash
GET http://localhost:8080/api/seckill/stock/{productId}
```

## 超卖问题解决方案

### 核心机制：Lua 脚本原子操作

使用 Redis Lua 脚本保证库存扣减的原子性：

```lua
local stock = tonumber(redis.call('get', KEYS[1]) or 0)
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    redis.call('decrby', KEYS[1], quantity)
    redis.call('incrby', KEYS[2], quantity)
    return 1
else
    return 0
end
```

### 优势

1. **原子性**：Lua 脚本在 Redis 中原子执行，不会被打断
2. **高性能**：Redis 内存操作，响应速度快
3. **并发安全**：多个请求同时执行时，Redis 保证脚本串行执行
4. **数据一致性**：库存扣减和已售数量更新在同一脚本中完成

## 注意事项

1. 秒杀开始前需要调用初始化接口，将商品库存加载到 Redis
2. Redis 中的库存是秒杀库存（seckillStock），不是总库存（stock）
3. 订单创建后状态为 PENDING（待支付），需要后续支付流程
4. 建议在生产环境中使用 Redis 集群和 MySQL 主从复制提高可用性

