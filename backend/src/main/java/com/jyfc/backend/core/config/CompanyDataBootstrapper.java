package com.jyfc.backend.core.config;

import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 企业数据初始化器
 * 创建公司并关联管理员和员工账户（幂等设计）
 */
@Component
@Order(5)
public class CompanyDataBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataBootstrapper.class);

    private final Environment environment;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public CompanyDataBootstrapper(Environment environment,
                                   UserRepository userRepository,
                                   CompanyRepository companyRepository,
                                   PasswordEncoder passwordEncoder) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String enabled = environment.getProperty("COMPANY_BOOTSTRAP_ENABLED", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        String ownerUsername = environment.getProperty("USER_BOOTSTRAP_USERNAME", "xuyi");
        String companyName = environment.getProperty("COMPANY_BOOTSTRAP_NAME", "grandpoem");
        String employeeUsername = environment.getProperty("COMPANY_BOOTSTRAP_EMPLOYEE", "diandian");
        String employeePassword = environment.getProperty("COMPANY_BOOTSTRAP_EMPLOYEE_PASSWORD", "123456");

        // 1. 确保企业主用户存在
        UserEntity owner = userRepository.findByUsername(ownerUsername).orElse(null);
        if (owner == null) {
            log.warn("企业主用户 '{}' 不存在，跳过企业初始化", ownerUsername);
            return;
        }

        // 2. 查找或创建公司
        Long ownerId = owner.getId();
        if (ownerId == null) {
            log.warn("Owner user ID is null, skipping company bootstrap");
            return;
        }
        CompanyEntity company = companyRepository.findByOwnerUserId(ownerId).orElseGet(() -> {
            CompanyEntity c = new CompanyEntity();
            c.setName(companyName);
            c.setOwnerUserId(ownerId);
            c.setStatus(1);
            CompanyEntity saved = companyRepository.save(c);
            log.info("创建公司: {} (id={})", companyName, saved.getId());
            return saved;
        });

        // 3. 更新企业主关联
        Long companyId = company.getId();
        if (companyId == null) {
            log.warn("Company ID is null, skipping enterprise user association");
            return;
        }
        ensureEnterpriseUser(owner, companyId);

        // 4. 查找或创建员工
        UserEntity employee = userRepository.findByUsername(employeeUsername).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setUsername(employeeUsername);
            u.setEmail(employeeUsername + "@example.com");
            u.setPassword(passwordEncoder.encode(employeePassword));
            u.setStatus(1);
            u.setEmailVerified(true);
            log.info("创建员工用户: {}", employeeUsername);
            return userRepository.save(u);
        });

        ensureEnterpriseUser(employee, companyId);

        log.info("企业数据初始化完成: 公司={}, 管理员={}, 员工={}", companyName, ownerUsername, employeeUsername);
    }

    private void ensureEnterpriseUser(UserEntity user, Long companyId) {
        boolean changed = false;
        if (!"ENTERPRISE".equals(user.getUserType())) {
            user.setUserType("ENTERPRISE");
            changed = true;
        }
        if (user.getCompanyId() == null || !user.getCompanyId().equals(companyId)) {
            user.setCompanyId(companyId);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            log.info("更新用户 {} 为企业用户, companyId={}", user.getUsername(), companyId);
        }
    }
}
