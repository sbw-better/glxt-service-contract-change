# 合同段落变更类型识别服务

基于 Java 8、Spring Boot 2.1.5、MyBatis-Plus、Oracle 11g 和公司内网统一
Embedding 网关，实现“历史段落导入—语义检索—多标签变更类型推荐”的第一版核心闭环。

## 业务边界

- 历史样本通过 `.xlsx` 导入，每行包含“合同段落”和“变更类型编码”。
- 多个编码以英文分号保存，例如 `TYPE01;TYPE02;TYPE03`。
- Oracle负责持久化，JVM内存负责1万条以内向量的精确检索。
- 单次Excel最多1000条；导入同步执行，默认逐条调用模型网关。
- 合同比对预测结果只随响应返回，不会自动写入历史向量库；历史样本继续通过 Excel 导入维护。
- 段落默认最多2000字符，超过上限明确拒绝，不做自动截断。
- 第一版Java服务按单实例部署，保证导入后JVM内存索引立即一致。
- 变更类型只作为历史段落标签参与投票，不与新段落进行向量比较。
- 所有业务SQL位于MyBatis XML中，Mapper接口不使用SQL注解。
- 不使用向量数据库、Rerank、异步任务表或模型自部署服务。

## 数据库初始化

使用业务用户按顺序执行：

```text
database/oracle/01_schema.sql
```

`01_schema.sql`创建历史段落向量库。
`99_rollback.sql`会删除本版表及序列，表内数据不可恢复，生产环境谨慎执行。

接入统一模型网关不需要修改表结构。`MODEL_VERSION`与`VECTOR_DIM`用于隔离不同模型产生的向量。

## 统一Embedding网关

Java服务直接调用公司内网统一模型平台，不再部署 Hugging Face、Python 或模型Docker容器。
接口采用OpenAI兼容格式：

```http
POST <EMBEDDING_URL>
Authorization: Bearer <EMBEDDING_API_KEY>
UserId: <实际操作人工号>
Content-Type: application/json

{
  "model": "gen-studio-Qwen3-Embedding-8B",
  "input": "待向量化合同段落",
  "dimensions": "1024",
  "encoding_format": "float"
}
```

批量能力确认后，`input`可以是字符串数组。响应必须包含：

```json
{
  "data": [
    {"index": 0, "embedding": [0.012, -0.038, 0.071]}
  ]
}
```

Java会校验返回数量、批量响应`index`、实际维度、非法浮点数和零向量，并执行L2归一化。
网关虽然允许省略`dimensions`，本项目仍每次显式发送，避免依赖平台默认值。

已确认的地址形式：

```text
测试：http://aihub-test.citicsinfo.com/embedding/api/qwen3-embedding-local/v1/embeddings
生产：http://aihub.citicsinfo.com/embedding/api/qwen3-embedding-local/v1/embeddings
```

测试环境需确认能够访问 `10.63.36.231:80`，生产环境需确认能够访问 `10.121.148.231:80`。

## 配置

测试环境使用已确认的模型参数作为开发默认值；API Key没有默认值，缺少时应用启动失败。
生产部署必须通过环境变量显式覆盖模型地址、名称、版本和维度：

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `ORACLE_URL` | 本机开发占位地址 | Oracle连接地址 |
| `ORACLE_USERNAME` / `ORACLE_PASSWORD` | 开发占位值 | 数据库凭据 |
| `EMBEDDING_URL` | 测试网关完整地址 | 生产环境必须覆盖为生产网关地址 |
| `EMBEDDING_API_KEY` | 无 | 模型平台密钥，严禁提交到Git或输出到日志 |
| `EMBEDDING_MODEL_NAME` | `gen-studio-Qwen3-Embedding-8B` | 请求体`model`字段使用的模型名称 |
| `EMBEDDING_MODEL_VERSION` | `gen-studio-Qwen3-Embedding-8B-1024-v1` | 数据库存储和索引隔离使用的模型版本 |
| `EMBEDDING_DIMENSION` | `1024` | 网关实际返回且项目用于校验、存储和检索的向量维度 |
| `EMBEDDING_BATCH_SIZE` | `16` | 单次网关请求文本数量，当前平台上限为16 |
| `IMPORT_MAX_ROWS` | `1000` | 单次Excel最大数据行数 |
| `IMPORT_MAX_TOTAL_SAMPLES` | `10000` | 第一版历史样本总数上限 |
| `SEARCH_MAX_PARAGRAPH_LENGTH` | `2000` | 规范化段落最大字符数，不等同于Token数 |
| `SEARCH_MIN_SIMILARITY` | `0.60` | 最低召回相似度 |
| `SEARCH_HIGH_THRESHOLD` | `0.80` | 高可信类型得分阈值 |
| `SEARCH_CANDIDATE_THRESHOLD` | `0.55` | 候选类型得分阈值 |
| `SEARCH_STRONG_MATCH_THRESHOLD` | `0.80` | 强相似单条候选兜底阈值 |

`EMBEDDING_MODEL_NAME`用于实际网关请求；`EMBEDDING_MODEL_VERSION`用于向量兼容性隔离。
即使平台模型别名不变，只要底层模型发生变化，也必须使用新的版本标识并重新生成历史向量。

Qwen3-Embedding-8B不传`dimensions`时默认返回4096维，但本项目固定显式请求1024维。对第一版最多
1万条CPU精确检索而言，1024维可将网络响应、Oracle BLOB、JVM向量内存和点积计算量降为
4096维的四分之一。切换维度时必须同步更改`EMBEDDING_MODEL_VERSION`并重新生成全部历史向量。

## Excel格式与导入流程

第一张Sheet前两列表头必须完全一致：

| 合同段落 | 变更类型编码 |
|---|---|
| 历史段落A | TYPE01;TYPE02;TYPE03 |
| 历史段落B | TYPE05;TYPE09 |

导入接口为同步接口。处理顺序为：文件校验、文本规范化、Hash去重、冲突判断、网关向量化、
单事务入库、索引重载。任一模型调用失败时，本批数据不会入库。

第一版只部署一个Java实例。若以后部署多个实例，导入后的索引重载必须通知每个实例，不能只
依赖处理导入请求的当前实例。

相同Hash、相同类型、当前模型版本及维度一致时幂等跳过；相同类型但模型版本或维度变化时
更新原记录；无论记录是否生效，类型编码不同时都整批拒绝，避免静默覆盖历史标签。

## 接口

服务上下文：`/glxt-service-contract-change`

```http
POST /service/contract-change/samples/import
POST /service/contract-change/predict
POST /service/contract-change/index/reload
GET  /service/contract-change/index/status
POST /service/contract-compare/compare
POST /service/contract-compare/export
```

导入、预测、合同比对和直接导出接口必须携带：

```http
UserId: 实际操作人工号
```

该值只透传给模型平台，所有场景均不在日志中输出 `UserId`。索引重载和状态查询不调用模型，
因此不要求该请求头。

合同分析会在段落比对或内容提取后调用历史向量库识别业务变更类型，因此必须提供 `UserId`。
`analysisType` 控制双版本比较或单文件变更函提取，未传或传 `null` 时默认使用
`DOUBLE_VERSION`。

双版本比较请求体传入服务器上的修改前、修改后 DOCX 路径：

```json
{
  "analysisType": "DOUBLE_VERSION",
  "oldFileGetPath": "/合同目录/修改前.docx",
  "newFileGetPath": "/合同目录/修改后.docx",
  "resultMode": "SIMPLE"
}
```

单文件变更函提取请求体只需传入一份 DOCX 路径：

```json
{
  "analysisType": "CHANGE_DOCUMENT",
  "changeFileGetPath": "/合同目录/补充协议.docx"
}
```

`DOUBLE_VERSION` 必须提供 `oldFileGetPath`、`newFileGetPath`；`CHANGE_DOCUMENT` 必须提供
`changeFileGetPath`。非当前模式字段即使存在也不参与处理。非法枚举或缺少当前模式必填字段时
返回业务码 400。

双版本模式的 `resultMode` 可选值为 `SIMPLE`、`CONTEXT`，未传或传 `null` 时默认使用 `SIMPLE`。
`SIMPLE` 保持精简响应，只返回变化段落；`CONTEXT` 在每条变更中额外返回
`context.oldContent/context.newContent` 完整条款内容。修改条款的上下文不重复包含子条款，
整条新增或删除时上下文包含被折叠输出的完整子树。两种模式使用相同的条款匹配和差异结果。
单文件变更函已经在 `changedParagraphs.oldContent/newContent` 返回完整变更约定，因此忽略
`resultMode`，始终不返回 `context`。

```json
{
  "clauseNo": "第二条",
  "changeType": "MODIFIED",
  "changedParagraphs": [
    {
      "paragraphChangeType": "MODIFIED",
      "oldContent": "合同金额为100万元。",
      "newContent": "合同金额为120万元。",
      "changeDetails": [
        {"detailType": "REPLACED", "oldText": "100", "newText": "120"}
      ]
    }
  ],
  "context": {
    "oldContent": "第二条 合同金额\n合同金额为100万元。\n其他约定不变。",
    "newContent": "第二条 合同金额\n合同金额为120万元。\n其他约定不变。"
  }
}
```

上例中的 `context` 仅在 `CONTEXT` 模式出现；`SIMPLE` 模式的条款对象在
`changedParagraphs` 后结束。

服务从主备 SFTP 读取原始文件，使用 Aspose.Words 19.9 忽略格式、目录、页眉页脚和批注差异，
返回按合同条款聚合的 `ADDED`、`DELETED`、`MODIFIED`。只有编号或位置变化时不返回变更。
结构解析支持“第X编/篇/章/部分/节/条”、中文或阿拉伯数字括号编号、阿拉伯数字层级编号、
Word自动列表和标题样式；
表格按行列阅读顺序并入所在条款。
每条结果固定返回条款编号、标题、父条款编号、变更类型和 `changedParagraphs`。变化段落通过
`oldContent`、`newContent` 提供上下文，段落内的 `changeDetails` 返回具体 `INSERTED`、
`DELETED`、`REPLACED` 文字。两种模式均不返回内部节点标识、顺序、内容类型或高亮下标；
完整条款内容仅由 `CONTEXT` 模式的 `context` 提供。日期、百分比、千分位金额、数值区间和版本号会尽量作为完整语义片段返回。基础解析不写数据库，也不触发原合同解析落库流程；
解析完成后会调用历史向量识别补充业务变更类型。

`CHANGE_DOCUMENT` 继续复用 `changes` 和 `changedParagraphs`。每项额外返回
`sourceHeading`（函件中的原始变更标题）和 `targetClauseReference`（标题中提取的目标条款）：

```json
{
  "totalChanges": 1,
  "changes": [
    {
      "clauseNo": null,
      "clauseTitle": "《基金合同》第二条“合同金额”",
      "parentClauseNo": null,
      "changeType": "MODIFIED",
      "sourceHeading": "1、《基金合同》第二条“合同金额”约定如下：",
      "targetClauseReference": "《基金合同》第二条“合同金额”",
      "changedParagraphs": [
        {
          "paragraphChangeType": "MODIFIED",
          "oldContent": "合同金额为100万元。",
          "newContent": "合同金额为120万元。",
          "changeDetails": [
            {"detailType": "REPLACED", "oldText": "100", "newText": "120"}
          ]
        }
      ]
    }
  ],
  "warnings": []
}
```

变更函按正文阅读顺序解析可复制文字、Word列表、表格和文本框，不处理图片OCR、页眉页脚、
批注或签章。补充协议、征询意见函和协商函统一按正文格式识别，不要求调用方指定函件类型。
解析器识别以“阿拉伯数字+顿号或点号”开头，并包含《基金合同》和“约定如下/如下约定”或
新增、删除、修改等动作的标题；数字后允许出现“在”等连接文字。也识别包含“自本协议/函件（的）
变更执行日起”、《基金合同》和新增、删除或修改动作的标题。当前标题至下一标题之间形成一个
变更分段：

- 找到“内容变更如下”时，标记前内容为变更前内容；若变更后内容以双引号开头，则连续读取到
  配对结束引号，支持一个约定跨多个 Word 段落和同一标题下连续多个独立引号块。没有双引号时
  仍取标记后的下一个非空内容块。
- 标题包含“增加”或“新增”时，分段内容作为新增内容；以双引号开头时按配对引号限定边界。
- 标题包含“删除”时，分段内容作为删除前内容；以双引号开头时按配对引号限定边界。
- 标题同时包含删除和新增动作时按 `MODIFIED` 返回；有“内容变更如下”标记时正常拆分前后内容，
  没有明确分界时把正文放到标题最后一个动作对应的一侧，并在 `warnings` 提示人工复核。
- 标题已识别但内容不完整时仍返回该项，并在 `warnings` 中提示人工复核；没有可解析正文或
  没有识别到任何变更标题时返回业务码 400。

`/service/contract-compare/export` 使用与 `/compare` 相同的 JSON 请求体，直接下载
`contract-compare-result.xlsx`。主工作表按一个变化段落一行输出条款编号、标题、上级条款、
条款及段落变更类型、变更前后内容和具体变化；`CONTEXT` 模式额外增加“完整变更前条款”和
“完整变更后条款”两列。存在匹配或识别提示时额外生成“提示信息”工作表。

`CHANGE_DOCUMENT` 导出文件名为 `contract-change-extract-result.xlsx`，主工作表为“提取结果”，
按一个变更分段一行输出来源标题、目标条款、变更类型、变更前后内容、具体变化及预测字段，
固定为 13 列。
Controller 继续通过
`HttpServletResponse` 直接写入 Excel，方法返回值保持 `void`。

预测请求示例：

```http
POST /glxt-service-contract-change/service/contract-change/predict
UserId: employee-001
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

当集成识别状态为 `FAILED` 或 `SKIPPED_TOO_LONG` 时，`maxSimilarity` 省略，避免将无结果误解为
相似度 `0.0`；`NO_RELIABLE_MATCH` 仍保留实际计算得到的最高相似度（如有）。

合同比对集成识别采用“变化段落优先、上下文兜底”：新增和修改使用新段落，删除使用旧段落；
只有段落返回 `NO_RELIABLE_MATCH` 时才尝试条款上下文。上下文命中的类型最高标记为
`CANDIDATE`，向量服务失败不会丢失基础比对结果。

每个变化段落通过 `businessTypePrediction` 返回识别状态、输入范围、是否使用兜底、
匹配类型、模型版本、最高相似度、候选业务类型及参考样本。顶层
`predictionSummary` 汇总匹配、无可靠匹配、调用失败和超长跳过数量。预测结果不会自动反哺历史库。

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
$env:EMBEDDING_URL='http://aihub-test.citicsinfo.com/embedding/api/qwen3-embedding-local/v1/embeddings'
$env:EMBEDDING_API_KEY='***'
$env:EMBEDDING_MODEL_NAME='gen-studio-Qwen3-Embedding-8B'
$env:EMBEDDING_MODEL_VERSION='gen-studio-Qwen3-Embedding-8B-1024-v1'
$env:EMBEDDING_DIMENSION='1024'
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
`UserId`、合同正文或向量。

## 日志与敏感信息

- 记录导入、事务、索引、模型调用和预测的数量、状态及耗时。
- 合同段落只记录规范化文本SHA-256，不记录完整正文。
- 不记录API Key、`UserId`、请求体、模型响应体或向量。
- 不建议开启Mapper或RestTemplate DEBUG，避免输出CLOB、BLOB或模型请求信息。
