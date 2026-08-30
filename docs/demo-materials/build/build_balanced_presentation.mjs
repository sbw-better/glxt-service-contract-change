import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT_DIR = "D:/projects/glxt-service-contract-change/docs/demo-materials";
const BUILD_DIR = `${OUT_DIR}/build/balanced_ppt_render`;
const FINAL = `${OUT_DIR}/合同段落变更类型识别服务-综合汇报版.pptx`;

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
  slide.speakerNotes.textFrame.setText([...lines, "", "[Sources]", "项目 README、接口文档、Oracle 脚本、当前需求上下文。"].join("\n"));
  slide.speakerNotes.setVisible(true);
}

const deck = Presentation.create({ slideSize: { width: 1280, height: 720 } });
let s;

s = deck.slides.add();
s.background.fill = C.white;
text(s, "kicker", "综合汇报版", 72, 62, 300, 30, { size: 18, bold: true, color: C.muted });
text(s, "cover-title", "合同段落变更类型识别服务", 72, 170, 900, 90, { size: 54, bold: true });
text(s, "cover-sub", "用历史段落经验辅助新段落识别，并让确认后的新样本继续沉淀", 76, 300, 980, 42, { size: 24, color: C.muted });
box(s, "cover-band", 72, 436, 1030, 94, C.panel2);
text(s, "cover-band-text", "适合业务 + 技术混合汇报：讲清楚价值、流程、实现思路和质量边界", 100, 466, 960, 36, { size: 23, bold: true });
footer(s, 1);
notes(s, ["开场不先讲参数，先讲这件事的目标：把过去确认过的经验用起来，帮助新段落判断更快、更有依据。"]);

s = deck.slides.add();
title(s, "这项能力的起点，是历史判断经验没有被充分复用");
columns(s, [
  { head: "业务场景", body: "合同文档比对后，会产生一批变化段落。业务人员需要判断这些段落分别属于哪些变更类型。" },
  { head: "现实问题", body: "很多相似段落过去其实处理过，但经验散在历史材料里，新一轮判断时不容易马上找到。" },
  { head: "建设目标", body: "让系统先找出相似历史段落和候选类型，业务人员再结合上下文做最终确认。" },
]);
footer(s, 2);
notes(s, ["这一页讲业务背景。自然表述：我们不是为了做一个模型而做模型，而是为了把历史判断经验变成可复用的依据。"]);

s = deck.slides.add();
title(s, "第一版先做一条简单可用、容易解释的闭环");
flow(s, ["导入确认过的\n历史样本", "生成段落\n语义向量", "保存到\nOracle", "加载到\n内存索引", "新段落\n发起预测", "返回类型\n和参考依据"], 226);
text(s, "explain", "业务上看到的是“历史经验被复用”；技术上做的是“段落向量化 + 相似检索 + 多标签投票”。", 110, 424, 1000, 42, { size: 23, bold: true });
note(s, "第一版先把核心闭环跑通，不急着引入更重的组件。", 500, C.amber);
footer(s, 3);
notes(s, ["把业务和技术接起来讲：业务同事理解为经验复用，技术同事理解为语义检索和投票。"]);

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
title(s, "预测时，先找完全相同，再找语义相近");
flow(s, ["新段落\n规范化", "Hash 精确\n命中", "未命中才\n调用模型", "找 Top 10\n相似样本", "按相似度\n加权投票", "输出候选\n变更类型"], 226);
text(s, "rule", "这套顺序的好处是：能精确复用的先复用，需要语义判断时再调用模型，结果也能追溯到历史参考段落。", 108, 430, 1020, 42, { size: 23, bold: true });
footer(s, 5);
notes(s, ["这一页自然讲算法流程。不要一上来讲阈值，先讲为什么这样排顺序：省调用、可追溯、符合人的判断方式。"]);

s = deck.slides.add();
title(s, "结果不是一句“模型说了算”，而是带着依据回来");
columns(s, [
  { head: "similarity", body: "表示新段落和某条历史段落有多像。它回答的是：这两段文本接近吗？" },
  { head: "score", body: "表示历史样本对某个变更类型的支持程度。它回答的是：这些相似历史证据支持哪个类型？" },
  { head: "参考段落", body: "系统会把相似历史段落一起返回，让业务人员能看到建议来自哪里，而不是只看一个标签。" },
]);
footer(s, 6);
notes(s, ["这里要讲清楚 similarity 和 score 的区别。可以说 score 不是概率，而是历史证据支持度。"]);

s = deck.slides.add();
title(s, "HIGH 和 CANDIDATE 对应不同的业务使用方式");
bullets(s, [
  "HIGH：相似历史证据比较集中，可以作为优先判断方向",
  "CANDIDATE：有参考价值，但需要结合合同上下文再确认",
  "强相似兜底：如果第一条历史段落非常像，即使投票不集中，也先作为候选给业务参考",
  "NO_RELIABLE_MATCH：知识库里没有足够依据时，不强行给结论",
], 112, 225, 1030, 58, 22);
note(s, "这不是替代人工，而是把“可参考依据”提前摆出来。", 514, C.green);
footer(s, 7);
notes(s, ["这一页面向业务解释输出等级。强相似兜底要说成候选参考，不要说成系统高置信。"]);

s = deck.slides.add();
title(s, "为什么第一版不引入向量数据库");
columns(s, [
  { head: "样本规模可控", body: "第一版最多约 1 万条历史样本。这个规模下，Java 内存里直接比较向量就能满足使用。" },
  { head: "部署更轻", body: "少引入一个中间组件，测试、部署、权限、备份和排障都会简单不少。" },
  { head: "后续可扩展", body: "如果以后样本量明显变大，或多实例检索压力上来，再评估专门的向量检索组件更合适。" },
]);
footer(s, 8);
notes(s, ["技术选择要讲原因，不要像辩解。核心是第一版规模不大，先轻量落地，未来再按规模升级。"]);

s = deck.slides.add();
title(s, "为什么固定 1024 维，并用 Oracle + JVM 索引");
columns(s, [
  { head: "1024 维", body: "统一模型默认能力更大，但本项目固定请求 1024 维。对当前场景来说，表达能力够用，也能降低存储和计算压力。" },
  { head: "Oracle 保存", body: "历史段落、类型、Hash、模型版本和向量都落在 Oracle，便于和现有系统管理方式保持一致。" },
  { head: "JVM 索引", body: "服务启动或重载时，把当前有效样本加载成只读索引。查询时读稳定快照，导入后再整体刷新。" },
]);
footer(s, 9);
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
footer(s, 10);
notes(s, ["这页适合技术和管理都听得懂：统一网关、安全、审计、敏感信息保护、模型版本隔离。"]);

s = deck.slides.add();
title(s, "知识库完善要走人工确认，避免错误经验被反复引用");
flow(s, ["文档比对\n发现新段落", "系统给出\n候选类型", "业务人员\n确认或修正", "整理为\n历史样本", "导入知识库", "后续识别\n继续复用"], 226);
text(s, "explain", "预测结果不直接自动入库。只有人工确认过的段落和类型，才适合作为后续识别的历史依据。", 112, 424, 1020, 42, { size: 23, bold: true });
note(s, "这一步看起来多一道确认，但能保证知识库越用越稳。", 500, C.green);
footer(s, 11);
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
footer(s, 12);
notes(s, ["演示时别硬念接口名。每一步都讲成一个业务动作，同时让技术同事看到数据确实落库、索引确实可观测。"]);

s = deck.slides.add();
title(s, "准确性和上线准备，需要用真实样本把结论补齐");
columns(s, [
  { head: "演示说明流程", body: "演示样本可以证明导入、识别、参考依据和知识库补充这条链路是通的。" },
  { head: "测试说明效果", body: "真实效果要用未入库的合同段落验证，建议准备 30 到 100 条人工标注样本。" },
  { head: "上线前确认", body: "完成构建验证、环境连通、匿名演示数据、备用录屏和准确性验证表。" },
]);
footer(s, 13);
notes(s, ["最后不要声称准确率。自然讲：流程演示和效果评估是两件事，演示完成后还要用真实样本做验收。"]);

await fs.mkdir(BUILD_DIR, { recursive: true });
for (const [index, slide] of deck.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  await writeBlob(`${BUILD_DIR}/${stem}.png`, await deck.export({ slide, format: "png", scale: 1 }));
  await fs.writeFile(`${BUILD_DIR}/${stem}.layout.json`, await (await slide.export({ format: "layout" })).text(), "utf8");
}
await writeBlob(`${BUILD_DIR}/deck-montage.webp`, await deck.export({ format: "webp", montage: true, scale: 1 }));
const inspect = await deck.inspect({ kind: "slide,textbox,shape,notes", maxChars: 12000 });
await fs.writeFile(`${BUILD_DIR}/inspect.ndjson`, inspect.ndjson, "utf8");
const pptx = await PresentationFile.exportPptx(deck);
await pptx.save(FINAL);
console.log(FINAL);
