# 合同段落变更类型识别服务

基于 Java 8、Spring Boot 2.1.5、MyBatis-Plus、Oracle 11g 和公司内网统一
Embedding 网关，实现“历史段落导入—语义检索—多标签变更类型推荐”的第一版核心闭环。

## 业务边界

- 历史样本通过 `.xlsx` 导入，每行包含“合同段落”和“变更类型编码”。
- 多个编码以英文分号保存，例如 `TYPE01;TYPE02;TYPE03`。
- Oracle负责持久化，JVM内存负责1万条以内向量的精确检索。
- 单次Excel最多1000条；导入同步执行，默认逐条调用模型网关。
- 预测结果不入库，接口输入必须是已经切分好的单个合同段落。
- 段落默认最多480字符，超过上限明确拒绝，不做自动截断。
- 变更类型只作为历史段落标签参与投票，不与新段落进行向量比较。
- 所有业务SQL位于MyBatis XML中，Mapper接口不使用SQL注解。
- 不使用向量数据库、Rerank、异步任务表或模型自部署服务。

## 数据库初始化

使用业务用户执行：

```text
database/oracle/01_schema.sql
```

脚本只创建 `TPIF_HTDLYB`、`SEQ_TPIF_HTDLYB` 和 `IDX_HTDLYB_MODEL`。
`99_rollback.sql`会删除本版表及序列，表内数据不可恢复，生产环境谨慎执行。

接入统一模型网关不需要修改表结构。`MODEL_VERSION`与`VECTOR_DIM`用于隔离不同模型产生的向量。

## 统一Embedding网关

Java服务直接调用公司内网统一模型平台，不再部署 Hugging Face、Python 或模型Docker容器。
接口采用OpenAI兼容格式：

```http
POST <EMBEDDING_URL>
Authorization: Bearer <EMBEDDING_API_KEY>
user_id: <实际操作人工号>
Content-Type: application/json

{
  "model": "<EMBEDDING_MODEL_NAME>",
  "input": "待向量化合同段落"
}
```

批量能力确认后，`input`可以是字符串数组。响应必须包含：

```json
{
  "data": [
    {"embedding": [0.012, -0.038, 0.071]}
  ]
}
```

Java会校验返回数量、实际维度、非法浮点数和零向量，并执行L2归一化。模型网关手册中的
路径示例存在差异，因此 `EMBEDDING_URL` 必须配置平台确认后的完整地址，Java不会自行拼接。

建议地址形式：

```text
测试：http://aihub-test.citicsinfo.com/embedding/api/<部署名称>/v1/embeddings
生产：http://aihub.citicsinfo.com/embedding/api/<部署名称>/v1/embeddings
```

测试环境需确认能够访问 `10.63.36.231:80`，生产环境需确认能够访问 `10.121.148.231:80`。

## 配置

模型关键参数没有代码默认值，缺少时应用启动失败：

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `ORACLE_URL` | 本机开发占位地址 | Oracle连接地址 |
| `ORACLE_USERNAME` / `ORACLE_PASSWORD` | 开发占位值 | 数据库凭据 |
| `EMBEDDING_URL` | 无 | 模型平台确认的完整Embedding地址 |
| `EMBEDDING_API_KEY` | 无 | 模型平台密钥，严禁提交到Git或输出到日志 |
| `EMBEDDING_MODEL_NAME` | 无 | 请求体`model`字段使用的模型名称 |
| `EMBEDDING_MODEL_VERSION` | 无 | 数据库存储和索引隔离使用的模型版本 |
| `EMBEDDING_DIMENSION` | 无 | 平台确认的实际向量维度 |
| `EMBEDDING_BATCH_SIZE` | `1` | 单次网关请求文本数量，确认平台上限后再调大 |
| `IMPORT_MAX_ROWS` | `1000` | 单次Excel最大数据行数 |
| `IMPORT_MAX_TOTAL_SAMPLES` | `10000` | 第一版历史样本总数上限 |
| `SEARCH_MAX_PARAGRAPH_LENGTH` | `480` | 规范化段落最大字符数，不等同于Token数 |
| `SEARCH_MIN_SIMILARITY` | `0.60` | 最低召回相似度 |
| `SEARCH_HIGH_THRESHOLD` | `0.80` | 高可信类型得分阈值 |
| `SEARCH_CANDIDATE_THRESHOLD` | `0.55` | 候选类型得分阈值 |
| `SEARCH_STRONG_MATCH_THRESHOLD` | `0.80` | 强相似单条候选兜底阈值 |

`EMBEDDING_MODEL_NAME`用于实际网关请求；`EMBEDDING_MODEL_VERSION`用于向量兼容性隔离。
即使平台模型别名不变，只要底层模型发生变化，也必须使用新的版本标识并重新生成历史向量。

## Excel格式与导入流程

第一张Sheet前两列表头必须完全一致：

| 合同段落 | 变更类型编码 |
|---|---|
| 历史段落A | TYPE01;TYPE02;TYPE03 |
| 历史段落B | TYPE05;TYPE09 |

导入接口为同步接口。处理顺序为：文件校验、文本规范化、Hash去重、冲突判断、网关向量化、
单事务入库、索引重载。任一模型调用失败时，本批数据不会入库。

相同Hash、相同类型、当前模型版本及维度一致时幂等跳过；相同类型但模型版本或维度变化时
更新原记录；生效记录的类型编码不同时整批拒绝。

## 接口

服务上下文：`/glxt-service-contract-change`

```http
POST /service/contract-change/samples/import
POST /service/contract-change/predict
POST /service/contract-change/index/reload
GET  /service/contract-change/index/status
```

导入和预测接口必须携带：

```http
user_id: 实际操作人工号
```

该值只透传给模型平台用于审计，不写数据库、不输出到日志。索引重载和状态查询不调用模型，
因此不要求该请求头。

如果接口经过Nginx等反向代理，必须确认代理允许并原样转发带下划线的`user_id`请求头；
Nginx默认配置可能忽略此类请求头，可由运维按内网规范开启`underscores_in_headers`。

预测请求示例：

```http
POST /glxt-service-contract-change/service/contract-change/predict
user_id: employee-001
Content-Type: application/json

{"paragraph":"新的合同段落"}
```

预测处理顺序：Hash精确匹配；未命中时生成新段落向量；与历史段落向量计算余弦相似度；
取前10条并过滤低相似记录；执行多标签平方加权投票；返回最多5条参考历史段落。

响应中的匹配类型：

- `EXACT`：规范化文本Hash完全相同，直接返回历史类型，不调用模型。
- `SEMANTIC`：语义检索和投票得到候选类型。
- `NO_RELIABLE_MATCH`：没有足够可靠的类型结果，可能仍返回参考段落。

`maxSimilarity`、`changeTypes[].score`和`references[].similarity`最多保留四位小数；内部计算、
排序和阈值判断仍使用完整精度。

Swagger UI：

```text
http://localhost:8080/glxt-service-contract-change/swagger-ui.html
```

Actuator只检查Java服务、数据库和内存索引，不主动调用模型推理接口：

```text
http://localhost:8080/glxt-service-contract-change/actuator/health
```

## 启动与迁移

```powershell
$env:JAVA_HOME='D:\tools\Java\jdk1.8.0_481'
$env:ORACLE_URL='jdbc:oracle:thin:@10.0.0.10:1521:ORCL'
$env:ORACLE_USERNAME='glxt'
$env:ORACLE_PASSWORD='***'
$env:EMBEDDING_URL='http://aihub-test.citicsinfo.com/embedding/api/<部署名称>/v1/embeddings'
$env:EMBEDDING_API_KEY='***'
$env:EMBEDDING_MODEL_NAME='<平台分配模型名称>'
$env:EMBEDDING_MODEL_VERSION='<本次向量兼容版本>'
$env:EMBEDDING_DIMENSION='<平台确认维度>'

mvn test
mvn -DskipTests package
java -jar target/glxt-service-contract-change-1.0.0.jar
```

切换模型时必须在维护窗口内使用原历史Excel全量重新导入。索引只加载当前模型版本和维度的
记录，禁止新旧模型向量混合检索。完成后核对数据库有效记录数与索引样本数再开放预测。

新模型的相似度分布可能变化，首轮仍沿用现有阈值；应使用人工确认样本验证后通过环境变量
调参，不应仅根据模型名称直接修改算法阈值。

## 错误与重试

- `400/422`：模型请求格式、输入长度或批量参数被网关拒绝。
- `401/403`：API Key错误或没有模型权限。
- `404`：完整接口地址或模型部署名称错误。
- `429`：模型平台限流。
- `5xx`、连接或读取异常：模型平台暂时不可用。

只有连接异常和`5xx`最多重试一次；其他错误不重试。对外错误不会包含平台响应体、API Key、
`user_id`、合同正文或向量。

## 日志与敏感信息

- 记录导入、事务、索引、模型调用和预测的数量、状态及耗时。
- 合同段落只记录规范化文本SHA-256，不记录完整正文。
- 不记录API Key、`user_id`、请求体、模型响应体或向量。
- 不建议开启Mapper或RestTemplate DEBUG，避免输出CLOB、BLOB或模型请求信息。
