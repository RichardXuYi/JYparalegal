# 数据库迁移整理说明

## 📋 迁移文件优化

### 优化前
```
V1__base_schema.sql                          (91 KB)  基础表结构
V2__add_law_references.sql                   (1.2 KB) 法律引用表
V3__add_counterparties.sql                   (1.3 KB) 相对方表
V4__add_performance_tasks.sql                (1.6 KB) 履约任务表
V5__add_risk_rule_examples.sql               (732 B)  风险规则示例
V6__add_contract_columns.sql                 (421 B)  合同表字段
V7__add_risk_rule_columns.sql                (458 B)  风险规则字段
V8__add_deep_audit_fields.sql                (972 B)  深度审核字段
V9__add_contract_source_id_to_audit_records.sql (343 B) 审核记录字段
V12__pipeline_and_archive_fields.sql         (4.7 KB) 管道和归档字段
V13__init_risk_rules.sql                     (29 KB)  风险规则初始化
V14__init_knowledge_base.sql                 (35 KB)  知识库初始化
V15__workflow_company_dimension.sql          (3.2 KB) 工作流公司维度
V16__rename_workflow_admin_role.sql          (303 B)  角色名称统一
```

### 优化后
```
V1__base_schema.sql                          (91 KB)  基础表结构（不变）
V2__merge_incremental_updates.sql            (3.5 KB) 合并 V2-V11 所有增量更新
V3__pipeline_and_archive_fields.sql          (4.7 KB) 管道和归档字段（原 V12）
V4__init_risk_rules.sql                      (29 KB)  风险规则初始化（原 V13）
V5__init_knowledge_base.sql                  (35 KB)  知识库初始化（原 V14）
V6__workflow_company_dimension.sql           (3.2 KB) 工作流公司维度（原 V15）
V7__rename_workflow_admin_role.sql           (303 B)  角色名称统一（原 V16）
```

## ✅ 优化效果

- **文件数量**：从 15 个减少到 7 个（减少 53%）
- **编号连续性**：从 V1-V16（有间隔）变为 V1-V7（连续）
- **逻辑分组**：相关的小更新合并为一个迁移文件
- **知识库迁移**：V5 保持独立，因为它包含大量业务数据

## 🔧 如何应用

### 方法一：使用自动化脚本（推荐）

```powershell
# 在项目根目录执行
.\scripts\reset-flyway.ps1
```

脚本会自动：
1. 备份当前迁移历史
2. 清空迁移历史表
3. 提示您重启后端

### 方法二：手动操作

```sql
-- 1. 连接数据库
docker exec -it jy-mysql-dev mysql -uroot -proot jy_financial

-- 2. 清空迁移历史
DELETE FROM flyway_schema_history;

-- 3. 退出并重启后端
exit
```

### 重启后端

```powershell
# 停止当前后端
Get-Process -Name "java" | Where-Object {$_.Path -like "*backend*"} | Stop-Process -Force

# 启动后端
cd backend
mvn spring-boot:run
```

## 📝 V2 合并内容详解

V2__merge_incremental_updates.sql 包含：

1. **法律引用表** (原 V2)
   - `law_references` 表创建

2. **相对方表** (原 V3)
   - `counterparties` 表创建
   - 包含索引优化

3. **履约任务表** (原 V4)
   - `performance_tasks` 表创建
   - 包含常用索引

4. **风险规则示例** (原 V5)
   - 4 条示例风险规则数据

5. **合同表字段扩展** (原 V6)
   - `contract_type`
   - `counterparty_id`
   - `amount`
   - `start_date`
   - `end_date`

6. **风险规则字段扩展** (原 V7)
   - `category`
   - `tags`

7. **深度审核字段** (原 V8)
   - `deep_audit_result`
   - `ai_analysis`
   - `confidence_score`

8. **审核记录字段** (原 V9)
   - `contract_source_id`

## ⚠️ 注意事项

1. **数据安全**：脚本会自动备份迁移历史，但不会删除任何业务数据
2. **幂等性**：所有 DDL 语句使用 `IF NOT EXISTS` 或 `ADD COLUMN IF NOT EXISTS`，可重复执行
3. **知识库数据**：V5 迁移不受影响，40 条知识库条目会正常插入
4. **回滚方案**：如有问题，可从备份恢复迁移历史

## 🎯 验证迁移

重启后端后，检查日志：

```
Flyway: Migrating schema `jy_financial` to version 2 - merge incremental updates
Flyway: Migrating schema `jy_financial` to version 3 - pipeline and archive fields
Flyway: Migrating schema `jy_financial` to version 4 - init risk rules
Flyway: Migrating schema `jy_financial` to version 5 - init knowledge base
Flyway: Migrating schema `jy_financial` to version 6 - workflow company dimension
Flyway: Migrating schema `jy_financial` to version 7 - rename workflow admin role
Flyway: Successfully applied 6 migrations
```

## 📊 数据库表结构验证

```sql
-- 检查所有表是否存在
SELECT TABLE_NAME FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'jy_financial' 
ORDER BY TABLE_NAME;

-- 检查迁移历史
SELECT version, description, execution_time 
FROM flyway_schema_history 
ORDER BY installed_rank;

-- 检查知识库数据
SELECT 
    kc.name AS 分类,
    COUNT(ke.id) AS 条目数
FROM knowledge_categories kc
LEFT JOIN knowledge_entries ke ON kc.id = ke.category_id
GROUP BY kc.name;
```

## 🔍 故障排除

### 问题 1：迁移校验和不匹配

```
ERROR: Found more than one migration with version 2
```

**解决方案**：
```sql
-- 清空迁移历史
DELETE FROM flyway_schema_history;
-- 重启后端
```

### 问题 2：表已存在

```
ERROR: Table 'counterparties' already exists
```

**解决方案**：V2 已使用 `CREATE TABLE IF NOT EXISTS`，此错误不应出现。如果出现，检查是否有其他迁移文件也在创建此表。

### 问题 3：列已存在

```
ERROR: Duplicate column name 'contract_type'
```

**解决方案**：V2 已使用 `ADD COLUMN IF NOT EXISTS`，此错误不应出现。

## 📞 支持

如有问题，请检查：
1. 后端日志中的 Flyway 迁移信息
2. 数据库迁移历史表 `flyway_schema_history`
3. 备份文件 `flyway_schema_history_backup.sql`
