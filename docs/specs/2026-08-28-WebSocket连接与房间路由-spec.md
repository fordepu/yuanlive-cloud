# WebSocket 连接与房间路由 SPEC

## 1. 架构边界

`yuanlive-live-service` 的 `USER_MAP` 和 `ROOM_MAP` 都是本机内存索引，绝不作为跨实例路由事实。跨实例路由事实存储在 Redis；跨实例投递通过 RabbitMQ 实例专属队列完成。

```text
应用连接: userId → Redis 路由 → 实例专属队列 → USER_MAP[userId]
房间连接: roomId → Redis 实例集合 → 实例专属队列 → ROOM_MAP[roomId]
```

### 1.1 接入路由与消息投递的职责分离

| 时机 | APP | ROOM | 权威来源 |
| --- | --- | --- | --- |
| 新建连接接入 | 网关对已验证 Token 中的 `userId` 做一致性哈希 | 网关对已校验的 `roomId` 做一致性哈希 | 网关 HRW 候选集 |
| 向已连接目标投递消息 | 查询用户 APP 路由 | 查询房间承载实例集合 | Redis 路由目录 |

网关只能使用认证后的 Token subject 作为 APP 路由键，绝不信任客户端查询参数中的 `userId`。一致性哈希用于提升连接稳定性和房间连接集中度；扩缩容会改变 HRW 候选集与计算结果，因此不得用重新计算的哈希结果覆盖或替代 Redis 当前路由。

本方案固定使用 Rendezvous Hash（HRW），不维护传统哈希环或虚拟节点。对每个可选实例计算 `unsignedHash64(routeKey + "|" + stableInstanceId)`，选择无符号分数最高的实例；分数相同按 `stableInstanceId` 字典序选择。`stableInstanceId` 必须由服务名、主机和端口组成，不能使用启动 epoch 或随机 ID。实现应使用 xxHash64 或 MurmurHash3 的稳定 64 位结果，不能使用 Java `String.hashCode()`。

网关先从 Nacos 获取健康 `live-service` 实例，过滤 metadata 为 `draining=true` 的实例，再执行 HRW。HRW 的 `O(N)` 是一次新连接接入时的本地 CPU 计算，不产生额外网络请求；网关获得 Nacos 候选集的方式与默认负载均衡一致。

## 2. 协议

### 2.1 应用级连接

```text
GET /ws?deviceID=<id>&scope=APP
Sec-WebSocket-Protocol: <accessToken>
```

- 不含 `roomId`，握手后不能发送 `JOIN_ROOM`。
- 网关在转发前验证 Token，并以认证后的 `userId` 使用 HRW 选择候选实例；服务端仍须再次校验 Token。
- `AuthHandshakeHandler` 校验 Token 后设置 `KEY_CONNECTION_SCOPE=APP`。
- `SessionManager.registerAppChannel(userId, channel)` 写本机 `USER_MAP`，并注册 Redis 路由。

### 2.2 房间级连接

```text
GET /ws?deviceID=<id>&scope=ROOM&roomId=<roomId>&lastSeq=<seq>
Sec-WebSocket-Protocol: <accessToken>
```

- `roomId` 必填，服务端验证房间处于直播状态后设置 `KEY_CONNECTION_SCOPE=ROOM` 和 `KEY_ROOM_ID=roomId`。
- 服务端握手成功后自动加入 `ROOM_MAP[roomId]`；客户端无需也不得发送第二次 `JOIN_ROOM`。
- 为灰度兼容保留 `JOIN_ROOM`：仅当 Channel 尚未绑定房间时可用；已绑定时请求中的 `roomId` 必须一致，否则返回 `400` 并关闭连接。
- `lastSeq` 缺省视为 `0`，服务端在完成加入后发送缓存中 `seq > lastSeq` 的有限消息。

### 2.3 灰度兼容连接

未携带 `scope` 的旧客户端在服务端标记为 `LEGACY`。其握手后仍先注册旧 `USER_MAP` 行为，并允许通过首帧 `JOIN_ROOM` 加入或切换房间；新客户端不得发送 `LEGACY`。该范围只用于过渡，且不参与新 APP/ROOM 定向路由能力。

### 2.4 公共事件结构

```json
{
  "eventId": "UUID",
  "seq": 42,
  "scope": "ROOM",
  "roomId": "790546539380737",
  "type": "CHAT",
  "timestamp": 1787900000,
  "data": {}
}
```

- `eventId`：全链路唯一，用于服务端消费者和客户端展示去重。
- `seq`：房间内单调递增，写入 Redis 有界缓冲后再广播。
- `scope`：`APP` 或 `ROOM`，消费者不得忽略。

## 3. 本机会话结构

```java
Map<Long, Channel> USER_MAP;                 // 仅 scope=APP
Map<String, ChannelGroup> ROOM_MAP;          // 仅 scope=ROOM
AttributeKey<ConnectionScope> KEY_CONNECTION_SCOPE;
AttributeKey<String> KEY_ROOM_ID;            // ROOM 连接唯一房间
AttributeKey<String> KEY_CONNECTION_ID;      // UUID，防旧连接误删路由
```

### 3.1 注册与清理

- APP 连接建立：原子写 `USER_MAP[userId]`，再写 Redis `ws:user:{userId}:app`；Redis 值为 `instanceId|epoch|connectionId`。实例 ID 含 `host:port`，因此字段分隔符固定为 `|`。
- ROOM 连接建立：先加入本机 `ROOM_MAP[roomId]`，再写 Redis 实例成员集合。
- APP 断开：只有 Redis 当前值的 `connectionId` 与断开 Channel 相同，才删除 `ws:user:{userId}:app`，防止旧连接删除新主连接。
- ROOM 断开：从本机 group 移除；若该实例此房间已无 Channel，删除该实例的房间成员记录。
- LEGACY 断开：沿用旧连接清理路径；不得影响 APP 连接的 connectionId 防旧连接保护。

## 4. Redis 路由目录

| 键 | 类型 | 值/成员 | TTL | 用途 |
| --- | --- | --- | --- | --- |
| `ws:instance:{instanceId}:lease` | String | 启动 epoch | 30 秒 | 实例存活租约 |
| `ws:user:{userId}:app` | String | `instanceId|epoch|connectionId` | 45 秒 | 用户主连接归属 |
| `ws:room:{roomId}:instances` | ZSet | `instanceId|epoch`，score 为过期毫秒 | 60 秒 | 承载该房间连接的实例集合 |
| `ws:room:{roomId}:seq` | String | 当前序号 | 与直播会话一致 | 房间事件序号 |
| `ws:room:{roomId}:buffer` | Stream/ZSet | `seq → event JSON` | 10 分钟或 500 条 | 断线补偿缓冲 |

每 10 秒续期一次。读取房间实例集合时过滤 `score < now` 的成员，并异步清除过期项。实例租约失效时，发送方必须跳过该实例，即使其仍存在于房间集合。

### 4.1 本地路由缓存与回退

每个发送实例可维护下列进程内存缓存，默认 TTL 为 5 秒，最长不得超过 10 秒：

```text
appRouteCache[userId] = { instanceId, epoch, connectionId, expireAt }
roomRouteCache[roomId] = { instances, expireAt }
```

缓存不是路由事实，不能持久化，也不能跨进程共享。发送过程必须遵循：

1. 缓存有效且其中的实例租约有效时，可据此定向投递。
2. 缓存未命中、TTL 到期、路由变更通知到达、实例租约无效或目标实例返回 `ROUTE_STALE` 时，立即删除缓存。
3. 删除后读取 Redis 路由目录，写回新缓存，并以原 `eventId` 重投一次。
4. APP 目标消费者只在 `connectionId` 等于当前 `USER_MAP[userId]` 连接时发送；不等则返回 `ROUTE_STALE`。
5. ROOM 目标消费者若没有本机 group 或 group 为空，返回 `ROUTE_STALE`；发送方刷新房间实例集合。重复投递由 `eventId` 去重。

对资金相关通知、礼物展示和其他不允许漏发的实时事件，发送方必须直接读 Redis 或收到最新路由变更通知后再投递；普通 CHAT 可使用有效短缓存，但重连补偿仍是最终保障。

## 5. RabbitMQ 定向拓扑

每个实例在启动时声明唯一队列，队列名由固定前缀、实例 ID 与实例启动 epoch 组成：

```text
live.ws.dispatch.instance-<instanceId>-<epoch>
```

使用 direct exchange `live.ws.dispatch.exchange`，路由键等于队列完整名称。实例启动时绑定其专属队列；关闭时取消消费并等待队列自动删除，避免新实例接收旧实例消息。

定向事件：

```json
{
  "eventId": "UUID",
  "targetInstanceId": "live-service-2",
  "targetEpoch": "...",
  "scope": "ROOM",
  "roomId": "...",
  "payload": {}
}
```

消费者校验 `targetInstanceId` 与 `targetEpoch` 均匹配本机，再按 `scope` 调用本机投递器：

- `APP`：验证 `USER_MAP[userId]` 的 `connectionId`，写单个 Channel。
- `ROOM`：从 `ROOM_MAP[roomId]` 获取 group，写本机 group。

普通 `CHAT` 在发送者即为当前房间承载实例且 Redis 路由目录（或有效本地缓存）只包含该实例时，直接本机广播，不进入 RabbitMQ。目录含多个实例、缓存失效或发送者非目标实例时，刷新 Redis 路由后按集合进行定向投递。

## 6. 顺序、去重与恢复

- 同一房间事件在 owner 实例分配 `seq` 并写入缓冲后才允许投递。
- 房间实例消费者维护有界 `eventId` 去重缓存；客户端也维护有界 `eventId` 集合。
- RabbitMQ 投递采用 publisher confirm；未确认事件不标记为完成，可按业务类型重试。
- WebSocket 是非可靠末端：网络断开期间无法确认客户端收到。客户端重连携带最后连续 `seq`，服务端从缓冲补发；缓冲之外的缺口返回 `RESYNC_REQUIRED`，客户端重新加载房间状态。
- 礼物、订单和系统状态依旧保留既有可靠业务事件链路；本方案不以聊天室广播替代资金消息可靠性。

## 7. 扩缩容与故障

1. 新实例启动：生成新 `instanceId/epoch`，声明专属队列，写实例租约，随后才允许 Nacos 将它作为 HRW 候选实例。新增候选实例后，只有部分新连接会改选至新实例；既有 WebSocket 不迁移。
2. 扩缩容期间：HRW 的候选集变化可能让旧、新实例同时承载同一房间；Redis 房间实例集合会包含多个成员，发送方以该集合进行定向多播，不能只按新哈希结果投递。
3. 优雅下线：实例先从负载均衡摘除，停止接收新连接，继续续期并服务存量连接；达到连接迁移/超时阈值后关闭连接，客户端重连至新实例；最后取消队列消费并删除租约。
4. 非正常宕机：实例租约过期后发送方不再投递到该实例；客户端检测连接断开后重连并按 `lastSeq` 补偿。

## 8. 安全与可观测性

- 服务端以认证身份为准，禁止客户端伪造 `userId`、`instanceId` 或其他归属字段。
- ROOM 连接只能接收和发送已绑定 `roomId` 的事件；聊天请求内不同房间号直接拒绝。
- 指标：APP/ROOM 连接数、每房间实例数、路由查找耗时、定向投递数量、全本机直发比例、路由过期命中、补拉条数、去重丢弃数。
- 日志必须包含 `instanceId`、`connectionId`、`scope`、`roomId`、`eventId` 和 `seq`，Token 不得记录。
