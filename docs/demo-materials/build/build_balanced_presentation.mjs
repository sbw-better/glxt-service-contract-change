import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT_DIR = "D:/projects/glxt-service-contract-change/docs/demo-materials";
const BUILD_DIR = `${OUT_DIR}/build/balanced_ppt_render`;
const FINAL = `${OUT_DIR}/合同段落变更类型识别服务-综合汇报版-业务背景融合版.pptx`;

const C = {
  ink: "#111111",
  muted: "#58606E",
  panel: "#F3F5F7",
  panel2: "#E9F0F7",
  accent: "#2F80ED",
  green: "#1B8A5A",
  amber: "#A46300",
  red: "#B42318",
  rule: "#D7DCE3",
  white: "#FFFFFF",
};

async function writeBlob(path, blob) {
  await fs.writeFile(path, new Uint8Array(await blob.arrayBuffer()));
}

function box(slide, name, left, top, width, height, fill = C.panel, line = C.rule) {
  return slide.shapes.add({
    geometry: "rect",
    name,
    position: { left, top, width, height },
    fill,
    line: { style: "solid", fill: line, width: 1 },
  });
}

function text(slide, name, value, left, top, width, height, opts = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position: { left, top, width, height },
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = value;
  shape.text.style = {
    fontSize: opts.size ?? 22,
    bold: opts.bold ?? false,
    color: opts.color ?? C.ink,
    alignment: opts.align ?? "left",
  };
  return shape;
}

function title(slide, value, eyebrow = "合同段落变更类型识别服务") {
  text(slide, "eyebrow", eyebrow, 72, 42, 820, 28, { size: 16, bold: true, color: C.muted });
  text(slide, "title", value, 72, 84, 1070, 88, { size: 38, bold: true });
  box(slide, "title-rule", 72, 182, 150, 3, C.accent, C.accent);
}

function footer(slide, n) {
  text(slide, "footer", String(n).padStart(2, "0"), 1160, 666, 48, 28, {
    size: 16,
    bold: true,
    color: C.muted,
    align: "right",
  });
}

function bullets(slide, items, left, top, width, gap = 48, size = 21) {
  items.forEach((item, i) => {
    box(slide, `dot-${i}`, left, top + i * gap + 12, 8, 8, C.accent, C.accent);
    text(slide, `bullet-${i}`, item, left + 24, top + i * gap, width - 24, 34, { size });
  });
}

function columns(slide, cols, top = 230, height = 320) {
  const gap = 28;
  const width = (1136 - gap * (cols.length - 1)) / cols.length;
  cols.forEach((col, i) => {
    const left = 72 + i * (width + gap);
    box(slide, `col-${i}`, left, top, width, height, i === 1 ? C.panel2 : C.panel);
    text(slide, `col-head-${i}`, col.head, left + 24, top + 24, width - 48, 40, {
      size: 24,
      bold: true,
    });
    text(slide, `col-body-${i}`, col.body, left + 24, top + 84, width - 48, height - 118, {
      size: 18,
      color: C.muted,
    });
  });
}

function flow(slide, steps, top = 230) {
  const gap = 14;
  const width = (1136 - gap * (steps.length - 1)) / steps.length;
  steps.forEach((step, i) => {
    const left = 72 + i * (width + gap);
    box(slide, `step-${i}`, left, top, width, 110, i % 2 ? C.panel2 : C.panel);
    text(slide, `num-${i}`, String(i + 1), left + 16, top + 16, 32, 30, {
      size: 22,
      bold: true,
      color: C.accent,
    });
    text(slide, `step-text-${i}`, step, left + 50, top + 16, width - 62, 72, {
      size: 17,
      bold: true,
    });
  });
}

function note(slide, value, top = 520, color = C.amber) {
  box(slide, "note-box", 96, top, 1000, 58, "#FFF8E8", "#E4C783");
  text(slide, "note-text", value, 124, top + 16, 944, 28, { size: 21, bold: true, color });
}

function notes(slide, lines) {
  slide.speakerNotes.textFrame.setText([...lines, "", "[Sources]", "《合同变更流程 - 变更章节自动解析功能业务需求说明书》业务背景与现状问题；项目 README、接口文档、Oracle 脚本。"].join("\n"));
  slide.speakerNotes.setVisible(true);
}

const deck = Presentation.create({ slideSize: { width: 1280, height: 720 } });
let s;

s = deck.slides.add();
s.background.fill = C.white;
text(s, "kicker", "综合汇报版", 72, 62, 300, 30, { size: 18, bold: true, color: C.muted });
text(s, "cover-title", "合同段落变更类型识别服务", 72, 170, 900, 90, { size: 54, bold: true });
text(s, "cover-sub", "减少合同生效阶段的重复梳理，让历史判断经验真正复用起来", 76, 300, 980, 42, { size: 24, color: C.muted });
box(s, "cover-band", 72, 436, 1030, 94, C.panel2);
text(s, "cover-band-text", "系统先找出候选类型和历史依据，业务人员结合合同上下文做最终确认", 100, 466, 960, 36, { size: 23, bold: true });
footer(s, 1);
notes(s, ["开场先落到真实业务场景：合同生效前，产品经理还要重新阅读补充协议、梳理变更章节和填写内容。我们希望系统先把可复用的历史依据找出来，让人工确认更快、更稳。"]);

s = deck.slides.add();
title(s, "合同生效前，产品经理还要重新完成一遍变更梳理");
columns(s, [
  { head: "当前做法", body: "产品经理需要根据补充协议或定稿文件，重新判断本次涉及哪些合同章节，并逐项填写对应的变更内容。" },
  { head: "复杂场景", body: "份额拆分、合并、版本更新等变更可能涉及多个章节。人工逐项阅读和勾选耗时，也容易漏选或选错。" },
  { head: "时间间隔", body: "定稿到实际生效可能相隔较久，业务人员需要重新打开材料、重新梳理；特殊的人工勾选也更容易被遗漏。" },
]);
note(s, "一个合同生效流程平均耗时约 10 到 20 分钟，主要是重复性的阅读、判断和填写。", 574, C.amber);
footer(s, 2);
notes(s, ["这一页只讲现状，不急着讲技术。可以举例：一个补充协议涉及多个章节时，产品经理到了生效阶段还要重新打开文件，从头梳理一遍；事情并不复杂，但很耗时间，也容易因为时间间隔而遗漏。", "需求说明书记录的产品经理反馈是：一个生效流程平均耗时 10 到 20 分钟。"]);

s = deck.slides.add();
title(s, "系统先整理候选依据，业务人员把精力放在最终确认");
flow(s, ["导入确认过的\n历史样本", "生成段落\n语义向量", "保存到\nOracle", "加载到\n内存索引", "新段落\n发起预测", "返回类型\n和参考依据"], 226);
text(s, "explain", "系统不替代产品经理做最终判断，而是先找出相似历史段落和候选类型，减少每次都从头梳理。", 110, 424, 1000, 42, { size: 23, bold: true });
note(s, "先减少重复工作，再通过人工确认守住业务准确性。", 500, C.green);
footer(s, 3);
notes(s, ["从现状自然过渡到方案：过去确认过的相似段落其实已经包含判断经验，系统先把这些经验找回来，产品经理只需要结合当前合同上下文确认或修正。", "这里再引出技术实现：段落向量化、相似检索和多标签投票，都是为了把历史依据更快地摆到业务人员面前。"]);

s = deck.slides.add();
title(s, "历史样本导入时，系统把段落变成可比较的知识");
columns(s, [
  { head: "Excel 输入", body: "每行是一段历史合同段落，以及人工确认过的一个或多个变更类型编码。" },
  { head: "文本处理", body: "系统会规范化文本并计算 Hash。完全相同的段落可以快速识别，也能避免重复沉淀。" },
  { head: "向量生成", body: "调用公司统一 Embedding 网关，把段落转成 1024 维向量，再保存为历史样本。" },
]);
footer(s, 4);
notes(s, ["这里可以稍微讲技术，但用业务语言解释：Hash 是为了认出完全相同的段落，向量是为了比较表达相近的段落。"]);

s = deck.slides.add();
title(s, "模型只负责理解文字，不直接决定变更类型");
columns(s, [
  { head: "准备请求", body: "段落经过规范化后送往公司统一 Embedding 网关。请求携带业务 UserId，并明确指定模型、1024 维和 float 格式。" },
  { head: "生成向量", body: "模型把一段文字转换成 1024 个浮点数。它表达的是段落语义位置，不会直接输出 20、25、43 这类业务编码。" },
  { head: "校验结果", body: "服务会校验返回数量、顺序、维度和非法数值，再做 L2 归一化，让后续点积可以直接表示余弦相似度。" },
]);
note(s, "模型解决“文字怎样比较”，业务类型仍由历史证据和投票规则决定。", 574, C.green);
footer(s, 5);
notes(s, ["这一页把模型边界讲清楚。可以说：模型像一个语义翻译器，把文字翻译成可比较的数字；最后属于什么变更类型，仍由历史样本和规则共同决定。", "请求关键字段：model=gen-studio-Qwen3-Embedding-8B，dimensions=1024，encoding_format=float。"]);

s = deck.slides.add();
title(s, "向量入库后，查询依靠一份完整、稳定的内存快照");
flow(s, ["Oracle 保存\n历史知识", "读取当前模型\n有效样本", "BLOB 还原为\nfloat[1024]", "构建列表和\nHash 查找表", "完整后一次性\n替换旧快照"], 226);
bullets(s, [
  "Oracle 负责持久保存，服务重启后知识仍然存在",
  "Hash 查找表处理完全相同段落，向量列表处理语义相似段落",
  "重新加载期间继续使用旧快照，避免预测请求读到一半数据",
], 112, 402, 1020, 52, 20);
note(s, "可以把 Oracle 理解为知识仓库，把 JVM 快照理解为预测时的工作台。", 570, C.green);
footer(s, 6);
notes(s, ["强调这里的“索引”不是向量数据库索引，而是一份只读内存快照。数据库保存数据，JVM 负责快速使用数据。", "当前快照包含历史向量列表和文本 Hash Map；AtomicReference 在新快照完整构建后整体切换。"]);

s = deck.slides.add();
title(s, "预测时，先找完全相同，再找语义相近");
flow(s, ["新段落\n规范化", "Hash 精确\n命中", "未命中才\n调用模型", "找 Top 10\n相似样本", "按相似度\n加权投票", "输出候选\n变更类型"], 226);
text(s, "rule", "这套顺序的好处是：能精确复用的先复用，需要语义判断时再调用模型，结果也能追溯到历史参考段落。", 108, 430, 1020, 42, { size: 23, bold: true });
footer(s, 7);
notes(s, ["这一页自然讲算法流程。不要一上来讲阈值，先讲为什么这样排顺序：省调用、可追溯、符合人的判断方式。"]);

s = deck.slides.add();
title(s, "投票不是只看第一条，而是看相似证据能否形成共识");
text(s, "evidence-head", "进入投票的历史证据（简化示例）", 82, 214, 600, 34, { size: 22, bold: true });
[
  ["样本 A", "similarity 0.90", "权重 0.90² = 0.81", "类型 20、25"],
  ["样本 B", "similarity 0.80", "权重 0.80² = 0.64", "类型 20"],
  ["样本 C", "similarity 0.70", "权重 0.70² = 0.49", "类型 28"],
].forEach((row, i) => {
  const top = 264 + i * 82;
  box(s, `vote-row-${i}`, 82, top, 610, 64, i === 0 ? C.panel2 : C.panel);
  text(s, `vote-name-${i}`, row[0], 102, top + 18, 84, 28, { size: 19, bold: true });
  text(s, `vote-sim-${i}`, row[1], 190, top + 18, 142, 28, { size: 18 });
  text(s, `vote-weight-${i}`, row[2], 340, top + 18, 184, 28, { size: 18 });
  text(s, `vote-code-${i}`, row[3], 532, top + 18, 142, 28, { size: 18, bold: true, color: C.accent });
});
box(s, "score-panel", 730, 214, 478, 296, C.panel2);
text(s, "score-head", "类型 20 的证据得分", 762, 242, 410, 38, { size: 24, bold: true });
text(s, "score-formula-1", "总权重 = 0.81 + 0.64 + 0.49 = 1.94", 762, 310, 410, 30, { size: 19 });
text(s, "score-formula-2", "类型20权重 = 0.81 + 0.64 = 1.45", 762, 358, 410, 30, { size: 19 });
text(s, "score-result", "score = 1.45 / 1.94 = 0.7474", 762, 414, 410, 36, { size: 24, bold: true, color: C.green });
text(s, "score-support", "supportCount = 2  →  CANDIDATE", 762, 462, 410, 30, { size: 19, bold: true });
note(s, "similarity 是两段文字有多像；score 是历史证据对某个类型的综合支持度。", 554, C.green);
footer(s, 8);
notes(s, ["用这一组数字带着听众算一遍即可。相似度平方让更相似的样本影响更大，但不会让第一条样本直接决定结果。", "分母是全部投票样本权重之和；多标签样本会同时支持多个类型，所以各类型 score 之和不一定等于 1。"]);

s = deck.slides.add();
title(s, "HIGH 和 CANDIDATE 对应不同的业务使用方式");
bullets(s, [
  "HIGH：相似历史证据比较集中，可以作为优先判断方向",
  "CANDIDATE：有参考价值，但需要结合合同上下文再确认",
  "强相似兜底：如果第一条历史段落非常像，即使投票不集中，也先作为候选给业务参考",
  "NO_RELIABLE_MATCH：知识库里没有足够依据时，不强行给结论",
], 112, 225, 1030, 58, 22);
note(s, "这不是替代人工，而是把“可参考依据”提前摆出来。", 514, C.green);
footer(s, 9);
notes(s, ["这一页面向业务解释输出等级。强相似兜底要说成候选参考，不要说成系统高置信。"]);

s = deck.slides.add();
title(s, "为什么第一版不引入向量数据库");
columns(s, [
  { head: "样本规模可控", body: "第一版最多约 1 万条历史样本。这个规模下，Java 内存里直接比较向量就能满足使用。" },
  { head: "部署更轻", body: "少引入一个中间组件，测试、部署、权限、备份和排障都会简单不少。" },
  { head: "后续可扩展", body: "如果以后样本量明显变大，或多实例检索压力上来，再评估专门的向量检索组件更合适。" },
]);
footer(s, 10);
notes(s, ["技术选择要讲原因，不要像辩解。核心是第一版规模不大，先轻量落地，未来再按规模升级。"]);

s = deck.slides.add();
title(s, "1024 维和 Oracle + JVM，是当前规模下的平衡选择");
columns(s, [
  { head: "1024 维", body: "统一模型默认能力更大，但本项目固定请求 1024 维。对当前场景来说，表达能力够用，也能降低存储和计算压力。" },
  { head: "Oracle 保存", body: "历史段落、类型、Hash、模型版本和向量都落在 Oracle，便于和现有系统管理方式保持一致。" },
  { head: "JVM 索引", body: "服务启动或重载时，把当前有效样本加载成只读索引。查询时读稳定快照，导入后再整体刷新。" },
]);
footer(s, 11);
notes(s, ["这里保留核心技术实现，但都加业务解释：1024 是成本和效果平衡，Oracle 是现有管理习惯，JVM 索引是简单快速。"]);

s = deck.slides.add();
title(s, "模型网关和安全边界按企业接入方式处理");
bullets(s, [
  "Embedding 调用走公司统一模型网关，不在本服务里自建模型",
  "请求会携带业务 UserId 给模型平台审计，但服务侧不保存、不输出日志",
  "API Key 只通过环境变量提供，不进入代码、Git 或演示材料",
  "日志只保留必要的数量、状态和耗时，不打印合同正文和向量",
  "模型版本和向量维度会记录下来，避免新旧模型结果混在一起使用",
], 96, 216, 1060, 52, 21);
footer(s, 12);
notes(s, ["这页适合技术和管理都听得懂：统一网关、安全、审计、敏感信息保护、模型版本隔离。"]);

s = deck.slides.add();
title(s, "知识库完善要走人工确认，避免错误经验被反复引用");
flow(s, ["文档比对\n发现新段落", "系统给出\n候选类型", "业务人员\n确认或修正", "整理为\n历史样本", "导入知识库", "后续识别\n继续复用"], 226);
text(s, "explain", "预测结果不直接自动入库。只有人工确认过的段落和类型，才适合作为后续识别的历史依据。", 112, 424, 1020, 42, { size: 23, bold: true });
note(s, "这一步看起来多一道确认，但能保证知识库越用越稳。", 500, C.green);
footer(s, 13);
notes(s, ["这是用户新增需求的核心页。讲法要自然：不是自动学习，而是有质量把关的经验沉淀。"]);

s = deck.slides.add();
title(s, "现场演示按业务流程推进，也顺手证明技术闭环");
bullets(s, [
  "看索引状态：确认当前知识库是否可用、样本是否已加载",
  "导入匿名 Excel：展示历史经验如何进入知识库",
  "Oracle 只读核验：确认样本、类型、模型版本和向量已保存",
  "预测 EXACT / HIGH / CANDIDATE / NO_RELIABLE_MATCH：展示不同业务场景",
  "手工 reload：展示知识库刷新和运维恢复能力",
], 96, 216, 1060, 52, 21);
footer(s, 14);
notes(s, ["演示时别硬念接口名。每一步都讲成一个业务动作，同时让技术同事看到数据确实落库、索引确实可观测。"]);

s = deck.slides.add();
title(s, "准确性和上线准备，需要用真实样本把结论补齐");
columns(s, [
  { head: "演示说明流程", body: "演示样本可以证明导入、识别、参考依据和知识库补充这条链路是通的。" },
  { head: "测试说明效果", body: "真实效果要用未入库的合同段落验证，建议准备 30 到 100 条人工标注样本。" },
  { head: "上线前确认", body: "完成构建验证、环境连通、匿名演示数据、备用录屏和准确性验证表。" },
]);
footer(s, 15);
notes(s, ["最后不要声称准确率。自然讲：流程演示和效果评估是两件事，演示完成后还要用真实样本做验收。"]);

await fs.mkdir(BUILD_DIR, { recursive: true });
for (const [index, slide] of deck.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  await writeBlob(`${BUILD_DIR}/${stem}.png`, await deck.export({ slide, format: "png", scale: 1 }));
  await fs.writeFile(`${BUILD_DIR}/${stem}.layout.json`, await (await slide.export({ format: "layout" })).text(), "utf8");
}
await writeBlob(`${BUILD_DIR}/deck-montage.webp`, await deck.export({ format: "webp", montage: true, scale: 1 }));
const inspect = await deck.inspect({ kind: "slide,textbox,shape,notes", maxChars: 50000 });
await fs.writeFile(`${BUILD_DIR}/inspect.ndjson`, inspect.ndjson, "utf8");
const pptx = await PresentationFile.exportPptx(deck);
await pptx.save(FINAL);
console.log(FINAL);
