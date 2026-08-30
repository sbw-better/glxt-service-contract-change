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

async function saveWorkbook(fileName, sheets, previewFirstSheet = true) {
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
  if (previewFirstSheet) {
    const png = await workbook.render({
      sheetName: sheets[0].name,
      range: `A1:${col(sheets[0].values[0].length)}${Math.min(sheets[0].values.length, 28)}`,
      scale: 1,
      format: "png",
    });
    await fs.writeFile(`${buildDir}/${fileName}.preview.png`, new Uint8Array(await png.arrayBuffer()));
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
    ["合同样例-测试导入样本-正常-990行.xlsx", "正常导入、大批量、单标签、多标签", "优先导入", "插入/更新/跳过统计、导入后索引刷新"],
    ["合同样例-测试导入样本-重复.xlsx", "同段落同编码重复", "正常样本导入后再导入", "重复样本应幂等跳过，不应新增重复历史经验"],
    ["合同样例-测试导入样本-冲突.xlsx", "同段落不同编码冲突", "作为错误场景演示", "整批拒绝，避免悄悄覆盖历史标签"],
    ["合同样例-测试导入样本-分隔符与去重.xlsx", "中文分号、中文逗号、重复编码", "可单独导入", "编码规范化为去重排序后的英文分号"],
    ["合同样例-测试导入样本-错误表头.xlsx", "第一行表头不符合要求", "作为错误场景演示", "第一张Sheet前两列表头必须是合同段落、变更类型编码"],
    ["合同样例-测试导入样本-空值与非法编码.xlsx", "空段落、空编码、编码含空格、单编码过长", "作为错误场景演示", "逐行返回校验错误"],
    ["合同样例-测试导入样本-超长段落.xlsx", "段落超过2000字符", "作为边界场景演示", "明确拒绝，不自动截断"],
    ["合同样例-测试导入样本-超过1000行.xlsx", "单Excel超过1000行", "只做边界验证，谨慎现场调用", "第1001条后触发最大行数限制"],
    ["合同样例-预测用例.xlsx", "EXACT、语义、高相似兜底、无可靠匹配、空段落、超长段落", "配合Postman请求使用", "解释matchType、similarity和score"],
    ["合同样例-多场景测试.postman_collection.json", "接口请求集合", "导入Postman后按文件夹顺序执行", "baseUrl和UserId放在环境变量"],
  ];
}

const normalRows = dataset.normalRows.map((row) => ({
  paragraph: row.paragraph,
  codes: row.codes,
  source: row.source,
  scenario: row.scenario,
}));

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
      ["用途", "用于正常导入演示，覆盖单标签、多标签、合同原文段落和轻微改写段落。"],
      ["数据来源", `从样例合同抽取 ${dataset.baseParagraphCount} 条可用段落，并生成同主题改写样本。`],
      ["注意", "编码为演示标签，不代表真实业务人工标注结果。"],
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
], false);

await saveWorkbook("合同样例-预测用例.xlsx", [
  {
    name: "预测用例",
    values: [
      ["用例名称", "paragraph", "预期关注点"],
      ...dataset.predictionCases.map((item) => [item.name, item.paragraph, item.expectedFocus]),
    ],
    widths: [280, 760, 460],
    tableName: "PredictionCases",
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
  };
}

function uploadRequest(name, fileName, desc, withUserId = true) {
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
  };
}

const collection = {
  info: {
    name: "合同段落变更类型识别服务-合同样例多场景测试",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    description: "基于合同样例生成的导入与预测测试集合。导入和预测必须携带UserId；baseUrl建议设置为http://localhost:8080/glxt-service-contract-change。",
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
        },
      ],
    },
    {
      name: "01 Excel导入",
      item: [
        uploadRequest("正常导入-990行", "合同样例-测试导入样本-正常-990行.xlsx", "正常导入演示，覆盖大批量、单标签、多标签和轻微改写样本。"),
        uploadRequest("重复导入-同段落同编码", "合同样例-测试导入样本-重复.xlsx", "验证相同Hash和相同编码的幂等跳过。"),
        uploadRequest("冲突导入-同段落不同编码", "合同样例-测试导入样本-冲突.xlsx", "验证标签冲突时整批拒绝，避免覆盖历史知识。"),
        uploadRequest("分隔符与去重导入", "合同样例-测试导入样本-分隔符与去重.xlsx", "验证中文分号、中文逗号和重复编码会被规范化。"),
        uploadRequest("错误表头导入", "合同样例-测试导入样本-错误表头.xlsx", "验证第一张Sheet前两列表头必须完全匹配。"),
        uploadRequest("空值与非法编码导入", "合同样例-测试导入样本-空值与非法编码.xlsx", "验证逐行校验错误返回。"),
        uploadRequest("超长段落导入", "合同样例-测试导入样本-超长段落.xlsx", "验证超过2000字符明确拒绝。"),
        uploadRequest("超过1000行导入", "合同样例-测试导入样本-超过1000行.xlsx", "验证单Excel最多1000行；现场调用前注意这会走校验失败路径。"),
        uploadRequest("缺少UserId导入", "合同样例-测试导入样本-重复.xlsx", "验证UserId请求头不能为空。", false),
      ],
    },
    {
      name: "02 预测识别",
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
        },
      ],
    },
    {
      name: "03 索引维护",
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

本目录下的测试数据基于用户提供的合同样例抽取和轻微改写生成。合同文本只作为测试内容来源，变更类型编码是演示标签，不代表真实人工标注结论。

## 建议演示顺序

1. Postman导入环境文件和Collection，确认baseUrl与userId。
2. 调用“查询索引状态”，先看当前样本数和索引状态。
3. 上传“正常导入-990行”，演示历史知识入库和索引刷新。
4. 用“EXACT_完全相同段落”预测，说明完全命中不调用模型。
5. 用语义预测相关请求，说明similarity是段落相似度，score是变更类型的历史证据支持度。
6. 上传重复、冲突、空值、超长、超过1000行等文件，演示边界和校验。
7. 调用“手工重载索引”和“重载后查询索引状态”，说明运维兜底能力。

## 文件用途

${scenarioRows().slice(1).map((r) => `- ${r[0]}：${r[1]}。${r[3]}`).join("\n")}

## 现场提示

- 测试环境真实调用模型网关，正常导入920行可能耗时较长；如果现场时间紧，可以先用较小的已有演示样本导入，再把本批大样本作为补充验证材料。
- 缺少真实人工测试集时，不建议把演示结果表述为准确率结论；更稳妥的说法是“接口闭环、规则边界和可解释字段已经具备，准确性需要后续人工标注样本验证”。
- 当前已知代码编译P0问题尚未修复，正式演示前仍需要完成Java 8下mvn clean test和打包验证。
`;
await fs.writeFile(`${root}/README-合同样例测试数据说明.md`, readme, "utf8");

console.log(JSON.stringify({
  outputDir: root,
  excelFiles: 9,
  postmanCollection: `${root}/合同样例-多场景测试.postman_collection.json`,
  postmanEnvironment: `${root}/合同样例-Postman环境.postman_environment.json`
}, null, 2));
