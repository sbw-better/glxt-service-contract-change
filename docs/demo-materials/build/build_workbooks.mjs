import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const OUT_DIR = "D:/projects/glxt-service-contract-change/docs/demo-materials";
const RENDER_DIR = `${OUT_DIR}/build/xlsx_render`;

async function saveWorkbook(workbook, name) {
  const output = await SpreadsheetFile.exportXlsx(workbook);
  const path = `${OUT_DIR}/${name}.xlsx`;
  await output.save(path);
  return path;
}

async function renderWorkbook(workbook, name, sheets) {
  await fs.mkdir(RENDER_DIR, { recursive: true });
  for (const sheetName of sheets) {
    const png = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
    await fs.writeFile(`${RENDER_DIR}/${name}-${sheetName}.png`, new Uint8Array(await png.arrayBuffer()));
  }
}

function styleHeader(range) {
  range.format = {
    fill: "#E8EEF5",
    font: { bold: true, color: "#111111" },
    borders: { preset: "all", style: "thin", color: "#D9DEE5" },
    wrapText: true,
  };
}

function styleBody(range) {
  range.format = {
    borders: { preset: "all", style: "thin", color: "#E5E7EB" },
    wrapText: true,
  };
}

function createImportWorkbook(rows, badHeader = false) {
  const wb = Workbook.create();
  const ws = wb.worksheets.add("导入样本");
  ws.showGridLines = false;
  ws.getRange("A1:B1").values = [[badHeader ? "段落内容" : "合同段落", badHeader ? "类型编码" : "变更类型编码"]];
  ws.getRange(`A2:B${rows.length + 1}`).values = rows;
  styleHeader(ws.getRange("A1:B1"));
  styleBody(ws.getRange(`A2:B${rows.length + 1}`));
  ws.getRange("A:A").format.columnWidth = 78;
  ws.getRange("B:B").format.columnWidth = 22;
  ws.freezePanes.freezeRows(1);
  return wb;
}

const normalRows = [
  ["因设计优化需要，承包人应在收到发包人书面指令后十日内提交调整后的施工组织方案。", "20;25"],
  ["若主要材料价格连续两个月波动超过约定幅度，双方应依据补充协议重新核定材料价差。", "28"],
  ["工程量清单漏项经双方确认后，应按合同约定的计价原则办理变更和结算。", "20;43"],
  ["因不可抗力导致工期延误的，承包人应及时提交证明材料并申请顺延工期。", "57"],
  ["发包人要求增加临时安全防护措施的，相关费用经确认后纳入合同价款调整。", "25;43"],
  ["施工图纸变更涉及关键节点调整的，承包人应同步更新进度计划并报监理确认。", "20;57"],
  ["现场签证事项应载明发生原因、工程量、单价依据和确认人员，作为价款调整依据。", "43"],
  ["因政策调整造成税费计取口径变化的，双方应按最新规定办理合同价款调整。", "28;43"],
];

const duplicateRows = [
  normalRows[0],
  normalRows[0],
  normalRows[1],
  normalRows[2],
];

const conflictRows = [
  ["因设计优化需要，承包人应在收到发包人书面指令后十日内提交调整后的施工组织方案。", "20;25"],
  ["因设计优化需要，承包人应在收到发包人书面指令后十日内提交调整后的施工组织方案。", "28"],
  normalRows[3],
];

function createValidationWorkbook() {
  const wb = Workbook.create();

  const readme = wb.worksheets.add("使用说明");
  readme.showGridLines = false;
  readme.getRange("A1:E1").merge();
  readme.getRange("A1").values = [["合同段落变更类型识别服务 - 人工验证与知识库完善模板"]];
  readme.getRange("A1").format = { font: { bold: true, color: "#111111" }, fill: "#E8EEF5" };
  readme.getRange("A3:E8").values = [
    ["用途", "说明", null, null, null],
    ["人工标注测试集", "录入未进入历史库的独立段落、人工标准类型和系统预测结果，用于统计验收指标。", null, null, null],
    ["指标汇总", "根据人工标注页统计样本数、命中数、HIGH 结果数量等，可按项目验收口径继续扩展。", null, null, null],
    ["知识库补充候选", "文档比对后产生的新段落，经人工确认类型后，可整理为导入样本。", null, null, null],
    ["重要原则", "未经人工确认的预测结果不自动入库，避免错误标签污染历史知识库。", null, null, null],
    ["演示口径", "演示样本证明流程，真实准确性需要 30-100 条独立人工标注样本支撑。", null, null, null],
  ];
  readme.getRange("A3:B8").format = { wrapText: true, borders: { preset: "all", style: "thin", color: "#E5E7EB" } };
  readme.getRange("A3:A8").format = { fill: "#F1F3F5", font: { bold: true } };
  readme.getRange("A:A").format.columnWidth = 20;
  readme.getRange("B:B").format.columnWidth = 95;

  const test = wb.worksheets.add("人工标注测试集");
  test.showGridLines = false;
  const headers = [["样本ID", "来源", "段落文本", "人工变更类型编码", "系统matchType", "maxSimilarity", "系统返回类型编码", "最高level", "最高score", "supportCount", "是否命中人工标签", "是否可补充知识库", "备注"]];
  test.getRange("A1:M1").values = headers;
  const seed = [
    ["T001", "独立测试集", "填写未进入历史库的合同段落", "20;25", "待填", null, "待填", "待填", null, null, "待判定", "待判定", "演示样例行，正式验证时替换"],
    ["T002", "独立测试集", "填写第二条人工标注段落", "28", "待填", null, "待填", "待填", null, null, "待判定", "待判定", ""],
    ["T003", "独立测试集", "填写第三条人工标注段落", "43", "待填", null, "待填", "待填", null, null, "待判定", "待判定", ""],
  ];
  test.getRange("A2:M4").values = seed;
  styleHeader(test.getRange("A1:M1"));
  styleBody(test.getRange("A2:M101"));
  test.getRange("A:A").format.columnWidth = 12;
  test.getRange("B:B").format.columnWidth = 16;
  test.getRange("C:C").format.columnWidth = 58;
  test.getRange("D:D").format.columnWidth = 20;
  test.getRange("E:M").format.columnWidth = 18;
  test.freezePanes.freezeRows(1);

  const summary = wb.worksheets.add("指标汇总");
  summary.showGridLines = false;
  summary.getRange("A1:D1").merge();
  summary.getRange("A1").values = [["准确性验证指标汇总"]];
  summary.getRange("A1").format = { font: { bold: true, color: "#111111" }, fill: "#E8EEF5" };
  summary.getRange("A3:D11").values = [
    ["指标", "公式/口径", "当前值", "说明"],
    ["测试样本数", "人工标注测试集非空样本数", null, "建议 30-100 条"],
    ["命中样本数", "是否命中人工标签 = 是", null, "多标签只要覆盖人工标签即可按口径判定"],
    ["类型命中率", "命中样本数 / 测试样本数", null, "演示样本不能替代真实验收"],
    ["HIGH 结果数", "最高level = HIGH", null, "关注高可信结果质量"],
    ["无可靠匹配数", "matchType = NO_RELIABLE_MATCH", null, "反映知识库覆盖空白"],
    ["可补充知识库数", "是否可补充知识库 = 是", null, "人工确认后可入库"],
    ["人工标注完成率", "人工变更类型编码非空 / 样本数", null, "验证前置条件"],
    ["备注", "F1、召回率可按类型明细另建透视表", null, "正式验收建议按类型统计"],
  ];
  summary.getRange("C4").formulas = [["=COUNTA('人工标注测试集'!A2:A101)"]];
  summary.getRange("C5").formulas = [["=COUNTIF('人工标注测试集'!K2:K101,\"是\")"]];
  summary.getRange("C6").formulas = [["=IF(C4=0,0,C5/C4)"]];
  summary.getRange("C7").formulas = [["=COUNTIF('人工标注测试集'!H2:H101,\"HIGH\")"]];
  summary.getRange("C8").formulas = [["=COUNTIF('人工标注测试集'!E2:E101,\"NO_RELIABLE_MATCH\")"]];
  summary.getRange("C9").formulas = [["=COUNTIF('人工标注测试集'!L2:L101,\"是\")"]];
  summary.getRange("C10").formulas = [["=IF(C4=0,0,COUNTA('人工标注测试集'!D2:D101)/C4)"]];
  styleHeader(summary.getRange("A3:D3"));
  styleBody(summary.getRange("A4:D11"));
  summary.getRange("C6:C10").format.numberFormat = "0.0%";
  summary.getRange("A:A").format.columnWidth = 22;
  summary.getRange("B:B").format.columnWidth = 42;
  summary.getRange("C:C").format.columnWidth = 16;
  summary.getRange("D:D").format.columnWidth = 48;

  const kb = wb.worksheets.add("知识库补充候选");
  kb.showGridLines = false;
  kb.getRange("A1:J1").values = [["比对批次", "段落文本", "系统建议类型", "人工确认类型", "确认状态", "确认人", "确认时间", "冲突处理", "是否导入", "备注"]];
  kb.getRange("A2:J5").values = [
    ["COMPARE-001", "填写文档比对后发现的新段落", "20;25", "", "待确认", "", "", "无", "否", "人工确认后再导入"],
    ["COMPARE-001", "填写另一个待沉淀段落", "28", "", "待确认", "", "", "无", "否", ""],
    ["COMPARE-002", "若与历史 Hash 相同但类型不同，需人工判断是否历史标签错误", "43", "", "待复核", "", "", "冲突待处理", "否", ""],
    ["COMPARE-002", "确认后的低覆盖领域样本可优先补充", "57", "", "待确认", "", "", "无", "否", ""],
  ];
  styleHeader(kb.getRange("A1:J1"));
  styleBody(kb.getRange("A2:J101"));
  kb.getRange("A:A").format.columnWidth = 16;
  kb.getRange("B:B").format.columnWidth = 64;
  kb.getRange("C:J").format.columnWidth = 17;
  kb.freezePanes.freezeRows(1);

  return wb;
}

const outputs = [];
const normalWb = createImportWorkbook(normalRows);
outputs.push(await saveWorkbook(normalWb, "演示导入样本-正常"));
await renderWorkbook(normalWb, "演示导入样本-正常", ["导入样本"]);

const duplicateWb = createImportWorkbook(duplicateRows);
outputs.push(await saveWorkbook(duplicateWb, "演示导入样本-重复"));
await renderWorkbook(duplicateWb, "演示导入样本-重复", ["导入样本"]);

const conflictWb = createImportWorkbook(conflictRows);
outputs.push(await saveWorkbook(conflictWb, "演示导入样本-冲突"));
await renderWorkbook(conflictWb, "演示导入样本-冲突", ["导入样本"]);

const badHeaderWb = createImportWorkbook(normalRows.slice(0, 2), true);
outputs.push(await saveWorkbook(badHeaderWb, "演示导入样本-错误表头"));
await renderWorkbook(badHeaderWb, "演示导入样本-错误表头", ["导入样本"]);

const validationWb = createValidationWorkbook();
outputs.push(await saveWorkbook(validationWb, "人工标注验证与知识库完善模板"));
await renderWorkbook(validationWb, "人工标注验证与知识库完善模板", ["使用说明", "人工标注测试集", "指标汇总", "知识库补充候选"]);

for (const path of outputs) {
  console.log(path);
}
