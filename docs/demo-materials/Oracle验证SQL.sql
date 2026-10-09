-- 合同段落变更类型识别服务 - 线上演示 Oracle 只读验证 SQL
-- 注意：只读查询，不展示真实合同正文；如需投屏，建议对 YWBW/GFBW 做脱敏或只截取前 30 字。

-- 1. 当前知识库样本规模、模型版本和维度分布
SELECT
    MODEL_VERSION,
    VECTOR_DIM,
    SFSX,
    COUNT(1) AS SAMPLE_COUNT
FROM TPIF_HTDLYB
GROUP BY MODEL_VERSION, VECTOR_DIM, SFSX
ORDER BY MODEL_VERSION, VECTOR_DIM, SFSX;

-- 2. 最近导入样本核验：Hash、类型、维度、模型版本、CLOB JSON 字符数
SELECT *
FROM (
    SELECT
        ID,
        TEXT_HASH,
        BGLX_CODES,
        VECTOR_DIM,
        MODEL_VERSION,
        DBMS_LOB.GETLENGTH(VECTOR_DATA) AS VECTOR_CHARS,
        SFSX,
        CREATE_TIME
    FROM TPIF_HTDLYB
    ORDER BY CREATE_TIME DESC, ID DESC
)
WHERE ROWNUM <= 10;

-- 3. 只检查空CLOB和无效维度；JSON字符数不固定，不能据长度判断向量正确性。
-- JSON格式、数字元素、实际维度和有限值由应用VectorCodec及归一化校验。
-- Oracle 11g不依赖数据库JSON函数；重载后关注/index/status的errorCount和DEGRADED。
SELECT
    ID,
    TEXT_HASH,
    VECTOR_DIM,
    DBMS_LOB.GETLENGTH(VECTOR_DATA) AS VECTOR_CHARS,
    MODEL_VERSION
FROM TPIF_HTDLYB
WHERE VECTOR_DATA IS NULL OR DBMS_LOB.GETLENGTH(VECTOR_DATA) = 0
   OR VECTOR_DIM IS NULL OR VECTOR_DIM <= 0;

-- 4. 当前模型版本、当前维度、生效样本数；无坏记录时与/index/status的sampleCount对齐
SELECT COUNT(1) AS ACTIVE_INDEX_SAMPLE_COUNT
FROM TPIF_HTDLYB
WHERE MODEL_VERSION = 'gen-studio-Qwen3-Embedding-8B-1024-v1'
  AND VECTOR_DIM = 1024
  AND SFSX = 1;

-- 5. 按类型编码粗略查看样本覆盖；BGLX_CODES 为分号分隔多标签，此查询仅用于演示观察
SELECT
    BGLX_CODES,
    COUNT(1) AS SAMPLE_COUNT
FROM TPIF_HTDLYB
WHERE MODEL_VERSION = 'gen-studio-Qwen3-Embedding-8B-1024-v1'
  AND VECTOR_DIM = 1024
  AND SFSX = 1
GROUP BY BGLX_CODES
ORDER BY SAMPLE_COUNT DESC;

-- 6. 按 Hash 精确命中排查某段落是否已入库：
--    先在服务侧或工具中按同一规范化逻辑计算 SHA-256，再替换下面的占位值。
SELECT
    ID,
    TEXT_HASH,
    BGLX_CODES,
    VECTOR_DIM,
    MODEL_VERSION,
    SFSX,
    CREATE_TIME
FROM TPIF_HTDLYB
WHERE TEXT_HASH = '<替换为规范化段落SHA-256>';
