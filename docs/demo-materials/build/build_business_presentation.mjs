import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT_DIR = "D:/projects/glxt-service-contract-change/docs/demo-materials";
const BUILD_DIR = `${OUT_DIR}/build/business_ppt_render`;
const FINAL = `${OUT_DIR}/合同段落变更类型识别服务-业务汇报版.pptx`;

const C = {
  ink: "#111111",
  muted: "#58606E",
  rule: "#C9CED6",
  panel: "#F4F6F8",
  panel2: "#EAF1F8",
  accent: "#2F80ED",
  green: "#1B8A5A",
  amber: "#A46300",
  white: "#FFFFFF",
};

async function writeBlob(path, blob) {
  await fs.writeFile(path, new Uint8Array(await blob.arrayBuffer()));
}

function box(slide, name, left, top, width, height, fill = C.panel, line = "#DDE2E8") {
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
  text(slide, "title", value, 72, 84, 1050, 88, { size: 38, bold: true });
  box(slide, "rule", 72, 182, 150, 3, C.accent, C.accent);
}

function footer(slide, n) {
  text(slide, "footer", String(n).padStart(2, "0"), 1160, 666, 48, 28, {
    size: 16,
    bold: true,
    color: C.muted,
    align: "right",
  });
}

function bullets(slide, items, left, top, width, gap = 50, size = 22) {
  items.forEach((item, i) => {
    box(slide, `dot-${i}`, left, top + i * gap + 12, 8, 8, C.accent, C.accent);
    text(slide, `bullet-${i}`, item, left + 24, top + i * gap, width - 24, 34, { size });
  });
}

function columns(slide, cols, top = 232) {
  const gap = 28;
  const width = (1136 - gap * (cols.length - 1)) / cols.length;
  cols.forEach((col, i) => {
    const left = 72 + i * (width + gap);
    box(slide, `col-${i}`, left, top, width, 318, i === 1 ? C.panel2 : C.panel);
    text(slide, `col-head-${i}`, col.head, left + 24, top + 26, width - 48, 40, {
      size: 25,
      bold: true,
    });
    text(slide, `col-body-${i}`, col.body, left + 24, top + 88, width - 48, 180, {
      size: 19,
      color: C.muted,
    });
  });
}

function flow(slide, steps, top = 244) {
  const gap = 16;
  const width = (1136 - gap * (steps.length - 1)) / steps.length;
  steps.forEach((step, i) => {
    const left = 72 + i * (width + gap);
    box(slide, `step-${i}`, left, top, width, 112, i % 2 ? C.panel2 : C.panel);
    text(slide, `num-${i}`, String(i + 1), left + 18, top + 16, 34, 30, {
      size: 23,
      bold: true,
      color: C.accent,
    });
    text(slide, `step-text-${i}`, step, left + 54, top + 16, width - 66, 72, {
      size: 18,
      bold: true,
    });
  });
}

function notes(slide, lines) {
  slide.speakerNotes.textFrame.setText([...lines, "", "[Sources]", "基于项目 README、接口文档、数据库脚本和当前需求上下文整理。"].join("\n"));
  slide.speakerNotes.setVisible(true);
}

const deck = Presentation.create({ slideSize: { width: 1280, height: 720 } });
let s;

s = deck.slides.add();
s.background.fill = C.white;
text(s, "label", "业务汇报版", 72, 64, 260, 30, { size: 18, bold: true, color: C.muted });
text(s, "cover-title", "合同段落变更类型识别服务", 72, 175, 900, 90, { size: 54, bold: true });
text(s, "cover-sub", "把历史判断经验沉淀下来，帮助新合同段落更快找到可能的变更类型", 76, 300, 930, 42, { size: 24, color: C.muted });
box(s, "cover-band", 72, 436, 1030, 94, C.panel2);
text(s, "cover-band-text", "建议汇报方式：先讲业务闭环，再做 8-10 分钟现场演示", 100, 466, 960, 36, { size: 24, bold: true });
footer(s, 1);
notes(s, ["开场可以这样说：今天主要汇报这个服务能帮业务解决什么问题、怎么使用、结果怎么看，以及后续怎么让知识库越来越完整。"]);

s = deck.slides.add();
title(s, "我们要解决的不是单次识别，而是历史经验难复用");
columns(s, [
  { head: "现在的情况", body: "文档比对后会产生不少变化段落，业务人员需要逐段判断属于哪些变更类型。" },
  { head: "主要问题", body: "判断过程依赖人工经验。类似段落过去可能已经处理过，但新一轮工作里很难快速复用。" },
  { head: "服务目标", body: "系统先给出候选变更类型和参考段落，让人工判断有依据，也让历史经验能持续沉淀。" },
]);
footer(s, 2);
notes(s, ["这一页从业务痛点讲，不要急着讲模型。重点是：它减少重复判断，不替代最终业务确认。"]);

s = deck.slides.add();
title(s, "第一版先把最有价值的闭环跑通");
flow(s, ["导入历史\n段落样本", "系统学习\n段落特征", "新段落\n发起识别", "返回候选\n变更类型", "展示相似\n历史依据", "人工确认\n继续沉淀"], 230);
text(s, "explain", "这条链路的核心价值，是让“过去怎么判”的经验，在下一次遇到相似段落时能被看见、被复用。", 118, 430, 1000, 44, {
  size: 24,
  bold: true,
});
footer(s, 3);
notes(s, ["自然承接：我们不是做一个只能回答一次的模型，而是做一个可维护的知识库闭环。"]);

s = deck.slides.add();
title(s, "使用方式尽量简单，业务侧只需要理解四件事");
bullets(s, [
  "先把确认过的历史段落和变更类型整理成 Excel 导入",
  "新段落识别时，系统会先看是否与历史段落完全一致",
  "如果不是完全一致，系统会找最相似的历史段落作为参考",
  "结果只作为辅助建议，最终仍由业务人员确认",
  "确认后的新样本可以再补充进知识库，后续识别会更有依据",
], 96, 220, 1040, 54, 22);
footer(s, 4);
notes(s, ["这页可以用很口语的说法讲：业务同事不用关心底层怎么计算，只要知道导入、识别、参考、确认、沉淀。"]);

s = deck.slides.add();
title(s, "结果里最需要看懂的是“像不像”和“支持不支持”");
columns(s, [
  { head: "像不像", body: "系统会给出新段落和历史段落的相似程度。相似度越高，说明这两段文本越接近。" },
  { head: "支持不支持", body: "系统会综合相似历史段落，计算每个变更类型得到多少历史证据支持。" },
  { head: "怎么看结论", body: "HIGH 表示历史证据比较集中；CANDIDATE 表示可以参考，但需要人工进一步判断。" },
]);
footer(s, 5);
notes(s, ["避免说 score、TopK 等术语。可以补一句：分数不是概率，只是历史证据的支持程度。"]);

s = deck.slides.add();
title(s, "系统会给建议，也会在证据不足时明确说不可靠");
bullets(s, [
  "完全相同的历史段落：直接返回历史上确认过的类型",
  "相似度较高且证据集中：返回高可信建议",
  "有相似段落但证据还不够集中：返回候选建议和参考依据",
  "没有足够相似的历史样本：不强行给结论，提示无可靠匹配",
], 120, 230, 980, 58, 23);
box(s, "note", 120, 510, 940, 62, "#FFF8E8", "#E4C783");
text(s, "note-text", "这能避免为了“看起来有结果”而给出误导性判断。", 148, 528, 880, 30, { size: 22, bold: true, color: C.amber });
footer(s, 6);
notes(s, ["这一页是信任建设。业务上最怕系统乱给结论，所以要强调：证据不足时会留给人工判断。"]);

s = deck.slides.add();
title(s, "现场演示建议按业务流程来讲");
bullets(s, [
  "先看知识库状态：确认现在有多少历史样本可参与识别",
  "导入一份匿名历史样本：展示知识库如何补充",
  "预测一段已存在的段落：展示完全一致时的快速返回",
  "预测一段相似的新段落：展示候选类型和参考段落",
  "预测一段无关内容：展示系统不会硬给结论",
], 96, 220, 1040, 54, 22);
footer(s, 7);
notes(s, ["演示顺序从业务可理解的流程出发，不要先讲接口名。可以边操作边解释每一步对应的业务动作。"]);

s = deck.slides.add();
title(s, "知识库完善要走人工确认，不能让系统自己循环学习");
columns(s, [
  { head: "比对后发现新段落", body: "文档比对完成后，可以把有价值的新段落整理出来，形成待确认样本。" },
  { head: "人工确认类型", body: "业务人员确认或修正变更类型。确认过的内容才适合作为新的历史经验。" },
  { head: "补充回知识库", body: "确认后的段落再次导入。以后遇到相似段落，系统就能引用这部分新增经验。" },
]);
footer(s, 8);
notes(s, ["这是新增点的核心页。可以说：这不是自动学习，而是有把关的经验沉淀。"]);

s = deck.slides.add();
title(s, "为什么不让预测结果直接入库");
bullets(s, [
  "预测结果只是建议，不等同于业务确认",
  "如果误判直接入库，后续相似段落可能继续引用这个错误",
  "人工确认能保证知识库质量，尤其适合合同这类需要审慎判断的场景",
  "对系统来说，宁可慢一点沉淀，也要保证沉淀进去的是可信经验",
], 112, 230, 1010, 58, 23);
footer(s, 9);
notes(s, ["这页用业务语言讲风险。不要说模型污染、反馈环太多术语，可以说错误经验被反复引用。"]);

s = deck.slides.add();
title(s, "准确性要用真实业务样本来验证");
columns(s, [
  { head: "演示能证明什么", body: "证明导入、识别、返回参考依据、人工补充知识库这条流程能跑通。" },
  { head: "还需要验证什么", body: "需要用未进入历史库的真实段落，让业务人员先标注标准答案，再看系统表现。" },
  { head: "建议验收口径", body: "准备 30-100 条段落，统计命中情况、高可信建议质量、无可靠匹配比例和可补充样本。" },
]);
footer(s, 10);
notes(s, ["这页要讲得坦诚自然：现在没有真实测试集，所以不提前承诺准确率；我们准备好验证方法。"]);

s = deck.slides.add();
title(s, "这版汇报的结论可以收在三个判断上");
bullets(s, [
  "它能把历史判断经验变成可复用的知识库",
  "它能在新段落出现时给出候选类型和相似依据，帮助人工更快判断",
  "它能通过人工确认后的新增样本持续完善，但不会把未经确认的建议自动入库",
], 116, 245, 1000, 72, 24);
box(s, "close", 116, 540, 930, 54, C.panel2);
text(s, "close-text", "适合作为第一版先上线试用，再结合真实样本逐步验证和优化。", 144, 554, 880, 28, { size: 22, bold: true });
footer(s, 11);
notes(s, ["结尾回到业务价值：先可用、可解释、可持续完善。不要以技术细节结束。"]);

await fs.mkdir(BUILD_DIR, { recursive: true });
for (const [index, slide] of deck.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  await writeBlob(`${BUILD_DIR}/${stem}.png`, await deck.export({ slide, format: "png", scale: 1 }));
  await fs.writeFile(`${BUILD_DIR}/${stem}.layout.json`, await (await slide.export({ format: "layout" })).text(), "utf8");
}
await writeBlob(`${BUILD_DIR}/deck-montage.webp`, await deck.export({ format: "webp", montage: true, scale: 1 }));
const inspect = await deck.inspect({ kind: "slide,textbox,shape,notes", maxChars: 10000 });
await fs.writeFile(`${BUILD_DIR}/inspect.ndjson`, inspect.ndjson, "utf8");
const pptx = await PresentationFile.exportPptx(deck);
await pptx.save(FINAL);
console.log(FINAL);
