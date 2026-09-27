package com.jyfc.backend.core.config;

import com.jyfc.backend.module.account.entity.TenantQuotaEntity;
import com.jyfc.backend.module.account.repository.TenantQuotaRepository;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 把指定账号的套餐写成付费档，法律域（签署、模板、证据、比对、模拟法庭）不再被免费套餐拦住。
 */
@Component
@Order(8)
public class FullAccessBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FullAccessBootstrap.class);
    private static final int SIGN_QUOTA = 11000;
    private static final long AI_QUOTA = 100_000_000L;

    private final Environment environment;
    private final UserRepository users;
    private final TenantQuotaRepository quotas;

    public FullAccessBootstrap(Environment environment, UserRepository users, TenantQuotaRepository quotas) {
        this.environment = environment;
        this.users = users;
        this.quotas = quotas;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"true".equalsIgnoreCase(environment.getProperty("USER_FULL_ACCESS_ENABLED", "false"))) {
            return;
        }
        String username = environment.getProperty("USER_FULL_ACCESS_USERNAME", "xuyi");
        UserEntity user = users.findByUsername(username).orElse(null);
        if (user == null || user.getTenantId() == null || user.getTenantId() == 0L) {
            log.warn("账号 {} 还没有租户，跳过套餐开通", username);
            return;
        }
        TenantQuotaEntity quota = quotas.findByTenantId(user.getTenantId()).orElseGet(TenantQuotaEntity::new);
        quota.setTenantId(user.getTenantId());
        quota.setPlan("PRO");
        quota.setSignQuota(SIGN_QUOTA);
        quota.setAiQuotaTokens(AI_QUOTA);
        quotas.save(quota);
        log.info("账号 {} 已开通全部权限（套餐 PRO）", username);
    }
}
