# 数据库说明

在 `worth/` 工作目录运行程序，默认数据库为 `jdbc:h2:file:./data/worth`。`data/` 为本地运行数据，不提交 Git。

## 表结构

- users：id、username、password_hash。用户名唯一，密码只保存 BCrypt 哈希。
- cards：id、owner_id、name、description、status。owner_id 引用 users，同一用户的卡片名称唯一。
- card_history：id、card_id、occurred_at、status。card_id 引用 cards，按历史 ID 顺序恢复状态。

`schema.sql` 在启动时创建缺失的表，不会自动迁移已有表结构。使用其他旧数据库前需要单独备份并迁移。

## 职责

Controller 从 Session 取身份并确认用户存在，Service 检查卡片归属，Repository 负责 SQL。Repository 不自行判断当前登录用户。

创建卡片时，卡片和初始 TODO 历史在同一事务中保存。状态更新先锁定卡片行，再检查状态转换，并在同一事务中保存状态和新历史。读取卡片时会检查当前状态与最后一条历史是否一致。

`Card` 的状态修改只改变内存，持久化由 Repository 完成。Repository 的删除能力目前没有对应 HTTP 接口。
