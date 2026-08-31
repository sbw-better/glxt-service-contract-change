import json
import re
from pathlib import Path

from docx import Document


SOURCE_DOCX = Path(r"C:\Users\hyd\Desktop\丹羿精选12号1期私募证券投资基金基金合同（2024-1）-终版.docx")
OUT_DIR = Path(r"D:\projects\glxt-service-contract-change\docs\demo-materials\contract-sample-tests\build")
OUT_JSON = OUT_DIR / "contract_test_dataset.json"


CODE_GROUPS = [
    "20",
    "25",
    "28",
    "43",
    "57",
    "20;25",
    "20;28",
    "25;28",
    "20;25;28",
    "20;25;28;43;57",
    "35;40;45",
    "26;54;76",
    "11;18",
    "32;33;34",
]


def build_prediction_validation_rows():
    """Build controlled semantic clusters for deterministic voting-path verification."""
    rows = [
        {
            "paragraph": "预测验证精确命中样本：基金托管人应依照合同约定保管基金财产，并对托管账户资金收付情况进行核对。",
            "codes": "20",
            "cluster": "EXACT",
            "design": "精确命中种子"
        }
    ]

    high_single = [
        "募集资金前，私募基金管理人应已在中国证券投资基金业协会完成登记并取得管理人登记编码。",
        "基金开始募集之前，管理人须完成中国证券投资基金业协会登记，并取得有效的私募基金管理人登记编号。",
        "私募基金对外募集资金以前，基金管理人应当在基金业协会办妥登记手续并取得登记编码。",
        "开展基金募集活动之前，管理人需要已经完成中国证券投资基金业协会登记并持有管理人登记编码。",
        "管理人在募集本基金资金前，应确认其基金业协会登记状态有效并已经取得相应登记编号。",
        "本基金募集开始前，私募基金管理人应依法完成协会登记，具备有效的管理人登记编码。",
        "私募基金管理人只有在中国证券投资基金业协会完成登记并取得编码后，方可开展本基金募集工作。",
        "在向投资者募集基金资金之前，管理人应完成基金业协会登记程序并取得私募管理人登记编码。",
        "管理人开展募集业务的前提，是已在中国证券投资基金业协会登记并取得有效登记编号。",
        "基金募集前应核验私募基金管理人的协会登记信息以及管理人登记编码是否完整有效。",
    ]
    rows.extend({
        "paragraph": "预测验证主题A：私募基金管理人登记合规。" + text,
        "codes": "43",
        "cluster": "HIGH_SINGLE_43",
        "design": "10条近邻全部支持43"
    } for text in high_single)

    high_multi = [
        "管理人应向投资者充分揭示本基金的投资范围、风险特征和可能发生的损失。",
        "基金募集过程中，管理人需要说明产品投资范围、主要风险以及投资者可能承担的损失。",
        "管理人应当以清晰方式披露基金投资方向、风险收益特征和潜在损失情形。",
        "向投资者介绍本基金时，应完整说明投资范围、风险因素及可能造成的本金损失。",
        "基金管理人需就产品的投资标的、风险特征以及潜在损失向投资者作出充分说明。",
        "在投资者认购前，管理人应披露基金投资范围、主要风险和可能产生的不利后果。",
        "本基金募集前应向投资者揭示投资领域、风险收益特征及投资损失的可能性。",
        "管理人应确保投资者了解基金投资范围、重要风险事项和可能承担的损失。",
        "产品推介材料应准确说明本基金投资范围、风险等级以及潜在损失。",
        "投资者作出认购决定前，管理人应充分揭示基金投资方向、风险特征与损失风险。",
    ]
    rows.extend({
        "paragraph": "预测验证主题B：投资范围与风险揭示。" + text,
        "codes": "20;25;28",
        "cluster": "HIGH_MULTI_20_25_28",
        "design": "10条近邻共同支持20、25、28"
    } for text in high_multi)

    candidate = [
        "投资者认购前应完成风险承受能力评估，并确认产品风险等级与自身承受能力相匹配。",
        "募集机构应在投资者认购基金前完成适当性评估，核验投资者风险承受能力与产品风险等级。",
        "基金销售前需要评估投资者风险承受能力，并确认其与基金风险等级相适应。",
        "投资者参与本基金前，应完成风险测评和适当性匹配确认。",
        "募集机构应根据风险测评结果判断投资者是否适合认购本基金。",
        "认购申请受理前，应核实投资者风险承受等级与基金产品风险等级是否匹配。",
        "投资者购买基金前应阅读风险揭示内容，并确认已经理解产品可能发生的投资损失。",
        "募集机构应向投资者说明基金风险特征，并取得投资者对风险揭示事项的确认。",
        "投资者认购前需要确认已充分了解本基金风险以及可能承担的损失。",
        "基金销售过程中应完成风险揭示，并由投资者确认其理解产品风险。",
    ]
    rows.extend({
        "paragraph": "预测验证主题C：投资者适当性与风险确认。" + text,
        "codes": "25" if index < 6 else "28",
        "cluster": "CANDIDATE_25_VS_28",
        "design": "6条支持25、4条支持28"
    } for index, text in enumerate(candidate))

    fallback = [
        ("57", "管理人客观上丧失继续履职能力时，应立即承担首要处置责任，启动基金财产安全保障及应急接管预案。"),
        ("20", "管理人不能继续履职时，应及时通知托管人并采取措施保障托管账户和基金财产安全。"),
        ("25", "管理人丧失管理能力后，应向投资者披露应急处置安排及基金后续运营方案。"),
        ("28", "管理人无法继续管理基金时，应提示投资者相关风险并说明可能产生的不利影响。"),
        ("43", "管理人不能履行职责时，应由符合条件的临时管理主体按照约定承接管理工作。"),
        ("35", "管理人丧失履职能力后，应根据合同约定决定基金是否提前终止。"),
        ("40", "管理人无法继续管理时，应启动基金清算或管理人更换程序。"),
        ("45", "管理人不能正常履职的，应按照合同约定处理管理费及相关费用结算事项。"),
        ("26", "管理人丧失持续管理能力时，应完成相关信息报告和变更备案手续。"),
        ("54", "管理人不能继续履职后，应保存业务资料并配合后续管理主体完成交接。"),
    ]
    rows.extend({
        "paragraph": "预测验证主题D：管理人丧失履职能力后的应急处置。" + text,
        "codes": code,
        "cluster": "FALLBACK_DIVERSE",
        "design": "10条近邻标签分散，第一条57与查询最接近"
    } for code, text in fallback)
    return rows


def clean_text(text):
    text = re.sub(r"\s+", " ", text or "").strip()
    text = text.replace("\u3000", " ").strip()
    return text


def text_from_table(table):
    values = []
    for row in table.rows:
        cells = [clean_text(cell.text) for cell in row.cells]
        cells = [cell for cell in cells if cell]
        if cells:
            values.append("；".join(cells))
    return values


def should_keep(text):
    if not text:
        return False
    if len(text) < 18 or len(text) > 900:
        return False
    if re.fullmatch(r"[第\d一二三四五六七八九十百、.．（）()\-\s]+", text):
        return False
    if text in {"目录", "基金合同", "风险揭示书"}:
        return False
    if text.count(".") > 8 and len(text) < 80:
        return False
    return any(ch in text for ch in "基金合同管理人托管人投资者份额资产风险费用信息披露变更")


def split_long(text):
    if len(text) <= 360:
        return [text]
    parts = re.split(r"(?<=[。；;])", text)
    result = []
    current = ""
    for part in parts:
        part = clean_text(part)
        if not part:
            continue
        if len(current) + len(part) <= 360:
            current += part
        else:
            if should_keep(current):
                result.append(current)
            current = part
    if should_keep(current):
        result.append(current)
    return result or [text[:360]]


def variant(text, idx):
    replacements = [
        ("基金管理人", "管理人"),
        ("基金托管人", "托管人"),
        ("投资者", "基金投资者"),
        ("本基金", "该基金"),
        ("基金份额", "份额"),
        ("应当", "应"),
        ("可以", "可"),
        ("不得", "不可"),
    ]
    result = text
    for source, target in replacements:
        if source in result and idx % 2 == 0:
            result = result.replace(source, target, 1)
            break
    prefixes = [
        "经本次合同比对确认，",
        "本次修订后，",
        "在更新后的合同文本中，",
        "与原合同相比，",
    ]
    suffixes = [
        "上述内容用于本次变更识别测试。",
        "该段落作为历史相似样本参与投票。",
        "该表述用于验证语义相近但文本不完全一致的场景。",
        "该样本用于补充知识库演示。",
    ]
    if idx % 3 == 0:
        result = prefixes[idx % len(prefixes)] + result
    if idx % 4 == 0:
        result = result + suffixes[idx % len(suffixes)]
    return result[:1900]


def build_dataset():
    doc = Document(str(SOURCE_DOCX))
    raw = []
    for para in doc.paragraphs:
        value = clean_text(para.text)
        if value:
            raw.append(value)
    for table in doc.tables:
        raw.extend(text_from_table(table))

    seen = set()
    base = []
    for item in raw:
        for part in split_long(item):
            part = clean_text(part)
            if should_keep(part) and part not in seen:
                seen.add(part)
                base.append(part)

    if not base:
        raise RuntimeError("No usable contract paragraphs extracted from source docx")

    normal = []
    normal_seen = set()
    target_count = 990
    idx = 0
    while len(normal) < target_count:
        text = base[idx % len(base)]
        if idx < len(base):
            paragraph = text
        else:
            paragraph = variant(text, idx)
        if paragraph in normal_seen:
            paragraph = paragraph + "（批量导入唯一性验证序号%04d）" % (idx + 1)
        while paragraph in normal_seen:
            paragraph += "补充"
        normal_seen.add(paragraph)
        codes = CODE_GROUPS[idx % len(CODE_GROUPS)]
        normal.append({
            "rowNo": len(normal) + 2,
            "paragraph": paragraph,
            "codes": codes,
            "source": "合同样例抽取" if idx < len(base) else "合同样例改写",
            "scenario": "正常导入"
        })
        idx += 1

    duplicate_seed = normal[0]["paragraph"]
    conflict_seed = normal[1]["paragraph"]
    separator_seed = normal[2]["paragraph"]
    long_para = (base[0] + " ") * ((2100 // max(1, len(base[0]))) + 2)
    long_para = long_para[:2105]

    prediction_knowledge_rows = build_prediction_validation_rows()
    prediction_cases = [
        {
            "name": "EXACT_完全相同段落",
            "paragraph": prediction_knowledge_rows[0]["paragraph"],
            "expectedMatchType": "EXACT",
            "expectedCodes": "20",
            "expectedLevel": "HIGH",
            "expectedSupport": "1",
            "expectedScore": "=1.0000",
            "expectedSimilarity": "=1.0000",
            "assertion": "EXACT",
            "precondition": "先导入合同样例-预测验证知识库.xlsx",
            "expectedFocus": "Hash完全命中；不调用模型；类型20的score和similarity均为1。",
        },
        {
            "name": "SEMANTIC_HIGH_单标签一致投票",
            "paragraph": "预测验证主题A：管理人登记合规要求。本基金开始募集资金前，私募基金管理人已经在中国证券投资基金业协会办理登记并取得有效的管理人登记编码。",
            "expectedMatchType": "SEMANTIC",
            "expectedCodes": "43",
            "expectedLevel": "HIGH",
            "expectedSupport": ">=2（设计值约10）",
            "expectedScore": ">=0.80",
            "expectedSimilarity": ">=0.60",
            "assertion": "HIGH_SINGLE",
            "precondition": "主题A的10条历史样本已导入并加载索引",
            "expectedFocus": "Top近邻标签一致，类型43应达到HIGH；若失败先检查索引是否混入旧测试数据。",
        },
        {
            "name": "SEMANTIC_HIGH_多标签一致投票",
            "paragraph": "预测验证主题B：投资范围与风险揭示。投资者认购基金之前，管理人应完整说明产品投资范围、主要风险特征以及可能发生的投资损失。",
            "expectedMatchType": "SEMANTIC",
            "expectedCodes": "20;25;28",
            "expectedLevel": "HIGH",
            "expectedSupport": ">=2（设计值约10）",
            "expectedScore": "各类型>=0.80",
            "expectedSimilarity": ">=0.60",
            "assertion": "HIGH_MULTI",
            "precondition": "主题B的10条多标签历史样本已导入并加载索引",
            "expectedFocus": "同一批近邻共同包含20、25、28，验证多标签score之和可以大于1。",
        },
        {
            "name": "SEMANTIC_CANDIDATE_六四分票",
            "paragraph": "预测验证主题C：投资者适当性与风险确认。投资者提交基金认购申请前，应完成风险承受能力测评，并确认自身风险等级与基金产品风险等级相匹配。",
            "expectedMatchType": "SEMANTIC",
            "expectedCodes": "25",
            "expectedLevel": "CANDIDATE",
            "expectedSupport": "约6",
            "expectedScore": "0.55<=score<0.80",
            "expectedSimilarity": ">=0.60",
            "assertion": "CANDIDATE",
            "precondition": "主题C的10条历史样本已导入：6条类型25、4条类型28",
            "expectedFocus": "验证相似度较高不等于HIGH；类型25应以多数票成为CANDIDATE。",
        },
        {
            "name": "STRONG_SINGLE_MATCH_FALLBACK_十类分散",
            "paragraph": "预测验证主题D：管理人丧失履职能力后的应急处置。管理人客观上无法继续履职时，应立即承担首要处置责任，并启动基金财产安全保障和应急接管安排。",
            "expectedMatchType": "SEMANTIC",
            "expectedCodes": "57",
            "expectedLevel": "CANDIDATE",
            "expectedSupport": "1",
            "expectedScore": "<0.55",
            "expectedSimilarity": ">=0.80",
            "assertion": "FALLBACK",
            "precondition": "主题D的10条标签分散历史样本已导入，第一条类型57与查询最接近",
            "expectedFocus": "正常投票无类型达标，但第一名达到强匹配阈值，返回类型57兜底候选。",
        },
        {
            "name": "NO_RELIABLE_MATCH_跨领域无关文本",
            "paragraph": "射电望远镜阵列正在观测遥远星系的中性氢谱线，数据处理程序需要校准天线相位、消除电离层扰动并生成天区成像结果。",
            "expectedMatchType": "NO_RELIABLE_MATCH",
            "expectedCodes": "空",
            "expectedLevel": "空",
            "expectedSupport": "0",
            "expectedScore": "无候选",
            "expectedSimilarity": "通常<0.80",
            "assertion": "NO_MATCH",
            "precondition": "索引可用；知识库以合同业务段落为主",
            "expectedFocus": "跨领域文本不应输出变更类型；如果仍命中，应检查0.60相似度阈值是否适合当前模型。",
        },
        {
            "name": "ERROR_EMPTY_PARAGRAPH_空段落",
            "paragraph": "",
            "expectedMatchType": "不适用",
            "expectedCodes": "空",
            "expectedLevel": "空",
            "expectedSupport": "不适用",
            "expectedScore": "业务码400",
            "expectedSimilarity": "不适用",
            "assertion": "ERROR_EMPTY",
            "precondition": "无",
            "expectedFocus": "请求体校验失败，提示合同段落不能为空。",
        },
        {
            "name": "ERROR_TOO_LONG_超长段落",
            "paragraph": long_para,
            "expectedMatchType": "不适用",
            "expectedCodes": "空",
            "expectedLevel": "空",
            "expectedSupport": "不适用",
            "expectedScore": "业务码400",
            "expectedSimilarity": "不适用",
            "assertion": "ERROR_TOO_LONG",
            "precondition": "无",
            "expectedFocus": "超过2000字符，预期被业务校验拒绝。",
        },
    ]

    payload = {
        "sourceDocx": str(SOURCE_DOCX),
        "baseParagraphCount": len(base),
        "normalRows": normal,
        "normalRows920": normal[:920],
        "predictionKnowledgeRows": prediction_knowledge_rows,
        "duplicateRows": [
            {"paragraph": duplicate_seed, "codes": normal[0]["codes"]},
            {"paragraph": duplicate_seed, "codes": normal[0]["codes"]},
            {"paragraph": duplicate_seed, "codes": normal[0]["codes"]},
            {"paragraph": normal[4]["paragraph"], "codes": normal[4]["codes"]},
            {"paragraph": normal[4]["paragraph"], "codes": normal[4]["codes"]},
        ],
        "conflictRows": [
            {"paragraph": conflict_seed, "codes": normal[1]["codes"]},
            {"paragraph": conflict_seed, "codes": "43;57"},
            {"paragraph": normal[5]["paragraph"], "codes": "20;25"},
            {"paragraph": normal[5]["paragraph"], "codes": "28;43"},
        ],
        "separatorRows": [
            {"paragraph": separator_seed, "codes": "28，20；25;20"},
            {"paragraph": normal[6]["paragraph"], "codes": "57,43；57"},
            {"paragraph": normal[8]["paragraph"], "codes": "26；54，76"},
        ],
        "invalidRows": [
            {"paragraph": "", "codes": "20"},
            {"paragraph": normal[10]["paragraph"], "codes": ""},
            {"paragraph": normal[11]["paragraph"], "codes": "20 25"},
            {"paragraph": normal[12]["paragraph"], "codes": "X" * 65},
        ],
        "longRows": [
            {"paragraph": long_para, "codes": "20;25"}
        ],
        "overLimitRows": [
            {
                "paragraph": "批量行数边界验证第%04d条：%s" % (i + 1, base[i % len(base)]),
                "codes": CODE_GROUPS[i % len(CODE_GROUPS)]
            }
            for i in range(1001)
        ],
        "predictionCases": prediction_cases,
    }
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "source": str(SOURCE_DOCX),
        "baseParagraphCount": len(base),
        "normalRows": len(normal),
        "output": str(OUT_JSON)
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    build_dataset()
