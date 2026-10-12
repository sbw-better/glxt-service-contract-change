# LiveBOS历史段落路径导入调用说明

POST `/glxt-service-contract-change/service/contract-change/samples/import`，Content-Type为`application/json`。
请求体只有两个必填字符串：`fileGetPath`是SFTP服务器上的xlsx文件路径，`userId`是实际操作人工号。
不使用multipart上传或UserId请求头。服务复用合同比对的主备SFTP配置下载文件，最大20MB。
上传字段必须提供与合同比对文件路径相同形式的服务器路径；若字段返回附件ID，应先解析成服务器路径。

```json
{"fileGetPath":"/data/samples.xlsx","userId":"T024818"}
```

## LiveBOS脚本

系统参数931应保存本导入接口的完整地址。无需Oracle拼装函数。
使用JSON序列化，避免路径中反斜杠、引号等字符破坏请求体。

```javascript
<%@ livebos language='javascript' %>

var e = ABS_LOADBEAN("com.https.HttpsHelp");
var url = ABS_SQLVALUE("select paramvalue from tsysparam where ID=931");
var userId = "" + $Login.User.UserID;
var fileGetPath = "" + O_PARAMETER.HTBDWJSC;

if (userId == "" || userId == "null" || userId == "undefined") {
    throw "用户工号不能为空";
}
if (fileGetPath == "" || fileGetPath == "null" || fileGetPath == "undefined") {
    throw "历史段落Excel文件路径不能为空";
}

var V_content = JSON.stringify({
    fileGetPath: fileGetPath,
    userId: userId
});

V_1 = e.httspost(
    url,
    V_content,
    "Content-Type:application/json",
    180000
);
```

该示例沿用现有HttpsHelp调用签名；LiveBOS实际运行环境及JSON.stringify能力尚未联调。
业务成功以响应JSON的`code=200`为准，不能只看HTTP成功。
180000毫秒沿用现有示例，模型升级会同步全量向量化；超时后先检查索引状态及有效样本，避免直接重复导入。
响应统计、标签冲突处理、模型升级与DB_COMMITTED恢复机制均不变。
