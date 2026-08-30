package com.citics.glxt.contractchange.docx.service.impl;

import com.citics.glxt.common.service.CommonService;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.xml.bind.JAXBElement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;
import static com.citics.glxt.contractchange.docx.constants.Constants.*;

@Slf4j
@Service
public class DocxCommonServiceImpl implements DocxCommonService {

    // docx4j对象工厂
    private static final ObjectFactory factory = new ObjectFactory();
    private static final org.docx4j.math.ObjectFactory mathFactory = new org.docx4j.math.ObjectFactory();

    // 标题正则化。一级标题：一、；二级标题：（一）；三级标题：1、；四级标题：（1）；五级标题：1）；五级标题：1）
    private static final Pattern LEVEL1_TITLE_PATTERN = Pattern.compile("^(?<prefix>[零一二三四五六七八九十百千万]{1,}[、.])");
    private static final Pattern LEVEL2_TITLE_PATTERN = Pattern.compile("^(?<prefix>[(（][零一二三四五六七八九十百千万]{1,}[)）])");
    private static final Pattern LEVEL3_TITLE_PATTERN = Pattern.compile("^(?<prefix>\\d+[、.])");
    private static final Pattern LEVEL4_TITLE_PATTERN = Pattern.compile("^(?<prefix>[(（]\\d+[)）])");
    private static final Pattern LEVEL5_TITLE_PATTERN1 = Pattern.compile("^(?<prefix>\\d+[)）])");
    private static final Pattern LEVEL5_TITLE_PATTERN2 = Pattern.compile("^(?<prefix>[\\u2460-\\u2487])"); // ①‑㉗（1‑35）

    // 中文数字转换：静态常量缓存，避免重复创建
    private static final Map<Character, Integer> CHN_TO_NUM;
    private static final Map<Character, Integer> UNIT_TO_NUM;
    static {
        // 基础中文数字→整数
        CHN_TO_NUM = new HashMap<>(10);
        CHN_TO_NUM.put('零', 0);
        CHN_TO_NUM.put('一', 1);
        CHN_TO_NUM.put('二', 2);
        CHN_TO_NUM.put('三', 3);
        CHN_TO_NUM.put('四', 4);
        CHN_TO_NUM.put('五', 5);
        CHN_TO_NUM.put('六', 6);
        CHN_TO_NUM.put('七', 7);
        CHN_TO_NUM.put('八', 8);
        CHN_TO_NUM.put('九', 9);

        // 中文单位→整数（支持十、百、千）
        UNIT_TO_NUM = new HashMap<>(3);
        UNIT_TO_NUM.put('十', 10);
        UNIT_TO_NUM.put('百', 100);
        UNIT_TO_NUM.put('千', 1000);
    }




    @Resource
    private CommonService commonService;

    @Override
    public WordprocessingMLPackage getXmlDocument(String filePath) {
        WordprocessingMLPackage xmlLoadRes = null;
        log.info("开始从文档库加载文件...");
        xmlLoadRes = (WordprocessingMLPackage)commonService.downloadFromTable(filePath, true);
        isFalse( null == xmlLoadRes || null == xmlLoadRes.getMainDocumentPart(),
                "文件读取失败，加载的WordprocessingMLPackage对象为空，文件获取地址：" + filePath);
        return xmlLoadRes;
    }


    @Override
    public RPr getFirstRunPrP(P paragraph) {
        if (Objects.isNull(paragraph)) {
            return null;
        }
        for (Object obj : paragraph.getContent()) {
            if (obj instanceof R) {
                R run = (R) obj;
                if (run.getRPr() != null) {
                    return run.getRPr();
                }
            } else if (obj instanceof JAXBElement && ((JAXBElement<?>) obj).getValue() instanceof R) {
                R run = (R) ((JAXBElement<?>) obj).getValue();
                if (run.getRPr() != null) {
                    return run.getRPr();
                }
            } else if (obj instanceof RunIns) {
                RunIns runIns = (RunIns) obj;
                for (Object insObj : runIns.getCustomXmlOrSmartTagOrSdt()) {
                    if (insObj instanceof R) {
                        R run = (R) insObj;
                        if (run.getRPr() != null) {
                            return run.getRPr();
                        }
                    } else if (insObj instanceof JAXBElement && ((JAXBElement<?>) insObj).getValue() instanceof R) {
                        R run = (R) ((JAXBElement<?>) insObj).getValue();
                        if (run.getRPr() != null) {
                            return run.getRPr();
                        }
                    }
                }
            }
        }
        return null;
    }


    @Override
    public List<String> extractFullParagraphTextList(List<?> paragraphContent) {
        List<String> res = new ArrayList<>();
        if (CollectionUtils.isEmpty(paragraphContent)) {
            return res;
        }
        StringBuilder fullText = new StringBuilder();
        extractTextFromNodeList2(paragraphContent, fullText, res);
        // 最后把剩余的内容加入
        if (fullText.length() > 0) {
            res.add(fullText.toString());
        }
        return res;
    }

    @Override
    public void extractTextFromNodeList2(List<?> nodeList, StringBuilder fullText, List<String> textList) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }
        for (Object node : nodeList) {
            if (node instanceof Text) {
                Text textNode = (Text) node;
                String textValue = textNode.getValue();
                if (StringUtils.isNotEmpty(textValue)) {
                    // 替换不间断空格 \u00A0
                    textValue = textValue.replace("\u00A0", " ");
                    // 更新一下节点内的文本
                    textNode.setValue(textValue);
                    fullText.append(textValue.trim());
                }
            } else if (node instanceof Br || node instanceof R.Cr) {
                // 拆分: 当前行结束,存入list,清空fullText
                if (fullText.length() > 0) {
                    textList.add(fullText.toString());
                    fullText.setLength(0);
                }
            } else if (node instanceof JAXBElement) {
                extractTextFromNodeList2(Collections.singletonList(((JAXBElement<?>) node).getValue()), fullText, textList);
            } else if (node instanceof R) {
                extractTextFromNodeList2(((R) node).getContent(), fullText, textList);
            } else if (node instanceof RunIns) {
                extractTextFromNodeList2(((RunIns) node).getCustomXmlOrSmartTagOrSdt(), fullText, textList);
            } else if (node instanceof SdtElement) {
                extractTextFromNodeList2(((SdtElement) node).getSdtContent().getContent(), fullText, textList);
            }
        }
    }


    /**
     * 获取文本所属模块
     */
    @Override
    public long getCurrentModule(String text, long currentModuleId) {
        long res = currentModuleId;
        if (text.equals("合同封面")){
            res = CONTRACT_ANALYSIS_MODULE_COVER;
        } else if (text.equals("特别约定")){
            res = CONTRACT_ANALYSIS_MODULE_SPECIAL;
        } else if (text.equals("风险揭示书")){
            res = CONTRACT_ANALYSIS_MODULE_RISK;
        } else if (text.equals("合格投资者承诺书")){
            res = CONTRACT_ANALYSIS_MODULE_UNDERTAKING;
        } else if (text.equals("目录")){
            res = CONTRACT_ANALYSIS_MODULE_CONTENTS;
        } else if (text.equals("一、合同当事人")){
            res = CONTRACT_ANALYSIS_MODULE_MAIN;
        } else if (text.equals("签署页")){
            res = CONTRACT_ANALYSIS_MODULE_SIGNATURE;
        } else if (text.equals("附件")){
            res = CONTRACT_ANALYSIS_MODULE_ATTACHMENT;
        }
        return res;
    }


    /**
     * 更新当前文本的标题层级Map
     */
    @Override
    public void updateTextTitleLevelMap(String pgText, Map<Integer, String> mapNum, Map<Integer, String> mapText) {
        pgText = pgText.trim();
        // 匹配一级标题（优先匹配高层级，避免低级标题覆盖）
        Matcher level1Matcher = LEVEL1_TITLE_PATTERN.matcher(pgText);
        if (level1Matcher.find()) {
            String firstTitle = level1Matcher.group("prefix"); // 完整标题前缀：如“一、”
            String firstTitleSub = pgText.replaceFirst(Pattern.quote(firstTitle), ""); // 去掉标题前缀后的标题文本
            mapNum.put(1, firstTitle);
            // 重置低层级标题
            mapNum.put(2, "");
            mapNum.put(3, "");
            mapNum.put(4, "");
            mapNum.put(5, "");
            if (mapText != null) {
                mapText.put(1, firstTitleSub);
                mapText.put(2, "");
                mapText.put(3, "");
                mapText.put(4, "");
                mapText.put(5, "");
            }
            return;
        }

        // 匹配二级标题
        Matcher level2Matcher = LEVEL2_TITLE_PATTERN.matcher(pgText);
        if (level2Matcher.find()) {
            String secondTitle = level2Matcher.group("prefix"); // 完整标题前缀：如“（一）”
            String secondTitleSub = pgText.replaceFirst(Pattern.quote(secondTitle), ""); // 去掉标题前缀后的标题文本
            mapNum.put(2, secondTitle);
            mapNum.put(3, "");
            mapNum.put(4, "");
            mapNum.put(5, "");
            if (mapText != null) {
                mapText.put(2, secondTitleSub);
                mapText.put(3, "");
                mapText.put(4, "");
                mapText.put(5, "");
            }
            return;
        }

        // 匹配三级标题
        Matcher level3Matcher = LEVEL3_TITLE_PATTERN.matcher(pgText);
        if (level3Matcher.find()) {
            String thirdTitle = level3Matcher.group("prefix"); // 完整标题前缀：如“1、”
            String thirdTitleSub = pgText.replaceFirst(Pattern.quote(thirdTitle), ""); // 去掉标题前缀后的标题文本
            mapNum.put(3, thirdTitle);
            mapNum.put(4, "");
            mapNum.put(5, "");
            if (mapText != null) {
                mapText.put(3, thirdTitleSub);
                mapText.put(4, "");
                mapText.put(5, "");
            }
            return;
        }

        // 匹配四级标题
        Matcher level4Matcher = LEVEL4_TITLE_PATTERN.matcher(pgText);
        if (level4Matcher.find()) {
            String fourthTitle = level4Matcher.group("prefix"); // 完整标题前缀：如“（1）”
            String fourthTitleSub = pgText.replaceFirst(Pattern.quote(fourthTitle), ""); // 去掉标题前缀后的标题文本
            mapNum.put(4, fourthTitle);
            mapNum.put(5, "");
            if (mapText != null) {
                mapText.put(4, fourthTitleSub);
                mapText.put(5, "");
            }
            return;
        }

        // 匹配五级标题
        Matcher level5Matcher = LEVEL5_TITLE_PATTERN1.matcher(pgText);
        if (level5Matcher.find()) {
            String fifthTitle = level5Matcher.group("prefix"); // 完整标题前缀：如“1）”
            String fifthTitleSub = pgText.replaceFirst(Pattern.quote(fifthTitle), ""); // 去掉标题前缀后的标题文本
            mapNum.put(5, fifthTitle);
            if (mapText != null) {
                mapText.put(5, fifthTitleSub);
            }
            return;
        }
    }


    /**
     * 处理自带的标题序号前缀，返回纯文本的新段落内容
     */
    @Override
    public String handleTitleMode(P paragraph, PPr ppr, RPr rpr, String pgText, Map<String, String> titleModeMap, Map<Integer, String> textCurrentTitleMapNum, boolean modify) {
        String res = pgText;
        if(titleModeMap.size() > 0){
            // 获取数据
            String mode = titleModeMap.get(TITLE_MODE);
            String numFmt = titleModeMap.get(TITLE_NUMFMT);
            // 清空标题属性
            ppr.setNumPr(null);
            // 计算自带的前缀具体值
            String prefix = "";
            if (numFmt.contains("CHINESE_COUNTING") || numFmt.contains("TAIWANESE_COUNTING") || numFmt.contains("JAPANESE_COUNTING")) {
                if (mode.equals("%1、")) {
                    String title = textCurrentTitleMapNum.get(1);
                    prefix = title.equals("") ? "一、" : generateNextTitlePrefix(title);
                }
                if (mode.equals("（%1）") || mode.equals("(%1)")) {
                    String title = textCurrentTitleMapNum.get(2);
                    prefix = title.equals("") ? "(一)" : generateNextTitlePrefix(title);
                }
            }

            if (numFmt.equals("DECIMAL")) {
                if (mode.equals("%1、") || mode.equals("%1.")) {
                    String title = textCurrentTitleMapNum.get(3);
                    prefix = title.equals("") ? "1、" : generateNextTitlePrefix(title);
                }
                if (mode.equals("（%1）") || mode.equals("(%1)") || mode.equals("%1)") || mode.equals("(%1)")) {
                    String title = textCurrentTitleMapNum.get(4);
                    prefix = title.equals("") ? "(1)" : generateNextTitlePrefix(title);
                }
                if (mode.equals("%1)") || mode.equals("(%1)")) {
                    String title = textCurrentTitleMapNum.get(5);
                    prefix = title.equals("") ? "1)" : generateNextTitlePrefix(title);
                }
            }

            // 圆圈格式：①；暂时全写成①
            if (numFmt.contains("DECIMAL_ENCLOSED_CIRCLE")) {
                if (mode.equals("%1") || mode.equals("%1 ")) {
                    prefix = "①";
                }
            }

            // 黑点格式、方框格式、对号星号等
            if (numFmt.equals("BULLET")) {
                if (mode.equals("•")) {
                    prefix = "•";
                }
                if (mode.equals("●")) {
                    prefix = "●";
                }
                if (mode.equals("○")) {
                    prefix = "○";
                }
                if (mode.equals("■")) {
                    prefix = "■";
                }
                if (mode.equals("■")) {
                    prefix = "■";
                }
                if (mode.equals("▪")) {
                    prefix = "▪";
                }
                if (mode.equals("♦")) {
                    prefix = "♦";
                }
                if (mode.equals("◊")) {
                    prefix = "◊";
                }
                if (mode.equals("▲")) {
                    prefix = "▲";
                }
                if (mode.equals("▼")) {
                    prefix = "▼";
                }
                if (mode.equals("✓")) {
                    prefix = "✓";
                }
                if (mode.equals("☑")) {
                    prefix = "☑";
                }
                if (mode.equals("×")) {
                    prefix = "×";
                }
                if (mode.equals("☒")) {
                    prefix = "☒";
                }
                if (mode.equals("➤")) {
                    prefix = "➤";
                }
                if (mode.equals("✧")) {
                    prefix = "✧";
                }
                if (mode.equals("★")) {
                    prefix = "★";
                }
                if (mode.equals("☆")) {
                    prefix = "☆";
                }
                if (mode.equals("➔")) {
                    prefix = "➔";
                }
                if (mode.equals("➨")) {
                    prefix = "➨";
                }
            }

            // 替换段落文本内容
            if (!prefix.equals("")) {
                res = prefix + pgText;
                insertTitleBeforeParagraph(paragraph, pgText, prefix, rpr, modify);
            }
        }
        return res;
    }


    /**
     * 获取当前最低级别的标题
     */
    @Override
    public int getCurrentValidTitleLevel(Map<Integer, String> textTitleMap) {
        if (Objects.isNull(textTitleMap)) {
            return 0;
        }
        for (int i = MAX_TITLE_LEVEL ; i >= 1; i--) {
            if (StringUtils.isNotEmpty(textTitleMap.get(i))) {
                return i;
            }
        }
        return 0;
    }


    @Override
    public String extractPrefixByLevel(String paraText, int level) {
        if (StringUtils.isEmpty(paraText) || level < 1 || level > MAX_TITLE_LEVEL) {
            return "";
        }
        paraText = paraText.trim();
        Matcher matcher = null;
        // 按层级匹配对应的正则
        switch (level) {
            case 1:
                matcher = LEVEL1_TITLE_PATTERN.matcher(paraText);
                break;
            case 2:
                matcher = LEVEL2_TITLE_PATTERN.matcher(paraText);
                break;
            case 3:
                matcher = LEVEL3_TITLE_PATTERN.matcher(paraText);
                break;
            case 4:
                matcher = LEVEL4_TITLE_PATTERN.matcher(paraText);
                break;
            case 5:
                matcher = LEVEL5_TITLE_PATTERN1.matcher(paraText);
                break;
            default:
                return "";
        }
        // 匹配成功则返回前缀，否则返回空
        return matcher.find() ? matcher.group("prefix") : "";
    }


    @Override
    public Map<String, String> getTitleModeFromP(Numbering numbering, PPr ppr) {
        Map<String, String> res = new HashMap<>();
        //段落可能是自带word序号的段落
        // 获取段落的numId和ilvl
        PPrBase.NumPr numPr = ppr.getNumPr();
        PPrBase.NumPr.NumId numIdTmp = numPr.getNumId();
        PPrBase.NumPr.Ilvl ilvlTmp = numPr.getIlvl();
        if (numbering != null && numIdTmp != null && ilvlTmp != null) {
            int numId = numIdTmp.getVal().intValue();
            int ilvl = ilvlTmp.getVal().intValue();
            List<Numbering.Num> numberingNums = numbering.getNum();
            List<Numbering.AbstractNum> numberingAbstractNums = numbering.getAbstractNum();
            for (Numbering.Num n : numberingNums) {
                if (numId == n.getNumId().intValue()) {
                    // 获取对应级别的编号格式
                    int abstractNumId = n.getAbstractNumId().getVal().intValue();
                    for (Numbering.AbstractNum m : numberingAbstractNums) {
                        if (abstractNumId == m.getAbstractNumId().intValue()) {
                            List<Lvl> mLvls = m.getLvl();
                            for (Lvl l : mLvls) {
                                if (ilvl == l.getIlvl().intValue()) {
                                    String numFmt = String.valueOf(l.getNumFmt().getVal());
                                    String mode = l.getLvlText().getVal();
                                    res.put(TITLE_MODE, mode);
                                    res.put(TITLE_NUMFMT, numFmt);
                                }
                            }
                        }
                    }
                }
            }
        }
        return res;
    }

    /**
     * 段落文本前插入标题（仅新标题带修订模式）
     */
    @Override
    public void insertTitleBeforeParagraph(P paragraph, String pgText, String newPrefix, RPr rpr, boolean modify) {
        if (!StringUtils.isEmpty(newPrefix)) {
            List<Object> paraContent = paragraph.getContent();
            RPr firstRunPrP = rpr == null ? getFirstRunPrP(paragraph) : rpr;
            RPr cloneRpr = firstRunPrP != null ? XmlUtils.deepCopy(firstRunPrP) : null;
            paraContent.clear();
            if (modify) {
                R insRun = createNormalRun(newPrefix, cloneRpr, false);
                RunIns insRunIns = createNormalRunIns(insRun);
                paraContent.add(insRunIns);
                R run = createNormalRun(pgText, cloneRpr, false);
                paraContent.add(run);
            } else {
                R run = createNormalRun(newPrefix + pgText, cloneRpr, false);
                paraContent.add(run);
            }
        }
    }

    /**
     * 生成下一个标题前缀(如: 一→二、；（1）→（2）；1)→2))
     * currentPrefix: 当前文本的标题前缀，如（2）； 一、；  3)
     */
    @Override
    public String generateNextTitlePrefix(String currentPrefix) {
        // 处理一级标题（一、→二、）
        Matcher level1Matcher = LEVEL1_TITLE_PATTERN.matcher(currentPrefix);
        if (level1Matcher.find()) {
            String numStr = level1Matcher.group("prefix").replace("、", "");
            int num = chineseNumToInt(numStr) + 1;
            return intToChineseNum(num) + "、";
        }

        // 处理二级标题（（一）→（二））
        Matcher level2Matcher = LEVEL2_TITLE_PATTERN.matcher(currentPrefix);
        if (level2Matcher.find()) {
            String numStr = level2Matcher.group("prefix").replace("(", "").replace(")", "");
            int num = chineseNumToInt(numStr) + 1;
            return "(" + intToChineseNum(num) + ")";
        }

        // 处理三级标题（1、→2、）
        Matcher level3Matcher = LEVEL3_TITLE_PATTERN.matcher(currentPrefix);
        if (level3Matcher.find()) {
            String numStr = level3Matcher.group("prefix").replace("、", "");
            int num = Integer.parseInt(numStr) + 1;
            return num + "、";
        }

        // 处理四级标题（（1）→（2））
        Matcher level4Matcher = LEVEL4_TITLE_PATTERN.matcher(currentPrefix);
        if (level4Matcher.find()) {
            String numStr = level4Matcher.group("prefix").replace("(", "").replace(")", "");
            int num = Integer.parseInt(numStr) + 1;
            return "(" + num + ")";
        }

        // 处理五级标题（1）→2））
        Matcher level5Matcher = LEVEL5_TITLE_PATTERN1.matcher(currentPrefix);
        if (level5Matcher.find()) {
            String numStr = level5Matcher.group("prefix").replace(")", "");
            int num = Integer.parseInt(numStr) + 1;
            return num + ") ";
        }

        return currentPrefix;
    }

    /**
     * 创建普通Run（无修订，包含JAXBElement包装的Text）
     * @param text 文本内容
     * @param rPr 格式
     * @param isDel 是否是删除类文本
     * @return 新生成的R元素
     */
    @Override
    public R createNormalRun(String text, RPr rPr, boolean isDel) {
        R run = factory.createR();
        RPr cloneRPr = rPr != null ? XmlUtils.deepCopy(rPr) : factory.createRPr();
        run.setRPr(cloneRPr);
        if (!isDel) {
            Text textNode = factory.createText();
            textNode.setValue(text);
            JAXBElement<Text> textJaxb = factory.createRT(textNode);
            run.getContent().add(textJaxb);
        } else {
            DelText delText = factory.createDelText();
            delText.setValue(text);
            run.getContent().add(delText);
        }
        return run;
    }

    /**
     * 创建RunIns
     * @param r  R元素
     * @return 新生成的RunIns元素
     */
    @Override
    public RunIns createNormalRunIns(R r) {
        RunIns runIns = factory.createRunIns();
        runIns.getCustomXmlOrSmartTagOrSdt().add(r);
        return runIns;
    }

    @Override
    public int chineseNumToInt(String chnNumber) {
        isFalse(StringUtils.isEmpty(chnNumber), "文档标题序号转换时，输入的汉语数字不能为空！");
        int result = 0;
        int temp = 0; // 临时存储百位/十位的基数
        for (int i = 0; i < chnNumber.length(); i++) {
            char c = chnNumber.charAt(i);
            // 如果是基础数字（零‑九）
            if (CHN_TO_NUM.containsKey(c)) {
                temp = CHN_TO_NUM.get(c);
                // 最后一位直接累加
                if (i == chnNumber.length() - 1) {
                    result += temp;
                }
            }
            // 如果是单位（十、百）
            else if (UNIT_TO_NUM.containsKey(c)) {
                int unit = UNIT_TO_NUM.get(c);
                // 处理“十”的特殊情况：如“十”=10，“二十”=20
                if (temp == 0 && unit == 10) {
                    temp = 1;
                }
                result += temp * unit;
                temp = 0; // 重置临时值
            }
            // 非法字符
            else {
                throw new IllegalArgumentException("文档标题序号转换时，包含非法字符：" + c);
            }
        }
        // 检验结果范围
        isFalse(result < 0 || result > 999, "文档标题序号转换时，转换结果超出999范围，当前结果：" + result);
        return result;
    }

    @Override
    public String intToChineseNum(int number) {
        isFalse(number <= 0 || number >= 1000, "文档标题序号仅支持1‑999范围内！");
        String[] NUM_TO_CHN = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        StringBuilder res = new StringBuilder();
        // 拆分百位、十位、个位
        int hundred = number / 100;
        int ten = (number % 100) / 10;
        int unit = number % 10;

        // 处理百位
        if (hundred > 0) {
            res.append(NUM_TO_CHN[hundred]).append("百");
            // 百位非零，十位和个位都为0时，无需后续内容
            if (ten == 0 && unit == 0) {
                return res.toString();
            }
            // 百位非零，十位为0但个位非零，需加“零”
            if (ten == 0 && unit > 0) {
                res.append("零");
            }
        }
        // 处理十位
        if (ten > 0) {
            // 处理“十”的特殊情况：如10=“十”，11=“十一”，20=“二十”
            if (ten == 1 && hundred == 0) {
                res.append("十");
            } else {
                res.append(NUM_TO_CHN[ten]).append("十");
            }
        }
        // 处理个位
        if (unit > 0) {
            res.append(NUM_TO_CHN[unit]);
        }
        return res.toString();
    }

}
