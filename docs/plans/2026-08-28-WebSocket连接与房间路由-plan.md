# WebSocket 连接与房间路由实施计划

> **供执行智能体使用：** REQUIRED SUB-SKILL: 使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务实施。本计划使用复选框跟踪。

**目标：** 将应用级与房间级 WebSocket 连接分离，并在 `yuanlive-live-service` 多实例场景中通过 Redis 路由目录和 RabbitMQ 实例专属队列实现定向投递与断线补偿。

**架构：** 应用级连接保留每位用户一个主页面 Channel；每个直播页创建一个绑定单房间的 Channel。Redis 记录连接承载实例，RabbitMQ 只向这些实例的专属队列投递，实例再写本机 Channel 或 ChannelGroup。

**技术栈：** Vue 3、Tauri 2/Rust、Netty、Spring Boot、Redis、RabbitMQ、Vitest、JUnit 5、Testcontainers。

**依据：** [PRD](../prd/2026-08-28-WebSocket连接与房间路由-prd.md)、[SPEC](../specs/2026-08-28-WebSocket连接与房间路由-spec.md)。

## 全局约束

- 仅在两个项目的 `dev` 分支实施，且不得覆盖用户已有未提交修改。
- `Channel` 仅绑定一个 `roomId`；多房间观看通过多条 ROOM 连接实现。
- `USER_MAP` 只收录 `scope=APP` 的主页面连接，ROOM 连接不得覆盖它。
- 所有跨实例实时事件必须带 `eventId`；房间事件还必须带 `seq`。
- 普通 CHAT 不得使用所有 `live-service` 实例的 fanout 广播。
- Redis 路由键必须有 TTL，并由连接/实例心跳续期。
- 使用 Rendezvous Hash（HRW）实现一致性哈希；使用 `unsignedHash64(routeKey + "|" + stableInstanceId)` 选最高分实例，禁止 Java `String.hashCode()` 与随机实例 ID。
- 一致性哈希只用于新连接接入：APP 使用网关验证 Token 后的 `userId`，ROOM 使用 `roomId`；对已连接消息的投递以 Redis 路由目录为准。
- 本地路由缓存最长 10 秒；缓存失效、目标返回 `ROUTE_STALE` 或实例租约无效时必须刷新 Redis 并用原 `eventId` 重投。
- 对幂等、TTL、断线补偿、实例 epoch 和资金事件边界添加简体中文注释。

---

### 任务 1：定义连接范围与实时事件契约

**文件：**

- 修改：`yuanlive-server/yuanlive-live-service/src/main/java/**/server/SessionManager.java`。
- 修改：`yuanlive-server/yuanlive-live-service/src/main/java/**/message/Message.java` 及请求/响应 DTO。
- 新增：连接范围、实时事件 DTO 与序列化测试。

**接口：**

- 产生 `ConnectionScope { APP, ROOM }`。
- 产生 `RealtimeEvent { eventId, seq, scope, roomId, type, timestamp, data }`。

- [ ] 为 APP/ROOM 的 JSON 序列化、ROOM 缺少 roomId、ROOM 事件缺少 seq 编写失败测试。
- [ ] 运行测试，确认新枚举和 DTO 尚不存在或校验失败。
- [ ] 实现枚举、属性键和事件 DTO；禁止客户端提交 `userId` 与实例归属字段。
- [ ] 运行契约测试，确认通过。
- [ ] 提交：`:sparkles: 定义实时连接范围与事件契约`。

### 任务 2：实现服务端单房间握手与本机会话分离

**文件：**

- 修改：`.../server/AuthHandshakeHandler.java`。
- 修改：`.../server/NettyServerHandler.java`、`SessionManager.java`。
- 修改：`.../service/impl/LiveMessageServiceImpl.java`。
- 测试：握手、JOIN_ROOM 兼容和断开清理测试。

**接口：**

- `registerAppChannel(Long userId, Channel channel)`。
- `registerRoomChannel(String roomId, Channel channel)`。
- `remove(Channel channel)` 按 scope 分支清理。

- [ ] 编写失败测试：ROOM 握手自动加入房间、ROOM 连接不写 USER_MAP、APP 连接不能 JOIN_ROOM、已绑定不同 roomId 的 JOIN_ROOM 被拒绝。
- [ ] 运行指定 JUnit 测试，确认失败原因与新规则一致。
- [ ] 在握手解析 `scope`、`roomId`、`lastSeq`；APP 与 ROOM 分别注册，ROOM 校验房间直播状态。
- [ ] 保留旧 JOIN_ROOM 灰度兼容：只允许未绑定房间的 Channel 首次绑定，禁止切换房间。
- [ ] 运行 Netty、消息服务和 SessionManager 测试，确认通过。
- [ ] 提交：`:sparkles: 分离应用级与房间级会话`。

### 任务 3：实现 Redis 路由目录与租约

**文件：**

- 新增：`.../realtime/routing/ConnectionRouteRegistry.java`、配置属性与 Lua 脚本。
- 修改：`SessionManager.java`、应用启动/关闭生命周期配置。
- 测试：Redis/Testcontainers 路由注册、续期、旧 connectionId 保护和过期过滤测试。

**接口：**

- `registerApp(userId, instanceId, connectionId)`。
- `registerRoom(roomId, instanceId)`。
- `resolveApp(userId)` 与 `resolveRoomInstances(roomId)`。
- `unregisterAppIfCurrent(userId, connectionId)` 与 `unregisterRoomIfEmpty(roomId, instanceId)`。

- [ ] 编写失败测试：旧 APP Channel 断开不得删除新连接路由；房间多实例集合应返回未过期成员；失效实例租约必须被过滤。
- [ ] 编写失败测试：APP/ROOM 路由缓存到期后必须查询 Redis；目标 connectionId 不匹配或空房间 group 返回 `ROUTE_STALE` 后，发送方以原 eventId 刷新并重投。
- [ ] 运行 Redis 集成测试，确认失败。
- [ ] 实现 TTL、10 秒续期、实例 epoch、过期过滤与优雅下线清理。
- [ ] 运行路由目录测试，确认通过。
- [ ] 提交：`:sparkles: 增加实时连接 Redis 路由目录`。

### 任务 4：实现实例专属 RabbitMQ 定向投递

**文件：**

- 新增：`.../realtime/dispatch/InstanceDispatchTopology.java`、Publisher、Consumer、DTO。
- 修改：现有 Chat/Gift/System 事件发布入口。
- 测试：RabbitMQ 拓扑、APP 单播、ROOM 定向多播、epoch 不匹配丢弃与 eventId 去重测试。

**接口：**

- `dispatchToApp(userId, RealtimeEvent event)`。
- `dispatchToRoom(roomId, RealtimeEvent event)`。
- `deliverLocal(RealtimeEvent event)`。

- [ ] 编写失败测试：仅目标实例队列收到私信；房间包含 a、c 时只向 a、c 投递；路由仅有本机时 CHAT 不发布 MQ。
- [ ] 运行 RabbitMQ/Testcontainers 测试，确认失败。
- [ ] 实现实例唯一队列、direct exchange、publisher confirm、消费者 epoch 校验和本机 Channel/ChannelGroup 投递。
- [ ] 添加有界 eventId 去重缓存，防止路由重叠时重复下发。
- [ ] 运行定向投递测试，确认通过。
- [ ] 提交：`:sparkles: 增加实时消息实例定向投递`。

### 任务 5：实现房间 seq 缓冲与重连补偿

**文件：**

- 新增：`.../realtime/replay/RoomEventBuffer.java`。
- 修改：聊天、礼物和系统事件发布链路；ROOM 握手完成逻辑。
- 测试：seq 单调递增、缓冲边界、lastSeq 补拉、缓冲缺口 `RESYNC_REQUIRED` 和客户端去重测试。

**接口：**

- `append(roomId, event): RealtimeEvent`。
- `replayAfter(roomId, lastSeq): ReplayResult`。

- [ ] 编写失败测试：事件必须在本机/跨实例投递前获得 seq；重连从 lastSeq+1 补发；缺口超出缓冲时返回重同步信号。
- [ ] 运行失败测试。
- [ ] 使用 Redis Stream 或 ZSet 实现每房间最多 500 条、最长 10 分钟的有界缓冲。
- [ ] 在 ROOM 握手自动入组后回放消息；客户端确保仅提交连续的最后 seq。
- [ ] 运行回放和消息服务测试，确认通过。
- [ ] 提交：`:sparkles: 增加房间消息重连补偿`。

### 任务 6：改造 Tauri 客户端连接管理

**文件：**

- 修改：`/home/frodepu/IdeaProjects/yuanlive-tauri/src-tauri/src/websocket/client.rs`、`commands.rs`、`types.rs`。
- 修改：`/home/frodepu/IdeaProjects/yuanlive-tauri/src/services/webSocketRust.ts`。
- 新增：应用连接与房间连接实例管理、Rust/TypeScript 测试。

**接口：**

- `connectApp()`：单例 APP 连接。
- `connectRoom(roomId, viewId, lastSeq)`：按 viewId 管理独立 ROOM 连接。
- `disconnectRoom(viewId)`：只关闭指定观看页连接。

- [ ] 编写失败测试：登录仅创建一个 APP client；两个 viewId 创建两条 ROOM client；关闭 room-A 不影响 APP 与 room-B。
- [ ] 运行 Rust 与 Vitest 测试，确认失败。
- [ ] 实现多客户端容器、scope/roomId/lastSeq 握手参数、房间事件回调和重连 lastSeq 保存。
- [ ] 确保 Token 仅写请求协议头，日志不会记录 Token。
- [ ] 运行 Rust 测试、Vitest 和 `pnpm build`，确认通过。
- [ ] 提交：`:sparkles: 支持客户端应用与房间独立连接`。

### 任务 7：接入观看页与主播弹幕窗口

**文件：**

- 修改：`/home/frodepu/IdeaProjects/yuanlive-tauri/src/views/homeWindow/LivePlay.vue`。
- 修改：`/home/frodepu/IdeaProjects/yuanlive-tauri/src/views/recordWindow/Danmaku.vue`、`liveChat.ts`。
- 测试：观看页进入/离开、主播弹幕窗口、双房间隔离和重连展示测试。

- [ ] 编写失败测试：进入 A、B 两房间得到独立连接；A 的 CHAT 不出现在 B；主播弹幕窗口不使用 APP 连接。
- [ ] 运行组件/工具测试，确认失败。
- [ ] 在观看页 mounted 创建 ROOM 连接、unmounted 发送 LEAVE_ROOM 并关闭；主播弹幕窗口按主播 roomId 创建 ROOM 连接。
- [ ] 按 roomId、eventId、seq 过滤并展示事件，保留现有礼物去重逻辑。
- [ ] 运行 `pnpm test:unit && pnpm build`，确认通过。
- [ ] 提交：`:sparkles: 接入观看页与主播房间连接`。

### 任务 8：网关路由、扩缩容与端到端验证

**文件：**

- 修改：网关或 Ingress 的 WebSocket 路由配置与部署文档。
- 修改：`docker/start/start.md`、运行手册、监控配置。
- 测试：多实例容器集成测试、断线补偿和灰度兼容验证脚本。

- [ ] 为同 roomId 路由稳定性、多实例房间集合定向多播、实例宕机后重连补拉编写端到端测试。
- [ ] 在两实例 live-service 环境运行测试，确认失败。
- [ ] 为 Gateway 编写 HRW 选择器测试：同一 `routeKey` 与候选集选择固定实例；新增实例仅改变部分 key；`draining=true` 和不健康实例永不入选；分数并列按稳定实例 ID 处理。
- [ ] 实现 Gateway 的 `ReactorServiceInstanceLoadBalancer`：APP 使用网关验证 Token 后的 `userId`，ROOM 使用 `scope=ROOM&roomId=`；仅对 `/ws/**` 生效，其他 `live-service` HTTP 路由保留现有负载均衡。
- [ ] 验证新连接按 HRW 路由，已连接消息仍以 Redis 路由目录定向投递；确认 HRW 只使用本地候选列表计算，不增加 WebSocket 握手或网络请求。
- [ ] 验证扩容、优雅下线、强制宕机、双观看页、主播弹幕、私信和礼物事件。
- [ ] 运行后端相关 Maven 测试、客户端 `pnpm test:unit && pnpm build`，记录指标、限制与回滚步骤。
- [ ] 提交：`:sparkles: 完成多实例实时连接路由验证`。

## 完成判定

- APP 和 ROOM 连接职责分离，且 ROOM 连接不覆盖 USER_MAP。
- 同一用户可同时观看多个房间，消息完全隔离。
- 单实例房间聊天为本机 ChannelGroup 广播；多实例过渡期为 Redis 实例集合定向多播。
- 不存在全实例消费普通聊天消息的拓扑。
- 路由目录、实例租约、eventId 去重、seq 补拉和优雅下线都经过自动化测试与多实例实测。
