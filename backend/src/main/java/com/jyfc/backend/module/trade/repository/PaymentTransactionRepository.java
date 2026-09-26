package com.jyfc.backend.module.trade.repository;

import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByOrderId(Long orderId);

    Optional<PaymentTransaction> findByTransactionNo(String transactionNo);

    /**
     * BE-004: 支付回调幂等处理专用。对流水行加悲观锁（SELECT ... FOR UPDATE），
     * 配合 transaction_no 唯一约束（V191），保证并发重复回调下仅推进一次。
     * 必须在事务（@Transactional）内调用，锁随事务提交/回滚释放。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.transactionNo = :transactionNo")
    Optional<PaymentTransaction> findByTransactionNoForUpdate(@Param("transactionNo") String transactionNo);

    Optional<PaymentTransaction> findByChannelTradeNo(String channelTradeNo);

    List<PaymentTransaction> findByUserId(Long userId);
}
