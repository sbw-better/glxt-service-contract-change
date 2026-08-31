import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const root = "D:/projects/glxt-service-contract-change/docs/demo-materials/contract-sample-tests";
const buildDir = `${root}/build`;
const dataset = JSON.parse(await fs.readFile(`${buildDir}/contract_test_dataset.json`, "utf8"));

const theme = {
  header: "#123C69",
  subHeader: "#E8F1F8",
  border: "#CFD8E3",
  note: "#FFF7E6",
  ok: "#E8F5E9",
  warn: "#FFF3CD",
  bad: "#FDEAEA",
  text: "#1F2933",
};

function col(n) {
  let s = "";
  while (n > 0) {
    const m = (n - 1) % 26;
    s = String.fromCharCode(65 + m) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

function styleSheet(sheet, cols, rows) {
  sheet.showGridLines = false;
  sheet.freezePanes.freezeRows(1);
  const header = sheet.getRangeByIndexes(0, 0, 1, cols);
  header.format = {
    fill: theme.header,
    font: { bold: true, color: "#FFFFFF" },
    wrapText: true,
  };
  header.format.rowHeightPx = 30;
  const body = sheet.getRangeByIndexes(1, 0, Math.max(1, rows - 1), cols);
  body.format = {
    font: { color: theme.text },
    wrapText: true,
    borders: {
      insideHorizontal: { style: "thin", color: theme.border },
      bottom: { style: "thin", color: theme.border },
    },
  };
}

function setWidths(sheet, widths) {
  widths.forEach((width, index) => {
    sheet.getRange(`${col(index + 1)}:${col(index + 1)}`).format.columnWidthPx = width;
  });
}

async function saveWorkbook(fileName, sheets) {
  const workbook = Workbook.create();
  for (const spec of sheets) {
    const sheet = workbook.worksheets.add(spec.name);
    sheet.getRangeByIndexes(0, 0, spec.values.length, spec.values[0].length).values = spec.values;
    styleSheet(sheet, spec.values[0].length, spec.values.length);
    setWidths(sheet, spec.widths);
    if (spec.tableName) {
      const end = `${col(spec.values[0].length)}${spec.values.length}`;
      const table = sheet.tables.add(`A1:${end}`, true, spec.tableName);
      table.style = "TableStyleMedium2";
      table.showFilterButton = true;
    }
  }
  const inspect = await workbook.inspect({
    kind: "sheet,table",
    tableMaxRows: 3,
    tableMaxCols: 6,
    maxChars: 2500,
  });
  console.log(`INSPECT ${fileName}`);
  console.log(inspect.ndjson);
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 100 },
    summary: `formula error scan ${fileName}`,
    maxChars: 1500,
  });
  console.log(errors.ndjson);
  for (const spec of sheets) {
    const png = await workbook.render({
      sheetName: spec.name,
      range: `A1:${col(spec.values[0].length)}${Math.min(spec.values.length, 28)}`,
      scale: 1,
      format: "png",
    });
    await fs.writeFile(`${buildDir}/${fileName}.${spec.name}.preview.png`, new Uint8Array(await png.arrayBuffer()));
  }
  const output = await SpreadsheetFile.exportXlsx(workbook);
  await output.save(`${root}/${fileName}`);
}

function importSheetRows(rows) {
  return [
    ["合同段落", "变更类型编码"],
    ...rows.map((row) => [row.paragraph, row.codes]),
  ];
}

function scenarioRows() {
  return [
    ["文件/请求", "覆盖场景", "建议演示方式", "预期关注点"],
    ["合同样例-测试导入样本-正常-920行.xlsx", "中等批量正常导入", "用于导入链路和耗时验证", "920条段落唯一、无文件内标签冲突；标签仅用于接口验证"],
    ["合同样例-测试导入样本-正常-990行.xlsx", "接近1000行上限的正常导入", "用于容量边界前的成功场景", "990条段落唯一、无文件内标签冲突；不要用于预测准确性判断"],
    ["合同样例-预测验证知识库.xlsx", "EXACT、HIGH、CANDIDATE、强相似兜底的受控历史样本", "执行预测前必须先导入", "41条样本按主题聚类并设计明确投票结构"],
    ["合同样例-测试导入样本-重复.xlsx", "同段落同编码重复", "正常样本导入后再导入", "重复样本应幂等跳过，不应新增重复历史经验"],
    ["合同样例-测试导入样本-冲突.xlsx", "同段落不同编码冲突", "作为错误场景演示", "整批拒绝，避免悄悄覆盖历史标签"],
    ["合同样例-测试导入样本-分隔符与去重.xlsx", "中文分号、中文逗号、重复编码", "可单独导入", "编码规范化为去重排序后的英文分号"],
    ["合同样例-测试导入样本-错误表头.xlsx", "第一行表头不符合要求", "作为错误场景演示", "第一张Sheet前两列表头必须是合同段落、变更类型编码"],
    ["合同样例-测试导入样本-空值与非法编码.xlsx", "空段落、空编码、编码含空格、单编码过长", "作为错误场景演示", "逐行返回校验错误"],
    ["合同样例-测试导入样本-超长段落.xlsx", "段落超过2000字符", "作为边界场景演示", "明确拒绝，不自动截断"],
    ["合同样例-测试导入样本-超过1000行.xlsx", "单Excel超过1000行", "只做边界验证，谨慎现场调用", "第1001条后触发最大行数限制"],
    ["合同样例-预测用例-校准版.xlsx", "EXACT、单标签HIGH、多标签HIGH、CANDIDATE、强相似兜底、无可靠匹配和参数错误", "配合专用预测知识库及Postman使用", "每条用例列出matchType、类型、等级、support和score预期；替代旧版预测用例"],
    ["合同样例-多场景测试.postman_collection.json", "接口请求集合", "导入Postman后按文件夹顺序执行", "baseUrl和UserId放在环境变量"],
  ];
}

const normalRows = dataset.normalRows.map((row) => ({
  paragraph: row.paragraph,
  codes: row.codes,
  source: row.source,
  scenario: row.scenario,
}));

const normalRows920 = dataset.normalRows920.map((row) => ({
  paragraph: row.paragraph,
  codes: row.codes,
  source: row.source,
  scenario: row.scenario,
}));

await saveWorkbook("合同样例-测试导入样本-正常-920行.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(normalRows920),
    widths: [760, 150],
    tableName: "NormalImportSamples920",
  },
  {
    name: "说明",
    values: [
      ["项目", "内容"],
      ["用途", "用于中等批量导入、同步Embedding、Oracle落库和索引刷新验证。"],
      ["结构检查", "920条段落文本唯一，不包含同段落不同编码冲突。"],
      ["重要限制", "变更类型编码为规则验证标签，不是人工业务标注，不用于判断预测准确率。"],
    ],
    widths: [180, 720],
    tableName: "NormalImportNotes920",
  },
]);

await saveWorkbook("合同样例-测试导入样本-正常-990行.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(normalRows),
    widths: [760, 150],
    tableName: "NormalImportSamples",
  },
  {
    name: "说明",
    values: [
      ["项目", "内容"],
      ["用途", "用于接近单Excel 1000行限制的成功导入、同步Embedding和索引刷新验证。"],
      ["数据来源", `从样例合同抽取 ${dataset.baseParagraphCount} 条可用段落，并补充唯一性验证改写样本。`],
      ["结构检查", "990条段落文本唯一，不包含同段落不同编码冲突。"],
      ["重要限制", "编码为规则验证标签，不代表真实业务人工标注结果；不要用于预测准确性判断。"],
    ],
    widths: [180, 720],
    tableName: "NormalImportNotes",
  },
]);

await saveWorkbook("合同样例-测试导入样本-重复.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.duplicateRows),
    widths: [760, 150],
    tableName: "DuplicateImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-冲突.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.conflictRows),
    widths: [760, 150],
    tableName: "ConflictImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-分隔符与去重.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.separatorRows),
    widths: [760, 150],
    tableName: "SeparatorImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-错误表头.xlsx", [
  {
    name: "导入样本",
    values: [
      ["段落内容", "类型编码"],
      [dataset.normalRows[13].paragraph, "20;25"],
      [dataset.normalRows[14].paragraph, "28"],
    ],
    widths: [760, 150],
    tableName: "BadHeaderImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-空值与非法编码.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.invalidRows),
    widths: [760, 220],
    tableName: "InvalidImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-超长段落.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.longRows),
    widths: [760, 150],
    tableName: "TooLongImportSamples",
  },
]);

await saveWorkbook("合同样例-测试导入样本-超过1000行.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.overLimitRows),
    widths: [760, 150],
    tableName: "OverLimitImportSamples",
  },
]);

await saveWorkbook("合同样例-预测验证知识库.xlsx", [
  {
    name: "导入样本",
    values: importSheetRows(dataset.predictionKnowledgeRows),
    widths: [820, 180],
    tableName: "PredictionValidationKnowledge",
  },
  {
    name: "样本设计",
    values: [
      ["样本簇", "样本数", "标签结构", "预期作用"],
      ["EXACT", 1, "20", "预测请求与历史段落完全相同，直接Hash命中"],
      ["HIGH_SINGLE_43", 10, "全部为43", "Top近邻一致支持43，预期score>=0.80且support>=2"],
      ["HIGH_MULTI_20_25_28", 10, "全部为20;25;28", "验证多个类型同时HIGH，多个score之和可大于1"],
      ["CANDIDATE_25_VS_28", 10, "6条为25、4条为28", "类型25预期达到0.55但低于0.80，返回CANDIDATE"],
      ["FALLBACK_DIVERSE", 10, "10条使用不同编码", "正常投票不达标，第一条57与查询最接近并触发强相似兜底"],
      ["使用条件", 41, "专用规则验证标签", "预测前先导入；严格验收建议使用干净测试库，避免旧随机标签样本干扰Top10"],
    ],
    widths: [280, 120, 260, 680],
    tableName: "PredictionKnowledgeDesign",
  },
]);

await saveWorkbook("合同样例-预测用例-校准版.xlsx", [
  {
    name: "预测用例",
    values: [
      ["用例名称", "paragraph", "预期关注点"],
      ...dataset.predictionCases.map((item) => [
        item.name,
        item.assertion === "ERROR_TOO_LONG"
          ? "完整2105字符请求体已配置在Postman同名请求中，本表不展开显示。"
          : item.paragraph,
        item.expectedFocus,
      ]),
    ],
    widths: [280, 760, 460],
    tableName: "PredictionCases",
  },
  {
    name: "预期结果",
    values: [
      ["用例名称", "matchType", "预期类型", "level", "supportCount", "score", "maxSimilarity", "前置条件"],
      ...dataset.predictionCases.map((item) => [
        item.name,
        item.expectedMatchType,
        item.expectedCodes,
        item.expectedLevel,
        item.expectedSupport,
        item.expectedScore,
        item.expectedSimilarity,
        item.precondition,
      ]),
    ],
    widths: [300, 180, 180, 160, 220, 220, 220, 620],
    tableName: "PredictionExpectedResults",
  },
  {
    name: "投票设计",
    values: [
      ["场景", "历史样本构造", "规则条件", "预期判断"],
      ["EXACT", "1条完全相同文本，类型20", "Hash相同", "EXACT；score=1；不调用Embedding"],
      ["单标签HIGH", "主题A共10条，全部类型43", "score>=0.80且support>=2", "43/HIGH"],
      ["多标签HIGH", "主题B共10条，全部类型20;25;28", "每个类型score>=0.80且support>=2", "20、25、28均为HIGH"],
      ["CANDIDATE", "主题C共10条：6条25、4条28", "25得分应在0.55到0.80之间", "25/CANDIDATE"],
      ["强相似兜底", "主题D共10条且标签互不相同，第一条类型57与查询最接近", "无类型达到0.55且第一名similarity>=0.80", "57/CANDIDATE，score<0.55，support=1"],
      ["无可靠匹配", "查询文本跨领域且不在合同知识库中", "无可靠候选或第一名未达到兜底条件", "NO_RELIABLE_MATCH，changeTypes为空"],
    ],
    widths: [220, 620, 520, 460],
    tableName: "PredictionVoteDesign",
  },
  {
    name: "演示顺序",
    values: scenarioRows(),
    widths: [360, 280, 320, 420],
    tableName: "ScenarioGuide",
  },
]);

function body(paragraph) {
  return {
    mode: "raw",
    raw: JSON.stringify({ paragraph }, null, 2),
    options: { raw: { language: "json" } },
  };
}

function testEvent(lines) {
  return [{
    listen: "test",
    script: {
      type: "text/javascript",
      exec: lines,
    },
  }];
}

function predictTestLines(item) {
  const common = [
    "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
    "const json = pm.response.json();",
  ];
  if (item.assertion === "ERROR_EMPTY") {
    return [...common,
      "pm.test('空段落返回业务码400', function () { pm.expect(json.code).to.eql(400); });",
      "pm.test('错误信息明确', function () { pm.expect(json.message).to.include('合同段落不能为空'); });",
    ];
  }
  if (item.assertion === "ERROR_TOO_LONG") {
    return [...common,
      "pm.test('超长段落返回业务码400', function () { pm.expect(json.code).to.eql(400); });",
      "pm.test('错误信息明确', function () { pm.expect(json.message).to.include('合同段落不能超过2000字符'); });",
    ];
  }
  const success = [...common,
    "pm.test('业务码为0', function () { pm.expect(json.code).to.eql(0); });",
    "const data = json.data;",
    "const findType = function (code) { return (data.changeTypes || []).find(function (item) { return item.code === code; }); };",
  ];
  if (item.assertion === "EXACT") {
    return [...success,
      "pm.test('精确命中', function () { pm.expect(data.matchType).to.eql('EXACT'); pm.expect(data.maxSimilarity).to.eql(1); });",
      "pm.test('类型20为HIGH且score=1', function () { const type = findType('20'); pm.expect(type).to.exist; pm.expect(type.level).to.eql('HIGH'); pm.expect(type.score).to.eql(1); });",
    ];
  }
  if (item.assertion === "HIGH_SINGLE") {
    return [...success,
      "pm.test('语义匹配', function () { pm.expect(data.matchType).to.eql('SEMANTIC'); });",
      "pm.test('类型43达到HIGH', function () { const type = findType('43'); pm.expect(type).to.exist; pm.expect(type.level).to.eql('HIGH'); pm.expect(type.score).to.be.at.least(0.80); pm.expect(type.supportCount).to.be.at.least(2); });",
    ];
  }
  if (item.assertion === "HIGH_MULTI") {
    return [...success,
      "pm.test('语义匹配', function () { pm.expect(data.matchType).to.eql('SEMANTIC'); });",
      "pm.test('20、25、28全部达到HIGH', function () { ['20','25','28'].forEach(function (code) { const type = findType(code); pm.expect(type, code).to.exist; pm.expect(type.level, code).to.eql('HIGH'); pm.expect(type.score, code).to.be.at.least(0.80); pm.expect(type.supportCount, code).to.be.at.least(2); }); });",
    ];
  }
  if (item.assertion === "CANDIDATE") {
    return [...success,
      "pm.test('语义匹配', function () { pm.expect(data.matchType).to.eql('SEMANTIC'); });",
      "pm.test('类型25为候选', function () { const type = findType('25'); pm.expect(type).to.exist; pm.expect(type.level).to.eql('CANDIDATE'); pm.expect(type.score).to.be.at.least(0.55); pm.expect(type.score).to.be.below(0.80); });",
    ];
  }
  if (item.assertion === "FALLBACK") {
    return [...success,
      "pm.test('强相似兜底仍属于SEMANTIC', function () { pm.expect(data.matchType).to.eql('SEMANTIC'); pm.expect(data.maxSimilarity).to.be.at.least(0.80); });",
      "pm.test('类型57以低分单条证据候选返回', function () { const type = findType('57'); pm.expect(type).to.exist; pm.expect(type.level).to.eql('CANDIDATE'); pm.expect(type.score).to.be.below(0.55); pm.expect(type.supportCount).to.eql(1); });",
    ];
  }
  if (item.assertion === "NO_MATCH") {
    return [...success,
      "pm.test('无可靠匹配', function () { pm.expect(data.matchType).to.eql('NO_RELIABLE_MATCH'); pm.expect(data.changeTypes).to.be.an('array').that.is.empty; });",
    ];
  }
  return success;
}

function predictRequest(item) {
  return {
    name: item.name,
    request: {
      method: "POST",
      header: [
        { key: "UserId", value: "{{userId}}" },
        { key: "Content-Type", value: "application/json" },
      ],
      body: body(item.paragraph),
      url: {
        raw: "{{baseUrl}}/service/contract-change/predict",
        host: ["{{baseUrl}}"],
        path: ["service", "contract-change", "predict"],
      },
      description: item.expectedFocus,
    },
    event: testEvent(predictTestLines(item)),
  };
}

function uploadTestLines(expectation) {
  const lines = [
    "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
    "const json = pm.response.json();",
  ];
  if (expectation.kind === "missingUser") {
    return [...lines,
      "pm.test('缺少UserId返回业务码400', function () { pm.expect(json.code).to.eql(400); pm.expect(json.message).to.include('UserId'); });",
    ];
  }
  if (expectation.kind === "validationFailure") {
    return [...lines,
      "pm.test('导入校验失败', function () { pm.expect(json.code).to.eql(400); pm.expect(json.data.success).to.eql(false); pm.expect(json.data.errors.length).to.be.above(0); });",
    ];
  }
  const successLines = [...lines,
    "pm.test('导入成功', function () { pm.expect(json.code).to.eql(0); pm.expect(json.data.success).to.eql(true); pm.expect(json.data.indexReloaded).to.eql(true); });",
  ];
  if (expectation.totalRows) {
    successLines.push(`pm.test('读取${expectation.totalRows}条数据', function () { pm.expect(json.data.totalRows).to.eql(${expectation.totalRows}); });`);
  }
  if (expectation.kind === "duplicate") {
    successLines.push("pm.test('存在幂等跳过记录', function () { pm.expect(json.data.skipped).to.be.at.least(1); });");
  }
  return successLines;
}

function uploadRequest(name, fileName, desc, expectation = { kind: "success" }, withUserId = true) {
  const headers = withUserId ? [{ key: "UserId", value: "{{userId}}" }] : [];
  return {
    name,
    request: {
      method: "POST",
      header: headers,
      body: {
        mode: "formdata",
        formdata: [
          {
            key: "file",
            type: "file",
            src: `${root}/${fileName}`,
          },
        ],
      },
      url: {
        raw: "{{baseUrl}}/service/contract-change/samples/import",
        host: ["{{baseUrl}}"],
        path: ["service", "contract-change", "samples", "import"],
      },
      description: desc,
    },
    event: testEvent(uploadTestLines(expectation)),
  };
}

const collection = {
  info: {
    name: "合同段落变更类型识别服务-合同样例多场景测试",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    description: "基于合同样例生成的导入与预测测试集合。预测场景使用41条专用受控历史样本，并配置Postman自动断言。导入和预测必须携带UserId；baseUrl建议设置为http://localhost:8080/glxt-service-contract-change。",
  },
  item: [
    {
      name: "00 索引状态",
      item: [
        {
          name: "查询索引状态",
          request: {
            method: "GET",
            header: [],
            url: {
              raw: "{{baseUrl}}/service/contract-change/index/status",
              host: ["{{baseUrl}}"],
              path: ["service", "contract-change", "index", "status"],
            },
            description: "演示前先确认JVM内存索引状态、样本数、模型版本和维度。",
          },
          event: testEvent([
            "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
            "const json = pm.response.json();",
            "pm.test('状态接口业务码为0', function () { pm.expect(json.code).to.eql(0); pm.expect(json.data).to.be.an('object'); });",
          ]),
        },
      ],
    },
    {
      name: "01 Excel导入",
      item: [
        uploadRequest("正常导入-920行", "合同样例-测试导入样本-正常-920行.xlsx", "中等批量成功导入；用于验证同步Embedding、Oracle落库和索引刷新，不用于准确率判断。", { kind: "success", totalRows: 920 }),
        uploadRequest("正常导入-990行", "合同样例-测试导入样本-正常-990行.xlsx", "接近1000行上限的成功导入；990条段落已保证唯一，无文件内标签冲突。", { kind: "success", totalRows: 990 }),
        uploadRequest("重复导入-同段落同编码", "合同样例-测试导入样本-重复.xlsx", "验证相同Hash和相同编码的幂等跳过。", { kind: "duplicate", totalRows: 5 }),
        uploadRequest("冲突导入-同段落不同编码", "合同样例-测试导入样本-冲突.xlsx", "验证标签冲突时整批拒绝，避免覆盖历史知识。", { kind: "validationFailure" }),
        uploadRequest("分隔符与去重导入", "合同样例-测试导入样本-分隔符与去重.xlsx", "验证中文分号、中文逗号和重复编码会被规范化。", { kind: "success", totalRows: 3 }),
        uploadRequest("错误表头导入", "合同样例-测试导入样本-错误表头.xlsx", "验证第一张Sheet前两列表头必须完全匹配。", { kind: "validationFailure" }),
        uploadRequest("空值与非法编码导入", "合同样例-测试导入样本-空值与非法编码.xlsx", "验证逐行校验错误返回。", { kind: "validationFailure" }),
        uploadRequest("超长段落导入", "合同样例-测试导入样本-超长段落.xlsx", "验证超过2000字符明确拒绝。", { kind: "validationFailure" }),
        uploadRequest("超过1000行导入", "合同样例-测试导入样本-超过1000行.xlsx", "验证单Excel最多1000行；第1001条触发最大行数限制。", { kind: "validationFailure" }),
        uploadRequest("缺少UserId导入", "合同样例-测试导入样本-重复.xlsx", "验证UserId请求头不能为空。", { kind: "missingUser" }, false),
      ],
    },
    {
      name: "02 预测前置数据",
      item: [
        uploadRequest("导入预测验证知识库-41行", "合同样例-预测验证知识库.xlsx", "预测请求执行前先导入。包含4组受控语义簇，用于稳定触发HIGH、CANDIDATE和强相似兜底。", { kind: "success", totalRows: 41 }),
        {
          name: "确认预测索引已加载",
          request: {
            method: "GET",
            header: [],
            url: {
              raw: "{{baseUrl}}/service/contract-change/index/status",
              host: ["{{baseUrl}}"],
              path: ["service", "contract-change", "index", "status"],
            },
            description: "确认状态为READY且样本数不为0；严格验收建议使用干净测试库。",
          },
          event: testEvent([
            "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
            "const json = pm.response.json();",
            "pm.test('索引READY且有样本', function () { pm.expect(json.code).to.eql(0); pm.expect(json.data.status).to.eql('READY'); pm.expect(json.data.sampleCount).to.be.above(0); });",
          ]),
        },
      ],
    },
    {
      name: "03 预测识别",
      item: [
        ...dataset.predictionCases.map(predictRequest),
        {
          name: "ERROR_MISSING_USERID_缺少UserId",
          request: {
            method: "POST",
            header: [{ key: "Content-Type", value: "application/json" }],
            body: body(dataset.predictionCases[0].paragraph),
            url: {
              raw: "{{baseUrl}}/service/contract-change/predict",
              host: ["{{baseUrl}}"],
              path: ["service", "contract-change", "predict"],
            },
            description: "验证预测接口必须携带UserId。",
          },
          event: testEvent([
            "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
            "const json = pm.response.json();",
            "pm.test('缺少UserId返回业务码400', function () { pm.expect(json.code).to.eql(400); pm.expect(json.message).to.include('UserId'); });",
          ]),
        },
      ],
    },
    {
      name: "04 索引维护",
      item: [
        {
          name: "手工重载索引",
          request: {
            method: "POST",
            header: [],
            url: {
              raw: "{{baseUrl}}/service/contract-change/index/reload",
              host: ["{{baseUrl}}"],
              path: ["service", "contract-change", "index", "reload"],
            },
            description: "从Oracle重新加载当前模型版本、当前维度、SFSX=1的历史段落向量。",
          },
          event: testEvent([
            "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
            "const json = pm.response.json();",
            "pm.test('重载成功', function () { pm.expect(json.code).to.eql(0); pm.expect(json.data.status).to.be.oneOf(['READY','EMPTY']); });",
          ]),
        },
        {
          name: "重载后查询索引状态",
          request: {
            method: "GET",
            header: [],
            url: {
              raw: "{{baseUrl}}/service/contract-change/index/status",
              host: ["{{baseUrl}}"],
              path: ["service", "contract-change", "index", "status"],
            },
            description: "确认重载后的状态和样本数。",
          },
          event: testEvent([
            "pm.test('HTTP状态为200', function () { pm.response.to.have.status(200); });",
            "const json = pm.response.json();",
            "pm.test('状态查询成功', function () { pm.expect(json.code).to.eql(0); pm.expect(json.data).to.be.an('object'); });",
          ]),
        },
      ],
    },
  ],
};

const environment = {
  name: "合同段落变更类型识别服务-本地测试环境",
  values: [
    {
      key: "baseUrl",
      value: "http://localhost:8080/glxt-service-contract-change",
      type: "default",
      enabled: true,
    },
    {
      key: "userId",
      value: "employee-001",
      type: "default",
      enabled: true,
    },
  ],
};

await fs.writeFile(`${root}/合同样例-多场景测试.postman_collection.json`, JSON.stringify(collection, null, 2), "utf8");
await fs.writeFile(`${root}/合同样例-Postman环境.postman_environment.json`, JSON.stringify(environment, null, 2), "utf8");

const readme = `# 合同样例测试数据与Postman报文说明

本目录下的测试数据基于用户提供的合同样例和受控语义簇生成。大批量导入文件用于接口、容量和异常路径验证；预测验证知识库专门用于验证投票规则。所有编码都是规则验证标签，不代表真实人工业务标注结论。

## 建议演示顺序

1. Postman导入环境文件和Collection，确认baseUrl与userId。
2. 调用“查询索引状态”，先看当前样本数和索引状态。
3. 容量验证可上传“正常导入-920行”或“正常导入-990行”；两份文件均已消除文件内冲突。
4. 执行“02 预测前置数据/导入预测验证知识库-41行”，再确认索引READY。
5. 按顺序执行EXACT、单标签HIGH、多标签HIGH、CANDIDATE、强相似兜底和NO_RELIABLE_MATCH；每个请求都有自动断言。
6. 上传重复、冲突、空值、超长、超过1000行等文件，演示边界和校验。
7. 调用“手工重载索引”和“重载后查询索引状态”，说明运维能力。

## 文件用途

${scenarioRows().slice(1).map((r) => `- ${r[0]}：${r[1]}。${r[3]}`).join("\n")}

## 现场提示

- 测试环境真实调用模型网关，920或990行批量导入可能耗时较长；现场演示预测时只需优先导入41行专用预测知识库。
- 严格验证语义投票结果时，建议使用干净测试库，或确认旧的随机标签样本不会进入相同主题的Top 10。专用知识库已经按每组10条近邻设计，以降低其他样本干扰。
- Postman请求名称不会影响后端判断；是否通过以Tests页自动断言为准。HIGH同时要求score>=0.80和supportCount>=2。
- 原“合同样例-预测用例.xlsx”使用随机标签历史数据，已经废弃；请使用“合同样例-预测用例-校准版.xlsx”。
- 缺少真实人工测试集时，不建议把演示结果表述为准确率结论；更稳妥的说法是“接口闭环、规则边界和可解释字段已经具备，准确性需要后续人工标注样本验证”。
- 正式演示前仍需要在Java 8下执行mvn clean test和mvn -DskipTests package，并保存成功结果作为演示证据。
`;
await fs.writeFile(`${root}/README-合同样例测试数据说明.md`, readme, "utf8");

console.log(JSON.stringify({
  outputDir: root,
  excelFiles: 11,
  postmanCollection: `${root}/合同样例-多场景测试.postman_collection.json`,
  postmanEnvironment: `${root}/合同样例-Postman环境.postman_environment.json`
}, null, 2));
