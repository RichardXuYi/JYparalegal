package com.jyfc.backend.module.trade.service;

import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.entity.Sku;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.SkuRepository;
import com.jyfc.backend.module.trade.entity.OrderEntity;
import com.jyfc.backend.module.trade.entity.OrderItem;
import com.jyfc.backend.module.trade.repository.OrderItemRepository;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        ProductRepository productRepository, SkuRepository skuRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
    }

    public Page<OrderEntity> listOrders(int page, int size, Integer status) {
        return listOrders(page, size, status, null);
    }

    public Page<OrderEntity> listOrders(int page, int size, Integer status, Long userId) {
        Pageable pageable = PageRequest.of(page - 1, Math.max(1, Math.min(size, 100)),
                Sort.by("createdAt").descending());
        if (userId != null && status != null) {
            return orderRepository.findByUserIdAndStatus(userId, status, pageable);
        }
        if (userId != null) {
            return orderRepository.findByUserId(userId, pageable);
        }
        if (status != null) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public OrderEntity createOrder(Long userId, Map<String, Object> body) {
        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setStatus(0); // Pending payment

        // Parse items
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        if (items != null && !items.isEmpty()) {
            for (Map<String, Object> item : items) {
                Long productId = item.get("productId") != null
                        ? Long.valueOf(String.valueOf(item.get("productId"))) : null;
                Long skuId = item.get("skuId") != null
                        ? Long.valueOf(String.valueOf(item.get("skuId"))) : null;
                Integer quantity = item.get("quantity") != null
                        ? Integer.valueOf(String.valueOf(item.get("quantity"))) : 1;

                // F1-3: 数量合法性校验（防御负数/0/超大值）
                if (quantity == null || quantity <= 0) {
                    throw new IllegalArgumentException("数量必须大于0");
                }
                if (quantity > 9999) {
                    throw new IllegalArgumentException("数量超过单笔上限9999");
                }

                if (productId == null) continue;

                OrderItem oi = buildOrderItem(productId, skuId, quantity);
                totalAmount = totalAmount.add(oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
                orderItems.add(oi);
            }
        } else {
            // Fallback: single item from request body fields
            Long productId = body.get("productId") != null
                    ? Long.valueOf(String.valueOf(body.get("productId"))) : null;
            Long skuId = body.get("skuId") != null
                    ? Long.valueOf(String.valueOf(body.get("skuId"))) : null;
            Integer quantity = body.get("quantity") != null
                    ? Integer.valueOf(String.valueOf(body.get("quantity"))) : 1;

            // F1-3: 数量合法性校验（防御负数/0/超大值）
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("数量必须大于0");
            }
            if (quantity > 9999) {
                throw new IllegalArgumentException("数量超过单笔上限9999");
            }

            if (productId != null) {
                OrderItem oi = buildOrderItem(productId, skuId, quantity);
                totalAmount = totalAmount.add(oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
                orderItems.add(oi);
            }
        }

        // F1-1: 删除客户端可控金额 fallback — 金额只能由 items 计算得出
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("订单金额不能为0，请检查商品项");
        }

        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);

        // Payment method
        if (body.containsKey("paymentMethod")) {
            order.setPaymentMethod((String) body.get("paymentMethod"));
        }

        // Shipping method
        if (body.containsKey("shippingMethod")) {
            order.setShippingMethod((String) body.get("shippingMethod"));
        }

        // Receiver info (address snapshot)
        String receiverInfoJson = buildReceiverInfo(body);
        order.setReceiverInfo(receiverInfoJson);

        order.setRemark((String) body.get("remark"));

        order = orderRepository.save(order);

        // Save order items with orderId
        Long finalOrderId = order.getId();
        for (OrderItem item : orderItems) {
            item.setOrderId(finalOrderId);
        }
        orderItemRepository.saveAll(orderItems);

        return order;
    }

    private OrderItem buildOrderItem(Long productId, Long skuId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        BigDecimal unitPrice;
        String skuSpecs = null;

        if (skuId != null) {
            // H12/H5：skuId 显式提供时必须存在且属于本商品。此前"SKU 不存在→静默回退
            // 商品价且不扣库存"可被用来无限超卖，"不校验 sku.productId"可用廉价 SKU
            // 配高价商品名绕过定价。
            Sku sku = skuRepository.findById(skuId)
                    .orElseThrow(() -> new RuntimeException("SKU不存在: " + skuId));
            if (!productId.equals(sku.getProductId())) {
                throw new RuntimeException("SKU与商品不匹配: skuId=" + skuId + ", productId=" + productId);
            }
            // F1-2: 原子扣减库存（条件 WHERE stock >= qty 防止超卖）
            int updated = skuRepository.deductStockAtomic(skuId, quantity);
            if (updated == 0) {
                throw new RuntimeException("SKU库存不足: " + sku.getSpecs() + ", 需要: " + quantity);
            }
            unitPrice = sku.getPrice();
            skuSpecs = sku.getSpecs();
        } else {
            unitPrice = product.getPrice();
        }

        OrderItem oi = new OrderItem();
        oi.setProductId(productId);
        oi.setSkuId(skuId);
        oi.setProductName(product.getName());
        oi.setSkuSpecs(skuSpecs);
        oi.setPrice(unitPrice);
        oi.setQuantity(quantity);
        return oi;
    }

    public Optional<OrderEntity> findById(Long id) {
        if (id == null) return Optional.empty();
        return orderRepository.findById(id);
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        if (orderId == null) return List.of();
        return orderItemRepository.findByOrderId(orderId);
    }

    @Transactional
    public void restoreStockForOrder(Long orderId) {
        List<OrderItem> items = getOrderItems(orderId);
        for (OrderItem item : items) {
            if (item.getSkuId() != null && item.getQuantity() != null) {
                skuRepository.restoreStockAtomic(item.getSkuId(), item.getQuantity());
            }
        }
    }

    private String buildReceiverInfo(Map<String, Object> body) {
        Map<String, Object> info = new LinkedHashMap<>();
        String[] keys = {"receiverName", "receiverPhone", "receiverAddress", "province", "city", "district", "detailAddress"};

        // Try nested address object first
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) body.get("address");
        if (address != null) {
            for (String key : keys) {
                if (address.get(key) != null) {
                    info.put(key, String.valueOf(address.get(key)));
                }
            }
        } else {
            for (String key : keys) {
                if (body.get(key) != null) {
                    info.put(key, String.valueOf(body.get(key)));
                }
            }
        }

        try {
            return new ObjectMapper().writeValueAsString(info);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generateOrderNo() {
        return "ORD" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }
}
