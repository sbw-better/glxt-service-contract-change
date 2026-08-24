# 合同段落变更类型识别服务

基于 Java 8、Spring Boot 2.1.5、MyBatis-Plus、Oracle 11g 和内网 Hugging Face Embedding 容器，实现“历史段落导入—语义检索—多标签变更类型推荐”的第一版核心闭环。

## 业务边界

- 历史样本通过 `.xlsx` 导入，每行包含“合同段落”和“变更类型编码”。
- 变更类型编码由历史 Excel 直接提供，作为稳定的业务标签使用。
- 一个段落的多个编码以英文分号保存，例如 `TYPE01;TYPE02;TYPE03`。
- 使用768维归一化向量；Oracle负责持久化，Java内存负责精确检索。
- 第一版最多保存并加载1万条历史段落，单次Excel最多1000条。
- BGE Base输入上限为512 Token；第一版将规范化段落限制为480字符，并要求模型容器关闭自动截断。
- 预测结果不入库，接口接收的内容是已经切分好的单个合同段落。
- 所有业务SQL位于MyBatis XML中，Mapper接口不使用SQL注解。

## 数据库初始化

使用业务用户执行：

```text
database/oracle/01_schema.sql
```

脚本只创建：

```text
TPIF_HTDLYB
SEQ_TPIF_HTDLYB
IDX_HTDLYB_MODEL
```

`99_rollback.sql`会删除本版表及序列，表内数据不可恢复，生产环境谨慎执行。

## Embedding容器

模型容器需要提供兼容接口：

```http
POST /embed
Content-Type: application/json

{"inputs":["历史段落A","历史段落B"]}
```

响应为二维浮点数组：

```json
[[0.012,-0.038,0.071],[0.027,-0.011,0.044]]
```

开发、测试、生产应使用同一个镜像摘要和模型版本。容器默认地址为 `http://127.0.0.1:8081/embed`，模型固定输出768维。Java会校验维度、非法数值并再次执行L2归一化。

模型容器必须配置 `AUTO_TRUNCATE=false`。如果保持默认自动截断，过长合同段落可能只计算前半部分却仍返回成功，造成不可见的数据错误。当前CPU容器的 `MAX_CLIENT_BATCH_SIZE` 为4，Java默认批量同样设置为4。

## 配置

主要环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ORACLE_URL` | `jdbc:oracle:thin:@127.0.0.1:1521:ORCL` | Oracle连接地址 |
| `ORACLE_USERNAME` / `ORACLE_PASSWORD` | 开发占位值 | 数据库凭据 |
| `EMBEDDING_URL` | `http://127.0.0.1:8081/embed` | 批量Embedding接口 |
| `EMBEDDING_HEALTH_URL` | `http://127.0.0.1:8081/health` | 模型健康接口 |
| `EMBEDDING_MODEL_VERSION` | `bge-base-zh-768-v1` | 模型版本 |
| `EMBEDDING_DIMENSION` | `768` | 向量维度 |
| `EMBEDDING_BATCH_SIZE` | `4` | 导入批量大小，不能超过模型容器最大客户端批量 |
| `IMPORT_MAX_ROWS` | `1000` | 单次Excel最大数据行数 |
| `IMPORT_MAX_TOTAL_SAMPLES` | `10000` | 第一版历史样本物理记录总数上限 |
| `SEARCH_MAX_PARAGRAPH_LENGTH` | `480` | 规范化段落最大字符数 |
| `SEARCH_MIN_SIMILARITY` | `0.60` | 最低召回相似度 |
| `SEARCH_HIGH_THRESHOLD` | `0.80` | 高置信度标签阈值 |
| `SEARCH_CANDIDATE_THRESHOLD` | `0.55` | 候选标签阈值 |
| `SEARCH_STRONG_MATCH_THRESHOLD` | `0.80` | 投票无结果时第一名启用兜底的最低相似度 |

## Excel格式

第一张Sheet的前两列表头必须完全一致：

| 合同段落 | 变更类型编码 |
|---|---|
| 历史段落A | TYPE01;TYPE02;TYPE03 |
| 历史段落B | TYPE05;TYPE09 |

导入会完成文本规范化、SHA-256去重、编码去重排序、批量向量化和单事务入库。当前模型、当前维度、生效状态和编码都一致时幂等跳过；停用记录会更新并重新启用；编码相同但模型版本或维度变化时会更新原记录并重新生成向量；生效记录编码不同时整批拒绝。

## 接口

服务上下文：`/glxt-service-contract-change`

```http
POST /service/contract-change/samples/import
POST /service/contract-change/predict
POST /service/contract-change/index/reload
GET  /service/contract-change/index/status
```

预测请求：

```json
{
  "paragraph": "新的合同段落"
}
```

预测响应包含：

- `EXACT`、`SEMANTIC`或`NO_RELIABLE_MATCH`；
- 变更类型编码、投票得分、支持样本数和置信等级；
- 最多5条相似历史段落和相似度。

语义预测使用最相似的10条历史样本进行多标签加权投票，返回其中最多5条作为参考。投票没有
产出任何类型时，如果第一名相似度不低于`SEARCH_STRONG_MATCH_THRESHOLD`，则将第一名历史
段落的类型作为`CANDIDATE`返回；单条证据兜底不会产生`HIGH`等级，`score`仍表示投票得分，
第一名段落相似度使用响应已有的`maxSimilarity`查看。

响应中的`maxSimilarity`、`changeTypes[].score`和`references[].similarity`以JSON数字返回，
最多保留四位小数；内部相似度计算、排序和阈值判断仍使用完整精度。

Swagger UI：

```text
http://localhost:8080/glxt-service-contract-change/swagger-ui.html
```

Actuator健康检查：

```text
http://localhost:8080/glxt-service-contract-change/actuator/health
```

## 启动与验证

先启动Oracle和Embedding容器，再启动Java服务：

```powershell
$env:JAVA_HOME='D:\tools\Java\jdk1.8.0_481'
$env:ORACLE_URL='jdbc:oracle:thin:@10.0.0.10:1521:ORCL'
$env:ORACLE_USERNAME='glxt'
$env:ORACLE_PASSWORD='***'
$env:EMBEDDING_URL='http://127.0.0.1:8081/embed'

mvn test
mvn -DskipTests package
java -jar target/glxt-service-contract-change-1.0.0.jar
```

应用启动时会从Oracle加载当前模型版本、当前维度且 `SFSX=1` 的历史样本。索引构建成功后原子替换旧快照；单条损坏向量会被跳过并将状态标记为 `DEGRADED`。

索引状态包括：`NOT_READY`、`READY`、`EMPTY`、`DEGRADED`和`LOAD_FAILED`。正常空库不会调用Embedding；加载失败或数据库有记录但全部向量损坏时预测返回服务不可用。Actuator同时检查Embedding和内存索引状态。

模型升级时保持同一Excel标签重新导入即可：系统会识别旧 `MODEL_VERSION` 或旧维度，在原行上更新向量和模型版本，不新增表、不产生相同文本的重复记录。必须完成全量重新导入后再验收索引数量。

## 日志与敏感信息

- 导入、事务入库、索引重载、模型调用和预测均记录开始/完成、数量、状态及耗时。
- 合同段落只记录规范化文本的 SHA-256，不记录完整正文；向量内容永不写入日志。
- 模型异常记录 HTTP 状态、重试次数和异常类型，不记录请求体。
- 默认业务日志级别为 `INFO`，高频内存检索明细为 `DEBUG`。
- 不建议开启 Mapper 的 `DEBUG` SQL 参数日志，否则可能输出 CLOB 合同正文和 BLOB 参数。
