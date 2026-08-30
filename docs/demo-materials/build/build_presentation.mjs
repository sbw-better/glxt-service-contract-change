import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT_DIR = "D:/projects/glxt-service-contract-change/docs/demo-materials";
const BUILD_DIR = `${OUT_DIR}/build/ppt_render`;
const FINAL = `${OUT_DIR}/合同段落变更类型识别服务-汇报演示.pptx`;

const C = {
  ink: "#111111",
  muted: "#5B6472",
  rule: "#B8BCC4",
  panel: "#F1F3F5",
  panel2: "#E8EEF5",
  accent: "#3D8DFF",
  accent2: "#00A676",
  warn: "#C07600",
  risk: "#B42318",
  white: "#FFFFFF",
};

async function writeBlob(path, blob) {
  await fs.writeFile(path, new Uint8Array(await blob.arrayBuffer()));
}

function addBox(slide, name, left, top, width, height, fill = C.panel, line = C.rule) {
  return slide.shapes.add({
    geometry: "rect",
    name,
    position: { left, top, width, height },
    fill,
    line: { style: "solid", fill: line, width: 1 },
  });
}

function addText(slide, name, text, left, top, width, height, opts = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position: { left, top, width, height },
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = text;
  shape.text.style = {
    fontSize: opts.size ?? 22,
    bold: opts.bold ?? false,
    color: opts.color ?? C.ink,
    alignment: opts.align ?? "left",
  };
  return shape;
}

function addTitle(slide, title, kicker = "") {
  addText(slide, "kicker", kicker || "合同段落变更类型识别服务", 72, 42, 760, 28, {
    size: 16,
    bold: true,
    color: C.muted,
  });
  addText(slide, "title", title, 72, 84, 1000, 92, {
    size: 38,
    bold: true,
    color: C.ink,
  });
  addBox(slide, "title-rule", 72, 186, 160, 3, C.accent, C.accent);
}

function addFooter(slide, n) {
  addText(slide, "footer", String(n).padStart(2, "0"), 1160, 666, 48, 28, {
    size: 16,
    bold: true,
    color: C.muted,
    align: "right",
  });
}

function addBullets(slide, items, left, top, width, lineHeight = 43, size = 22) {
  items.forEach((item, i) => {
    addBox(slide, `bullet-dot-${i}`, left, top + i * lineHeight + 10, 8, 8, C.accent, C.accent);
    addText(slide, `bullet-${i}`, item, left + 22, top + i * lineHeight, width - 22, 34, {
      size,
      color: C.ink,
    });
  });
}

function addColumns(slide, columns, top = 230) {
  const gap = 28;
  const width = (1136 - gap * (columns.length - 1)) / columns.length;
  columns.forEach((col, i) => {
    const left = 72 + i * (width + gap);
    addBox(slide, `col-${i}`, left, top, width, 330, i === 1 ? C.panel2 : C.panel, "#D9DEE5");
    addText(slide, `col-head-${i}`, col.head, left + 24, top + 24, width - 48, 40, {
      size: 24,
      bold: true,
    });
    addText(slide, `col-body-${i}`, col.body, left + 24, top + 82, width - 48, 210, {
      size: 18,
      color: C.muted,
    });
  });
}

function addFlow(slide, steps, top = 248) {
  const gap = 18;
  const width = (1136 - gap * (steps.length - 1)) / steps.length;
  steps.forEach((s, i) => {
    const left = 72 + i * (width + gap);
    addBox(slide, `flow-${i}`, left, top, width, 112, i % 2 === 0 ? C.panel : C.panel2, "#D9DEE5");
    addText(slide, `flow-num-${i}`, String(i + 1), left + 18, top + 16, 34, 32, {
      size: 24,
      bold: true,
      color: C.accent,
    });
    addText(slide, `flow-text-${i}`, s, left + 54, top + 18, width - 70, 70, {
      size: 18,
      bold: true,
    });
    if (i < steps.length - 1) {
      addText(slide, `arrow-${i}`, ">", left + width + 2, top + 38, 20, 32, {
        size: 24,
        bold: true,
        color: C.muted,
        align: "center",
      });
    }
  });
}

function notes(slide, lines, sources = []) {
  const text = [
    ...lines,
    "",
    "[Sources]",
    ...(sources.length ? sources : ["项目 README、接口文档、Oracle 初始化脚本、当前实现上下文。"]),
  ].join("\n");
  slide.speakerNotes.textFrame.setText(text);
  slide.speakerNotes.setVisible(true);
}

const deck = Presentation.create({ slideSize: { width: 1280, height: 720 } });

let s = deck.slides.add();
s.background.fill = C.white;
addText(s, "small", "第一版实现检查与汇报演示", 72, 60, 520, 32, { size: 18, bold: true, color: C.muted });
addText(s, "cover-title", "合同段落变更类型识别服务", 72, 170, 900, 92, { size: 54, bold: true });
addText(s, "cover-subtitle", "历史样本导入、语义检索、多标签投票与知识库持续完善闭环", 76, 295, 880, 44, { size: 24, color: C.muted });
addBox(s, "cover-panel", 72, 430, 1030, 108, C.panel2, "#D9DEE5");
addText(s, "cover-points", "适合 25-30 分钟线上汇报：PPT 主讲 + Swagger/Postman 演示 + Oracle 只读核验", 98, 462, 980, 42, { size: 23, bold: true });
addFooter(s, 1);
notes(s, ["开场说明：本次汇报重点不是展示一个孤立接口，而是展示一个可解释、可运维、可持续完善的业务闭环。"]);

s = deck.slides.add();
addTitle(s, "这项能力解决的是人工识别慢、历史经验难复用的问题");
addColumns(s, [
  { head: "业务痛点", body: "合同文档比对后会产生大量变化段落，人工逐段判断变更类型耗时且口径依赖个人经验。" },
  { head: "系统目标", body: "把历史“段落-类型”关系沉淀为可检索知识，给新段落返回候选类型和参考段落。" },
  { head: "第一版定位", body: "先做简单可靠的辅助识别能力，强调可解释、可回退、容易部署和验收。" },
]);
addFooter(s, 2);
notes(s, ["强调这是辅助判断系统，不替代人工确认。它的价值在于把历史经验复用起来，减少重复判断。"]);

s = deck.slides.add();
addTitle(s, "第一版边界清楚，减少现场演示和上线复杂度");
addBullets(s, [
  "导入历史 Excel 后调用统一 Embedding 网关生成 1024 维向量",
  "向量保存到 Oracle 11g，JVM 内存索引做 1 万条以内精确检索",
  "预测时返回候选变更类型、可信等级和最多 5 条参考段落",
  "不引入向量数据库、Rerank、异步任务或额外业务表",
  "不关联 TPIF_XBGHTZJ；输入必须是已切分好的单个合同段落",
], 96, 230, 1040, 48, 21);
addFooter(s, 3);
notes(s, ["这页要主动讲边界。技术听众会关心为什么没有上更复杂组件，业务听众会关心现在能解决什么。"]);

s = deck.slides.add();
addTitle(s, "核心闭环从历史样本开始，也允许人工确认后持续补充");
addFlow(s, ["历史 Excel", "文本规范化\nHash", "Embedding\n1024 维", "Oracle\n持久化", "JVM 索引", "预测投票"], 236);
addBox(s, "loop", 170, 430, 940, 108, "#F6F8FA", "#D9DEE5");
addText(s, "loop-title", "文档比对后的知识完善闭环", 202, 452, 850, 30, { size: 24, bold: true });
addText(s, "loop-body", "新段落经过人工确认变更类型后，可通过现有导入流程沉淀为历史样本；不把未经确认的预测结果自动反哺。", 202, 492, 850, 32, { size: 20, color: C.muted });
addFooter(s, 4);
notes(s, ["这里引入新增考虑点：系统不是一次性知识库。关键原则是人工确认后补充，避免错误标签污染后续投票。"]);

s = deck.slides.add();
addTitle(s, "对外接口围绕导入、预测和索引状态展开");
addColumns(s, [
  { head: "导入", body: "POST /samples/import\n上传 .xlsx，必须携带 UserId。\n校验、向量化、入库、重载索引同步完成。" },
  { head: "预测", body: "POST /predict\n输入单段合同文本。\n先 Hash 精确命中，再走语义检索和多标签投票。" },
  { head: "索引", body: "GET /index/status\nPOST /index/reload\n可观测当前样本数、模型版本、维度和加载状态。" },
], 230);
addFooter(s, 5);
notes(s, ["演示入口建议用 Swagger 或 Postman。强调 UserId 只透传给模型平台用于审计，不入库、不打日志。"]);

s = deck.slides.add();
addTitle(s, "预测逻辑先精确命中，再用相似历史样本投票");
addFlow(s, ["规范化\nSHA-256", "Hash 精确\n命中", "未命中\n生成向量", "Top 10\n相似样本", "相似度平方\n加权投票", "候选类型\n和参考段落"], 228);
addText(s, "rule", "阈值规则：similarity < 0.60 不参与投票；score >= 0.80 且 support >= 2 为 HIGH；score >= 0.55 为 CANDIDATE。", 96, 425, 1010, 46, { size: 22, bold: true });
addText(s, "fallback", "兜底规则：若无类型达标但第一名 similarity >= 0.80，返回第一历史样本类型为 CANDIDATE，score 仍保留真实投票占比。", 96, 492, 1010, 44, { size: 20, color: C.muted });
addFooter(s, 6);
notes(s, ["这一页可解释最近截图案例：相似度 0.9389 触发强相似兜底，所以低于 0.55 的类型仍以候选返回。"]);

s = deck.slides.add();
addTitle(s, "similarity 看文本相近程度，score 看历史证据支持度");
addColumns(s, [
  { head: "similarity", body: "新段落与某条历史段落的余弦相似度。\n它回答：这两段文本像不像。" },
  { head: "score", body: "某个类型在 Top-K 历史样本中的加权支持占比。\n它回答：历史证据多大程度支持这个类型。" },
  { head: "不要误读", body: "score 不是概率，多标签结果的分数之和不一定等于 1。\n参考段落最多展示 5 条，但最多 10 条参与投票。" },
], 230);
addFooter(s, 7);
notes(s, ["这是面向业务方最重要的解释页。避免把 score 讲成模型置信概率。"]);

s = deck.slides.add();
addTitle(s, "技术选型服务于第一版目标：简单、可控、能解释");
addColumns(s, [
  { head: "不用向量数据库", body: "第一版最多 1 万条历史样本，Java 内存精确点积足够；少引入一个组件，部署和排障更简单。" },
  { head: "固定 1024 维", body: "相对默认 4096 维，网络响应、Oracle BLOB、JVM 内存和计算量约降为四分之一。" },
  { head: "Oracle + JVM 索引", body: "Oracle 负责持久化和审计字段，AtomicReference 切换只读索引快照，适配当前单实例部署。" },
], 230);
addFooter(s, 8);
notes(s, ["技术听众可能会问规模和扩展。回应：这是按 1 万条样本的一期边界设计，未来多实例或更大规模再引入分布式索引能力。"]);

s = deck.slides.add();
addTitle(s, "模型接入和安全边界已按企业网关方式处理");
addBullets(s, [
  "调用公司统一模型网关，OpenAI 兼容 embeddings 接口",
  "请求显式传 model、dimensions=1024、encoding_format=float",
  "API Key 只通过 EMBEDDING_API_KEY 环境变量提供",
  "日志不输出合同正文、向量、UserId、API Key 或模型响应正文",
  "401/403/404/422/429 不重试；连接异常和 5xx 最多重试一次",
], 96, 230, 1040, 48, 21);
addFooter(s, 9);
notes(s, ["这里强调合规和可运维：密钥不进代码、不进 Git、不进日志；模型版本与维度用于隔离向量兼容性。"]);

s = deck.slides.add();
addTitle(s, "知识库完善要坚持人工确认，避免错误标签自我放大");
addColumns(s, [
  { head: "近期做法", body: "文档比对结束后导出待确认样本，人工确认段落和类型，再通过现有 Excel 导入流程入库。" },
  { head: "入库条件", body: "文本有效、类型编码合法、Hash 去重；相同段落类型冲突时拒绝静默覆盖，必须人工处理。" },
  { head: "后续增强", body: "可新增确认入库接口、确认人/确认时间/来源批次等审计字段，以及 decisionReason 提升解释性。" },
], 230);
addFooter(s, 10);
notes(s, ["这页回应用户新增需求。说清楚：本次不改代码，但设计上已把持续完善纳入方案。"]);

s = deck.slides.add();
addTitle(s, "现场演示按“可用性 -> 证据 -> 边界”推进");
addBullets(s, [
  "1. index/status：确认索引状态、样本数、模型版本和维度",
  "2. 导入匿名小 Excel：展示同步导入、幂等跳过和错误提示",
  "3. Oracle 只读查询：核验 Hash、类型、1024 维和 BLOB 长度",
  "4. EXACT / HIGH / CANDIDATE 预测：解释参考段落和分数",
  "5. NO_RELIABLE_MATCH 与 index/reload：展示边界和运维动作",
], 96, 218, 1040, 54, 21);
addFooter(s, 11);
notes(s, ["线上投屏建议一开始打开 Swagger/Postman、SQL 客户端和讲稿，避免窗口切换卡顿。"]);

s = deck.slides.add();
addTitle(s, "演示前必须先处理 P0 编译问题");
addBox(s, "risk", 86, 220, 1108, 96, "#FFF4E5", "#F2C078");
addText(s, "risk-text", "当前 Java 8 执行 mvn test 编译失败，原因是部分业务类缺少 util 包 import。正式演示前必须修复并保存测试、打包成功证据。", 112, 246, 1040, 42, { size: 22, bold: true, color: C.warn });
addColumns(s, [
  { head: "必须完成", body: "修复 import\nmvn clean test\nmvn -DskipTests package\n启动服务并检查 health/status" },
  { head: "不掩盖", body: "PPT 可先准备，但现场演示前不能假定服务可运行。必要时准备录屏或截图作为备选。" },
  { head: "可选优化", body: "loadedAt 格式化为 GMT+8 时间；响应增加 decisionReason 解释强相似兜底。" },
], 360);
addFooter(s, 12);
notes(s, ["这页内部汇报时可以酌情保留或淡化，但作为演示准备材料必须明确。"]);

s = deck.slides.add();
addTitle(s, "准确性结论需要独立人工标注集支撑");
addColumns(s, [
  { head: "样本", body: "准备 30-100 条未进入历史库的合同段落，由业务人员人工标注标准类型。" },
  { head: "指标", body: "覆盖率、类型准确率、召回率、F1、HIGH 准确率、NO_RELIABLE_MATCH 比例。" },
  { head: "口径", body: "演示样本只能证明流程可用，不能单独证明业务准确性；验收需独立测试集。" },
], 230);
addFooter(s, 13);
notes(s, ["这页要坦诚。没有真实测试数据时，不要说准确率已经达到某个数字。"]);

s = deck.slides.add();
addTitle(s, "结论：第一版适合先上线试用，再用人工确认样本持续优化");
addBullets(s, [
  "功能闭环完整：导入、向量化、持久化、索引、预测、参考证据",
  "技术方案轻量：Oracle + JVM 索引即可覆盖当前样本规模",
  "输出可解释：EXACT / SEMANTIC / NO_RELIABLE_MATCH 与参考段落共同支撑判断",
  "质量闭环明确：文档比对新增样本必须人工确认后再补充知识库",
  "演示前关键动作：修复编译、跑通测试、准备匿名样本和备用录屏",
], 96, 230, 1040, 48, 21);
addFooter(s, 14);
notes(s, ["收尾不要停在技术细节，回到业务价值：先辅助人工识别，再通过确认样本让知识库持续变好。"]);

await fs.mkdir(BUILD_DIR, { recursive: true });
for (const [index, slide] of deck.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  const png = await deck.export({ slide, format: "png", scale: 1 });
  await writeBlob(`${BUILD_DIR}/${stem}.png`, png);
  const layout = await slide.export({ format: "layout" });
  await fs.writeFile(`${BUILD_DIR}/${stem}.layout.json`, await layout.text(), "utf8");
}
const montage = await deck.export({ format: "webp", montage: true, scale: 1 });
await writeBlob(`${BUILD_DIR}/deck-montage.webp`, montage);
const inspect = await deck.inspect({ kind: "slide,textbox,shape,notes", maxChars: 12000 });
await fs.writeFile(`${BUILD_DIR}/inspect.ndjson`, inspect.ndjson, "utf8");
const pptx = await PresentationFile.exportPptx(deck);
await pptx.save(FINAL);
console.log(FINAL);
