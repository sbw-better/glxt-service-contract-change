-- Oracle 11g / LiveBOS 3.9.2 合同比对请求报文函数。
--
-- 本函数只校验参数并返回 /service/contract-compare/compare 所需的 JSON 请求体，
-- 不发起 HTTP 请求。LiveBOS 继续通过 com.https.HttpsHelp.httspost 调用 Java 服务。

CREATE OR REPLACE FUNCTION FN_CONTRACT_COMPARE_REQUEST(
    I_INSTID               IN NUMBER,
    I_USERID               IN VARCHAR2,
    I_ANALYSIS_TYPE        IN VARCHAR2,
    I_OLD_FILE_GET_PATH    IN VARCHAR2 DEFAULT NULL,
    I_NEW_FILE_GET_PATH    IN VARCHAR2 DEFAULT NULL,
    I_CHANGE_FILE_GET_PATH IN VARCHAR2 DEFAULT NULL
) RETURN CLOB
AS
    V_JSON          CLOB;
    V_ANALYSIS_TYPE VARCHAR2(30);

    -- Oracle 11g 没有 JSON_OBJECT，按 JSON 字符串规则转义输入值。
    FUNCTION JSON_QUOTE(I_VALUE IN VARCHAR2) RETURN VARCHAR2
    IS
        V_RESULT VARCHAR2(32767) := '"';
        V_CHAR   VARCHAR2(1);
        V_CODE   PLS_INTEGER;
    BEGIN
        IF I_VALUE IS NULL THEN
            RETURN 'null';
        END IF;

        FOR I IN 1 .. LENGTH(I_VALUE) LOOP
            V_CHAR := SUBSTR(I_VALUE, I, 1);
            V_CODE := ASCII(V_CHAR);
            CASE V_CHAR
                WHEN '"' THEN V_RESULT := V_RESULT || '\"';
                WHEN '\' THEN V_RESULT := V_RESULT || '\\';
                WHEN CHR(8) THEN V_RESULT := V_RESULT || '\b';
                WHEN CHR(9) THEN V_RESULT := V_RESULT || '\t';
                WHEN CHR(10) THEN V_RESULT := V_RESULT || '\n';
                WHEN CHR(12) THEN V_RESULT := V_RESULT || '\f';
                WHEN CHR(13) THEN V_RESULT := V_RESULT || '\r';
                ELSE
                    IF V_CODE < 32 THEN
                        V_RESULT := V_RESULT || '\u' || TO_CHAR(V_CODE, 'FM0000');
                    ELSE
                        V_RESULT := V_RESULT || V_CHAR;
                    END IF;
            END CASE;
        END LOOP;
        RETURN V_RESULT || '"';
    END JSON_QUOTE;
BEGIN
    IF I_INSTID IS NULL OR I_INSTID <= 0 OR I_INSTID <> TRUNC(I_INSTID) THEN
        RAISE_APPLICATION_ERROR(-20001, 'INSTID必须是大于0的整数');
    END IF;
    IF I_USERID IS NULL OR TRIM(I_USERID) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20002, 'USERID不能为空');
    END IF;
    IF LENGTH(TRIM(I_USERID)) > 100 THEN
        RAISE_APPLICATION_ERROR(-20003, 'USERID不能超过100个字符');
    END IF;

    -- 同时兼容LiveBOS字典值1/2和Java接口枚举值。
    V_ANALYSIS_TYPE := UPPER(TRIM(I_ANALYSIS_TYPE));
    IF V_ANALYSIS_TYPE IN ('1', 'DOUBLE_VERSION') THEN
        V_ANALYSIS_TYPE := 'DOUBLE_VERSION';
    ELSIF V_ANALYSIS_TYPE IN ('2', 'CHANGE_DOCUMENT') THEN
        V_ANALYSIS_TYPE := 'CHANGE_DOCUMENT';
    ELSE
        RAISE_APPLICATION_ERROR(-20004,
                '文档比对类型只能是1/DOUBLE_VERSION或2/CHANGE_DOCUMENT');
    END IF;

    IF V_ANALYSIS_TYPE = 'DOUBLE_VERSION' THEN
        IF I_OLD_FILE_GET_PATH IS NULL OR TRIM(I_OLD_FILE_GET_PATH) IS NULL
                OR I_NEW_FILE_GET_PATH IS NULL OR TRIM(I_NEW_FILE_GET_PATH) IS NULL THEN
            RAISE_APPLICATION_ERROR(-20005,
                    '双版本比对必须提供修改前和修改后文件路径');
        END IF;
        IF LENGTH(I_OLD_FILE_GET_PATH) > 1000 OR LENGTH(I_NEW_FILE_GET_PATH) > 1000 THEN
            RAISE_APPLICATION_ERROR(-20006, '合同文件路径不能超过1000个字符');
        END IF;

        V_JSON := '{"instId":' || TO_CHAR(TRUNC(I_INSTID), 'TM9')
                || ',"userId":' || JSON_QUOTE(TRIM(I_USERID))
                || ',"analysisType":"DOUBLE_VERSION"'
                || ',"oldFileGetPath":' || JSON_QUOTE(I_OLD_FILE_GET_PATH)
                || ',"newFileGetPath":' || JSON_QUOTE(I_NEW_FILE_GET_PATH)
                || '}';
    ELSE
        IF I_CHANGE_FILE_GET_PATH IS NULL OR TRIM(I_CHANGE_FILE_GET_PATH) IS NULL THEN
            RAISE_APPLICATION_ERROR(-20007,
                    '单文件变更分析必须提供变更文件路径');
        END IF;
        IF LENGTH(I_CHANGE_FILE_GET_PATH) > 1000 THEN
            RAISE_APPLICATION_ERROR(-20006, '合同文件路径不能超过1000个字符');
        END IF;

        V_JSON := '{"instId":' || TO_CHAR(TRUNC(I_INSTID), 'TM9')
                || ',"userId":' || JSON_QUOTE(TRIM(I_USERID))
                || ',"analysisType":"CHANGE_DOCUMENT"'
                || ',"changeFileGetPath":' || JSON_QUOTE(I_CHANGE_FILE_GET_PATH)
                || '}';
    END IF;

    RETURN V_JSON;
END FN_CONTRACT_COMPARE_REQUEST;
/

SHOW ERRORS FUNCTION FN_CONTRACT_COMPARE_REQUEST;

-- 双版本报文示例：
-- SELECT FN_CONTRACT_COMPARE_REQUEST(
--     10001,
--     'employee-001',
--     'DOUBLE_VERSION',
--     '/tomcat/ABS_DOCUMENT/zxtg/TEST/tqcy/before2.docx',
--     '/tomcat/ABS_DOCUMENT/zxtg/TEST/tqcy/after2.docx',
--     NULL
-- ) AS REQUEST_JSON
-- FROM DUAL;

-- 单文件报文示例：
-- SELECT FN_CONTRACT_COMPARE_REQUEST(
--     10001,
--     'employee-001',
--     'CHANGE_DOCUMENT',
--     NULL,
--     NULL,
--     '/tomcat/ABS_DOCUMENT/zxtg/TEST/tqcy/change.docx'
-- ) AS REQUEST_JSON
-- FROM DUAL;
