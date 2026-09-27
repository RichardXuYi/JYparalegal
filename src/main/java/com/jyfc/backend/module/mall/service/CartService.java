package com.jyfc.backend.module.mall.service;

import com.jyfc.backend.module.mall.entity.CartItem;
import com.jyfc.backend.module.mall.repository.CartRepository;
import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.entity.Sku;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.SkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       SkuRepository skuRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
    }

    /**
     * 加购：相同 product+sku 累加数量
     */
    @Transactional
    public CartItem addToCart(Long userId, Long productId, Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
        if (quantity > 999) {
            throw new IllegalArgumentException("单次加购数量不能超过 999");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("商品不存在"));
        // 校验 SKU（如果指定）
        if (skuId != null) {
            Sku sku = skuRepository.findById(skuId)
                    .orElseThrow(() -> new IllegalStateException("SKU 不存在"));
            if (!sku.getProductId().equals(productId)) {
                throw new IllegalStateException("SKU 与商品不匹配");
            }
            if (sku.getStock() != null && sku.getStock() < quantity) {
                throw new IllegalStateException("库存不足");
            }
        } else if (product.getStock() != null && product.getStock() < quantity) {
            throw new IllegalStateException("库存不足");
        }

        Optional<CartItem> existing = cartRepository.findByUserIdAndProductIdAndSkuId(userId, productId, skuId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = (item.getQuantity() == null ? 0 : item.getQuantity()) + quantity;
            if (newQty > 999) {
                throw new IllegalArgumentException("购物车数量不能超过 999");
            }
            item.setQuantity(newQty);
            return cartRepository.save(item);
        }

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setSelected(true);
        return cartRepository.save(item);
    }

    /**
     * 更新数量
     */
    @Transactional
    public CartItem updateQuantity(Long userId, Long cartId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
        CartItem item = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalStateException("购物车项不存在"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalStateException("无权操作");
        }
        item.setQuantity(quantity);
        return cartRepository.save(item);
    }

    /**
     * 切换选中
     */
    @Transactional
    public CartItem toggleSelected(Long userId, Long cartId, Boolean selected) {
        CartItem item = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalStateException("购物车项不存在"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalStateException("无权操作");
        }
        item.setSelected(selected != null ? selected : !Boolean.TRUE.equals(item.getSelected()));
        return cartRepository.save(item);
    }

    /**
     * 取消加购（单条）
     */
    @Transactional
    public void remove(Long userId, Long cartId) {
        CartItem item = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalStateException("购物车项不存在"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalStateException("无权操作");
        }
        cartRepository.delete(item);
    }

    /**
     * 清空购物车
     */
    @Transactional
    public long clear(Long userId) {
        List<CartItem> items = cartRepository.findByUserIdOrderByAddedAtDesc(userId);
        long count = items.size();
        cartRepository.deleteAll(items);
        return count;
    }

    public List<CartItem> list(Long userId) {
        return cartRepository.findByUserIdOrderByAddedAtDesc(userId);
    }

    public long count(Long userId) {
        return cartRepository.countByUserId(userId);
    }
}