package com.citics.glxt.contractchange.docx.service.impl;

import com.citics.glxt.contractchange.common.service.CommonService;
import com.citics.glxt.contractchange.common.utils.file.ToolUtil;
import com.citics.glxt.contractchange.docx.domain.ModifyConfigTableDetail;
import com.citics.glxt.contractchange.docx.service.DocxCommonService;
import com.citics.glxt.contractchange.docx.utils.RunInsUtils;
import com.citics.glxt.contractchange.docx.utils.RunUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.math.CTOMath;
import org.docx4j.math.CTOMathPara;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.xml.bind.JAXBElement;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;
import static com.citics.glxt.contractchange.constants.Constants.*;

@Slf4j
@Service
public class DocxCommonServiceImpl implements DocxCommonService {

    @Resource
    private CommonService commonService;

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

    @Override
    public WordprocessingMLPackage getXmlDocument(String filePath) {
        WordprocessingMLPackage xmlLoadRes = null;
        log.info("开始从文档库加载文件...");
        xmlLoadRes = (WordprocessingMLPackage)commonService.downloadFromTable(filePath, true);
        isFalse( null == xmlLoadRes || null == xmlLoadRes.getMainDocumentPart(),
                "文件读取失败，加载的WordprocessingMLPackage对象为空，文件获取地址：" + filePath);
        return xmlLoadRes;
    }

    /**
     * 保存修改后的文档到本地/文档库（可根据实际需求调整存储逻辑）
     */
    @Override
    public void saveModifiedDocument(WordprocessingMLPackage wordMLPackage, String fileSavePath) throws Docx4JException {
        // String fileSavePath = "E:\\TMP\\" + "modified_" + "xxx" + ".docx"; // 本地测试
        // 获取临时下载路径
        String tmp = ToolUtil.getDownloadPath() + UUID.randomUUID().toString() + "_docx4j.FJ";
        File outputFile = new File(tmp);
        File parentDir = outputFile.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            // mkdirs() 会创建所有不存在的父目录，mkdir() 只能创建单级目录
            boolean dirCreated = parentDir.mkdirs();
            if (!dirCreated) {
                throw new RuntimeException("创建目录失败：" + parentDir.getAbsolutePath());
            }
        }

        // 保存文件；outputFile指向新路径则会创建新的文件；若指向原路径则会覆盖原文件，且无法恢复
        wordMLPackage.save(outputFile);

        // 上传至服务器
        Boolean uploadStatus = commonService.sftpUpload(tmp, fileSavePath, "GBK");

        if (!uploadStatus) {
            log.error("文档保存失败，待保存服务器路径：{}", fileSavePath);
        } else {
            // 上传完成后删除临时文件，避免磁盘占用
            boolean deleted = outputFile.delete();
            if (!deleted) {
                log.info("文件上传服务器成功后临时文件删除失败：{}", tmp);
            }
        }

        log.info(
                "文档保存成功，修改后的文档已保存至临时路径：{}，和服务器路径：{}",
                tmp,
                fileSavePath
        );
    }

    /**
     * 中文数字转整数（正整数，支持一到九百九十九）
     */
    @Override
    public int chinesNumToInt(String chnNumber) {
        isFalse(
                StringUtils.isEmpty(chnNumber),
                "文档标题序号转换时，输入的汉语数字不能为空！"
        );

        int result = 0;
        int temp = 0; // 临时存储百位/十位的基数

        for (int i = 0; i < chnNumber.length(); i++) {
            char c = chnNumber.charAt(i);

            // 如果是基础数字（零~九）
            if (CHN_TO_NUM.containsKey(c)) {
                temp = CHN_TO_NUM.get(c);

                // 最后一位直接累加
                if (i == chnNumber.length() - 1) {
                    result += temp;
                }

                // 如果是单位（十、百）
            } else if (UNIT_TO_NUM.containsKey(c)) {
                int unit = UNIT_TO_NUM.get(c);

                // 处理“十”的特殊情况：如“十二”=10，“二十”=20
                if (temp == 0 && unit == 10) {
                    temp = 1;
                }

                result += temp * unit;
                temp = 0; // 重置临时值

                // 非法字符
            } else {
                throw new IllegalArgumentException(
                        "文档标题序号转换时，包含非法字符：" + c
                );
            }
        }

        // 校验结果范围
        isFalse(
                result < 0 || result > 999,
                "文档标题序号转换时，转换结果超出999范围，当前结果：" + result
        );

        return result;
    }

    @Override
    public String intToChinesNum(int number) {
        isFalse(
                number <= 0 || number >= 1000,
                "文档标题序号仅支持1-999范围内！"
        );

        String[] NUM_TO_CHN = {
                "零", "一", "二", "三", "四",
                "五", "六", "七", "八", "九"
        };

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

    /**
     * 生成下一个标题前缀（如：一、→二、；（1）→（2）；1→2）
     * currentPrefix：当前文本的标题前缀，如（2）、 3）
     */
    @Override
    public String generateNextTitlePrefix(String currentPrefix) {

        // 处理一级标题（一、→二、）
        Matcher level1Matcher =
                LEVEL1_TITLE_PATTERN.matcher(currentPrefix);

        if (level1Matcher.find()) {
            String numStr =
                    level1Matcher
                            .group("prefix")
                            .replace("、", "");

            int num = chinesNumToInt(numStr) + 1;

            return intToChinesNum(num) + "、";
        }

        // 处理二级标题（（一）→（二））
        Matcher level2Matcher =
                LEVEL2_TITLE_PATTERN.matcher(currentPrefix);

        if (level2Matcher.find()) {
            String numStr =
                    level2Matcher
                            .group("prefix")
                            .replace("（", "")
                            .replace("）", "");

            int num = chinesNumToInt(numStr) + 1;

            return "（" + intToChinesNum(num) + "）";
        }

        // 处理三级标题（1、→2、）
        Matcher level3Matcher =
                LEVEL3_TITLE_PATTERN.matcher(currentPrefix);

        if (level3Matcher.find()) {
            String numStr =
                    level3Matcher
                            .group("prefix")
                            .replace("、", "");

            int num = Integer.parseInt(numStr) + 1;

            return num + "、";
        }

        // 处理四级标题（（1）→（2））
        Matcher level4Matcher =
                LEVEL4_TITLE_PATTERN.matcher(currentPrefix);

        if (level4Matcher.find()) {
            String numStr =
                    level4Matcher
                            .group("prefix")
                            .replace("（", "")
                            .replace("）", "");

            int num = Integer.parseInt(numStr) + 1;

            return "（" + num + "）";
        }

        // 处理五级标题（1）→2））
        Matcher level5Matcher =
                LEVEL5_TITLE_PATTERN1.matcher(currentPrefix);

        if (level5Matcher.find()) {
            String numStr =
                    level5Matcher
                            .group("prefix")
                            .replace("）", "");

            int num = Integer.parseInt(numStr) + 1;

            return num + "）";
        }

        return currentPrefix;
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

    @Override
    public RPr getFirstRunPr(P paragraph) {
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
    public List<P> splitParagraphByLineBreaks(P originalP) {
        List<P> resultParagraphs = new ArrayList<>();
        List<Object> contentList = originalP.getContent();
        PPr pPr = originalP.getPPr();

        if (CollectionUtils.isEmpty(contentList) || pPr == null) {
            return resultParagraphs;
        }

        // 当前分段的节点缓存
        List<Object> currentSegment = new ArrayList<>();
        for (Object node : contentList) {
            // 遇到换行 -> 结束当前分段，生成新P
            if (node instanceof Br || node instanceof R.Cr) {
                if (!currentSegment.isEmpty()) {
                    // 克隆段落属性，创建新P
                    P newP = createParagraphWithSameNodes(
                            XmlUtils.deepCopy(originalP.getPPr()),
                            new ArrayList<>(currentSegment)
                    );
                    resultParagraphs.add(newP);
                    currentSegment.clear();
                }
                // 换行节点本身不加入新段落（和你原来逻辑一致）
            } else {
                // 其他类型的所有节点都直接保留
                currentSegment.add(node);
            }
        }

        // 最后一段加入
        if (!currentSegment.isEmpty()) {
            P newP = createParagraphWithSameNodes(
                    XmlUtils.deepCopy(originalP.getPPr()),
                    currentSegment
            );
            resultParagraphs.add(newP);
        }

        return resultParagraphs;
    }

    /**
     * 用原始节点集合 创建段落，1:1还原结构，保留修订/批注/格式
     * 创建与原段落样式完全一致的新段落（包含PPr段落格式+RPr字符格式）
     *
     * @param pPr 段落格式（继承格式）
     * @param contentList 新段落各类节点集合内容
     * @return 格式一致的新段落
     */
    @Override
    public P createParagraphWithSameNodes(PPr pPr, List<Object> contentList) {
        P p = new P();
        p.setPPr(pPr);
        List<Object> pContent = p.getContent();

        // 深度克隆节点，避免多个段落共用同一个XML对象导致文档损坏
        for (Object node : contentList) {
            pContent.add(XmlUtils.deepCopy(node));
        }

        return p;
    }

    /**
     * 按 <w:cr/> <w:br/> 拆分P，并替换原来的P，适用于多个带序号前缀段落实质上都在同一个P中的情况
     * 前提是mainIterator刚好迭代到当前P处
     * 根据文本
     */
    @Override
    public void splitAndReplaceParagraph(ListIterator<Object> mainIterator,
                                         List<List<Object>> nodeList,
                                         PPr ppr,
                                         RPr rpr,
                                         boolean goOnIterate) {
        if (nodeList.size() > 0) {
            // 克隆属性
            PPr clonePpr = XmlUtils.deepCopy(ppr);
            PPr clonePprClear = XmlUtils.deepCopy(ppr);
            clonePprClear.setNumPr(null); // 清空标题属性
            RPr cloneRpr = XmlUtils.deepCopy(rpr);

            // 移除旧P
            mainIterator.remove();

            // 逐行创建新P
            for (int i = 0; i < nodeList.size(); i++) {
                List<Object> paraList = nodeList.get(i);
                if (paraList.size() > 0) {
                    // 创建一个新的段落
                    P newP = createParagraphWithSameStyle(
                            i == 0 ? clonePpr : clonePprClear,
                            cloneRpr,
                            ""
                    );

                    for (int j = 0; j < paraList.size(); j++) {
                        Object obj = paraList.get(j);
                        List<Object> newContent = newP.getContent();

                        if (obj instanceof String) {
                            String s = String.valueOf(obj);
                            if (StringUtils.isEmpty(s)) {
                                continue;
                            }

                            R newRun = createNormalRun(s, cloneRpr, false);
                            newContent.add(newRun);
                        }

                        if (obj instanceof CTOMath) {
                            CTOMathPara oMathPara = mathFactory.createCTOMathPara();
                            oMathPara.getOMath().add((CTOMath) obj);
                            JAXBElement<CTOMathPara> mathJaxb =
                                    mathFactory.createOMathPara(oMathPara);
                            newContent.add(mathJaxb);
                        }
                    }

                    addNewParagraph(mainIterator, newP, true);
                }
            }

            // 插入在后面的段落也要遍历
            if (goOnIterate) {
                for (int i = 0; i < nodeList.size(); i++) {
                    mainIterator.previous();
                }
            }
        }
    }

    /**
     * 按 <w:cr/> <w:br/> 拆分P，并替换原来的P，适用于多个带序号前缀段落实质上都在同一个P中的情况
     * 前提是mainIterator刚好迭代到当前P处
     * 根据node
     */
    @Override
    public void splitAndReplaceParagraph(ListIterator<Object> mainIterator, List<P> pList, PPr ppr, boolean goOnIterate) {
        if (pList.size() > 0) {
            // 移除旧P
            mainIterator.remove();

            // 添加新P
            for (int i = 0; i < pList.size(); i++) {
                P p = pList.get(i);
                if (p == null) {
                    continue;
                }

                // 若自带标题，则第一段保留，剩余段落不保留，
                if (i > 0) {
                    p.getPPr().setNumPr(null);
                }

                addNewParagraph(mainIterator, p, true);
            }

            // 插入在后面的段落也要遍历
            if (goOnIterate) {
                for (int i = 0; i < pList.size(); i++) {
                    mainIterator.previous();
                }
            }
        }
    }

    /**
     * 新增段落：插入到当前段落前/后（维护迭代器位置），对于后续新插入的段落，后续遍历不会遍历到
     * isAfter: 在前还是在后插入新段落
     */
    @Override
    public void addNewParagraph(ListIterator<Object> iterator, P newPara, boolean isAfter) {
        if (isAfter) {
            iterator.add(newPara); // 后插入：直接添加
        } else {
            iterator.previous(); // 前插入：先回退，再添加，最后恢复位置
            iterator.add(newPara);
            iterator.next();
        }

        log.debug("新段落插入成功，位置：{}", isAfter ? "当前段落后" : "当前段落前");
    }

    /**
     * 递归检查对象是否含R元素
     */
    @Override
    public boolean hasRun(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof R) {
                    boolean hasTextInRun = hasTextInRun((R) node);
                    if (hasTextInRun) {
                        return true;
                    }
                } else if (node instanceof P) {
                    boolean subResult = hasRun(((P) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean subResult = hasRun(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean subResult = hasRun(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (subResult) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 递归检查对象是否含修订（新增）
     */
    @Override
    public boolean hasRunIns(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof RunIns) {
                    return true;
                } else if (node instanceof P) {
                    boolean subResult = hasRunIns(((P) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof R) {
                    boolean subResult = hasRunIns(((R) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunDel) {
                    boolean subResult = hasRunIns(((RunDel) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tbl) {
                    boolean subResult = hasRunIns(((Tbl) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tr) {
                    boolean subResult = hasRunIns(((Tr) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tc) {
                    boolean subResult = hasRunIns(((Tc) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean subResult = hasRunIns(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean subResult = hasRunIns(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (subResult) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 递归检查对象是否含修订（删除）
     */
    @Override
    public boolean hasRunDel(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof RunDel) {
                    return true;
                } else if (node instanceof P) {
                    boolean subResult = hasRunDel(((P) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof R) {
                    boolean subResult = hasRunDel(((R) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunIns) {
                    boolean subResult = hasRunDel(((RunIns) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tbl) {
                    boolean subResult = hasRunDel(((Tbl) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tr) {
                    boolean subResult = hasRunDel(((Tr) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tc) {
                    boolean subResult = hasRunDel(((Tc) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean subResult = hasRunDel(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean subResult = hasRunDel(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (subResult) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 递归检查对象是否含CommentRangeStart元素
     */
    @Override
    public boolean hasCommentStart(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof CommentRangeStart) {
                    return true;
                } else if (node instanceof P) {
                    boolean subResult = hasCommentStart(((P) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof R) {
                    boolean subResult = hasCommentStart(((R) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunIns) {
                    boolean subResult = hasCommentStart(((RunIns) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunDel) {
                    boolean subResult = hasCommentStart(((RunDel) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tbl) {
                    boolean subResult = hasCommentStart(((Tbl) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tr) {
                    boolean subResult = hasCommentStart(((Tr) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tc) {
                    boolean subResult = hasCommentStart(((Tc) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean subResult = hasCommentStart(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean subResult = hasCommentStart(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (subResult) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 递归检查对象是否含CommentRangeEnd元素
     */
    @Override
    public boolean hasCommentEnd(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof CommentRangeEnd) {
                    return true;
                } else if (node instanceof P) {
                    boolean subResult = hasCommentEnd(((P) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof R) {
                    boolean subResult = hasCommentEnd(((R) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunIns) {
                    boolean subResult = hasCommentEnd(((RunIns) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof RunDel) {
                    boolean subResult = hasCommentEnd(((RunDel) node).getCustomXmlOrSmartTagOrSdt());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tbl) {
                    boolean subResult = hasCommentEnd(((Tbl) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tr) {
                    boolean subResult = hasCommentEnd(((Tr) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof Tc) {
                    boolean subResult = hasCommentEnd(((Tc) node).getContent());
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean subResult = hasCommentEnd(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (subResult) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean subResult = hasCommentEnd(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (subResult) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 提取段落的完整文本内容（简化版，无需记录节点位置）
     */
    @Override
    public String extractFullParagraphText(List<?> paragraphContent) {
        if (CollectionUtils.isEmpty(paragraphContent)) {
            return "";
        }
        StringBuilder fullText = new StringBuilder();
        extractTextFromNodeList(paragraphContent, fullText);
        return StringUtils.trim(fullText.toString());
    }

    /**
     * 提取段落的完整文本内容（修订模式中修改前的文本）
     */
    @Override
    public String extractFullParagraphTextBeforeModify(List<?> paragraphContent) {
        if (CollectionUtils.isEmpty(paragraphContent)) {
            return "";
        }
        StringBuilder fullText = new StringBuilder();
        extractTextFromNodeListBeforeModify(paragraphContent, fullText);
        return StringUtils.trim(fullText.toString());
    }

    /**
     * 递归提取节点列表中的文本内容
     */
    @Override
    public void extractTextFromNodeList(List<?> nodeList, StringBuilder fullText) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }

        for (Object node : nodeList) {
            if (node instanceof Text) {
                String textValue = ((Text) node).getValue();
                if (StringUtils.isNotEmpty(textValue)) {
                    fullText.append(textValue);
                }
            } else if (node instanceof JAXBElement) {
                extractTextFromNodeList(
                        Collections.singletonList(((JAXBElement<?>) node).getValue()),
                        fullText
                );
            } else if (node instanceof R) {
                extractTextFromNodeList(((R) node).getContent(), fullText);
            } else if (node instanceof RunIns) {
                extractTextFromNodeList(((RunIns) node).getCustomXmlOrSmartTagOrSdt(), fullText);
            } else if (node instanceof SdtElement) {
                extractTextFromNodeList(((SdtElement) node).getSdtContent().getContent(), fullText);
            }
        }
    }

    /**
     * 递归提取节点列表中的文本内容（修订模式中修改前的文本）
     */
    @Override
    public void extractTextFromNodeListBeforeModify(List<?> nodeList, StringBuilder fullText) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }

        for (Object node : nodeList) {
            if (node instanceof Text) {
                String textValue = ((Text) node).getValue();
                if (StringUtils.isNotEmpty(textValue)) {
                    fullText.append(textValue);
                }
            } else if (node instanceof DelText) {
                String delTextValue = ((DelText) node).getValue();
                if (StringUtils.isNotEmpty(delTextValue)) {
                    fullText.append(delTextValue);
                }
            } else if (node instanceof JAXBElement) {
                extractTextFromNodeListBeforeModify(
                        Collections.singletonList(((JAXBElement<?>) node).getValue()),
                        fullText
                );
            } else if (node instanceof R) {
                extractTextFromNodeListBeforeModify(((R) node).getContent(), fullText);
            } else if (node instanceof RunDel) {
                extractTextFromNodeListBeforeModify(
                        ((RunDel) node).getCustomXmlOrSmartTagOrSdt(),
                        fullText
                );
            } else if (node instanceof SdtElement) {
                extractTextFromNodeListBeforeModify(
                        ((SdtElement) node).getSdtContent().getContent(),
                        fullText
                );
            }
        }
    }

    @Override
    public List<String> getCommentId(List<?> nodeList, int type) {
        List<String> res = new ArrayList<>();
        getCommentIdSub(nodeList, res, type);
        return res;
    }

    /**
     * R元素中是否有R$CommentReference元素，若有返回批注ID，若无则返回""
     */
    @Override
    public void hasCommentReference(List<?> nodeList, String res) {
        if (nodeList != null && nodeList.size() > 0 && StringUtils.isEmpty(res)) {
            for (Object node : nodeList) {
                if (node instanceof R) {
                    res = hasCommentReferenceInRun((R) node);
                    return;
                } else if (node instanceof P) {
                    hasCommentReference(((P) node).getContent(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof RunDel) {
                    hasCommentReference(((RunDel) node).getCustomXmlOrSmartTagOrSdt(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof RunIns) {
                    hasCommentReference(((RunIns) node).getCustomXmlOrSmartTagOrSdt(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof Tbl) {
                    hasCommentReference(((Tbl) node).getContent(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof Tr) {
                    hasCommentReference(((Tr) node).getContent(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof Tc) {
                    hasCommentReference(((Tc) node).getContent(), res);
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof JAXBElement) {
                    hasCommentReference(
                            Collections.singletonList(((JAXBElement<?>) node).getValue()),
                            res
                    );
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                } else if (node instanceof SdtElement) {
                    hasCommentReference(
                            ((SdtElement) node).getSdtContent().getContent(),
                            res
                    );
                    if (!StringUtils.isEmpty(res)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 递归查找节点及其子节点中的所有Tbl表格对象
     *
     * @param node 当前查找的节点（顶级元素/子节点）
     * @param tblList 收集到的表格列表（输出参数）
     */
    @Override
    public void findAllTblInNode(Object node, List<Tbl> tblList) {
        if (node == null || tblList == null) {
            return;
        }

        // 1. 直接是Tbl节点：加入列表
        if (node instanceof Tbl) {
            tblList.add((Tbl) node);
            return;
        }

        // 2. 是JAXBElement：解析包装的真实值
        if (node instanceof JAXBElement) {
            JAXBElement<?> jaxbElement = (JAXBElement<?>) node;
            findAllTblInNode(jaxbElement.getValue(), tblList);
            return;
        }

        // 3. 是SdtBlock（结构化文档标签）：解析内部内容
        if (node instanceof SdtBlock) {
            SdtBlock sdtBlock = (SdtBlock) node;
            if (sdtBlock.getSdtContent() instanceof SdtContentBlock) {
                SdtContentBlock sdtContent = (SdtContentBlock) sdtBlock.getSdtContent();

                // 遍历SdtContentBlock中的子节点
                if (sdtContent.getContent() != null) {
                    for (Object child : sdtContent.getContent()) {
                        findAllTblInNode(child, tblList);
                    }
                }
            }

            return;
        }

        // 4. 是其他可遍历的容器节点（如SdtContentBlock、P等）：遍历子节点
        List<?> childNodes = null;
        if (node instanceof SdtContentBlock) {
            childNodes = ((SdtContentBlock) node).getContent();
        } else if (node instanceof P) {
            childNodes = ((P) node).getContent();
        } else if (node instanceof Tbl) {
            childNodes = ((Tbl) node).getContent();
        }

        // 递归遍历子节点
        if (childNodes != null) {
            for (Object child : childNodes) {
                findAllTblInNode(child, tblList);
            }
        }
    }

    /**
     * 创建与原段落格式完全一致的新段落（包含PPr段落格式+RPr字符格式）（带修订模式）
     *
     * @param sourcePara 源段落（继承格式）
     * @param content 新段落内容，注意其中可能带有加粗标识，若带有则加粗处理
     * @param modify 是否采用修订模式
     * @return 格式一致的新段落
     */
    @Override
    public P createParagraphWithSameStyle(P sourcePara, String content, boolean modify) {
        // 深度克隆原段落（完整继承所有格式：缩进、行距、对齐等）
        P newPara = (P) XmlUtils.deepCopy(sourcePara);
        RPr newRPr = getFirstRunPr(newPara);
        List<Object> newContent = newPara.getContent();

        if (!CollectionUtils.isEmpty(newContent)) {
            // 清空克隆段落的文本内容
            newContent.clear();

            // 是否含有加粗标识
            if (content.contains("<bold>") && content.contains("</bold>")) {
                // 处理加粗
                Pattern pattern = Pattern.compile("<bold>(.*?)</bold>");
                Matcher matcher = pattern.matcher(content);
                int lastEnd = 0;

                while (matcher.find()) {
                    // 提取标签前的普通文本
                    String beforeText = content.substring(lastEnd, matcher.start());
                    if (!StringUtils.isEmpty(beforeText)) {
                        R normalRun = createNormalRun(beforeText, newRPr, false);
                        if (modify) {
                            newContent.add(createNormalRunIns(normalRun));
                        } else {
                            newContent.add(normalRun);
                        }
                    }

                    // 提取标签的文本
                    String boldText = matcher.group(1);
                    R boldRun = createNormalRunBold(boldText, newRPr);
                    RunIns ins = createNormalRunIns(boldRun);

                    if (modify) {
                        newContent.add(ins);
                    } else {
                        newContent.add(boldRun);
                    }

                    // 更新指针
                    lastEnd = matcher.end();
                }

                // 最后一段普通文本
                String lastText = content.substring(lastEnd);
                if (!lastText.isEmpty()) {
                    R normalRun = createNormalRun(lastText, newRPr, false);
                    if (modify) {
                        newContent.add(createNormalRunIns(normalRun));
                    } else {
                        newContent.add(normalRun);
                    }
                }

            } else {
                R newRun = createNormalRun(content, newRPr, false);
                RunIns runIns = createNormalRunIns(newRun);

                if (modify) {
                    newContent.add(runIns);
                } else {
                    newContent.add(newRun);
                }
            }
        }

        return newPara;
    }

    /**
     * 创建与原段落格式完全一致的新段落（包含PPr段落格式+RPr字符格式）（未带修订模式）
     *
     * @param ppr 段落格式
     * @param rpr 文本格式
     * @param content 新段落内容
     * @return 格式一致的新段落
     */
    @Override
    public P createParagraphWithSameStyle(PPr ppr, RPr rpr, String content) {
        P newPara = factory.createP();
        newPara.setPPr(ppr);
        List<Object> newContent = newPara.getContent();

        // 清空克隆段落的文本内容
        newContent.clear();

        // 是否含有加粗标识
        if (content.contains("<bold>") && content.contains("</bold>")) {
            Pattern pattern = Pattern.compile("<bold>(.*?)</bold>");
            Matcher matcher = pattern.matcher(content);
            int lastEnd = 0;

            while (matcher.find()) {
                // 提取标签前的普通文本
                String beforeText = content.substring(lastEnd, matcher.start());
                if (!StringUtils.isEmpty(beforeText)) {
                    newContent.add(createNormalRun(beforeText, rpr, false));
                }

                // 提取标签内的文本（分组1就是括号里的内容）
                String boldText = matcher.group(1);
                newContent.add(createNormalRunBold(boldText, rpr));

                // 更新指针
                lastEnd = matcher.end();
            }

            // 最后一段普通文本
            String lastText = content.substring(lastEnd);
            if (!lastText.isEmpty()) {
                newContent.add(createNormalRun(lastText, rpr, false));
            }
        } else {
            R newRun = createNormalRun(content, rpr, false);
            newContent.add(newRun);
        }

        return newPara;
    }

    /**
     * 判断一个批注是不是【空批注】（无任何文本内容）
     */
    @Override
    public boolean isEmptyComment(Comments.Comment comment) {
        for (Object obj : comment.getContent()) {
            if (obj instanceof P) {
                P paragraph = (P) obj;
                String text = extractFullParagraphText(paragraph.getContent());
                if (!StringUtils.isEmpty(text)) {
                    // 有文字 -> 不是空批注
                    return false;
                }
            }
        }

        // 没有任何文字 -> 空批注
        return true;
    }

    /**
     * 创建普通加粗Run（无修订，包含JAXBElement包装的Text）
     *
     * @param text 文本内容
     * @param rPr 格式
     * @return 新生成的R元素
     */
    @Override
    public R createNormalRunBold(String text, RPr rPr) {
        R run = factory.createR();
        RPr newRPr = rPr == null ? factory.createRPr() : XmlUtils.deepCopy(rPr);

        // 开启加粗
        BooleanDefaultTrue bold = factory.createBooleanDefaultTrue();
        bold.setVal(true); // 显式赋值
        newRPr.setB(bold);
        run.setRPr(newRPr);

        // 创建文本
        Text textNode = factory.createText();
        textNode.setValue(text);
        JAXBElement<Text> textJaxb = factory.createRT(textNode);
        run.getContent().add(textJaxb);

        return run;
    }

    @Override
    public List<List<Object>> extractFullParagraphNodeList(List<?> paragraphContent) {
        List<List<Object>> res = new LinkedList<>();
        List<Object> sub = new LinkedList<>();

        if (CollectionUtils.isEmpty(paragraphContent)) {
            return res;
        }

        StringBuilder fullText = new StringBuilder();
        extractNodeFromNodeList(paragraphContent, fullText, sub, res);

        // 最后把剩余的内容加入
        if (fullText.length() > 0) {
            sub.add(fullText.toString());
        }

        if (sub.size() > 0) {
            res.add(sub);
        }

        return res;
    }

    @Override
    public void replaceKeyWordInPara(P paragraph, String pgText, String keyword, String replaceContent, boolean modify) {
        // 1、入参校验：空值直接返回
        if (StringUtils.isEmpty(pgText) || StringUtils.isEmpty(keyword)) {
            log.warn("段落文本为空, 或待替换关键字为空, 无需处理（关键字：{}）", keyword);
            return;
        }

        // 清空段落原有内容（保留段落格式PPr）
        List<Object> paraContent = paragraph.getContent();
        RPr rPr = getFirstRunPrP(paragraph);
        RPr cloneRpr = XmlUtils.deepCopy(rPr);
        paraContent.clear();

        // 2、替换文本内容
        // 不采用修订模式
        if (!modify) {
            String newPgText = pgText.replaceAll(
                    Pattern.quote(keyword),
                    (StringUtils.isEmpty(replaceContent)
                            || replaceContent.equals("null")
                            || replaceContent.equals("NULL")) ? "" : replaceContent
            );
            R normalRun = createNormalRun(newPgText, rPr, false);
            paraContent.add(normalRun);
        } else {
            // 采用修订模式，采用滑窗去匹配关键字
            int pgTextLen = pgText.length();
            int keyLen = keyword.length();
            int left = 0; // 左指针

            while (left <= pgTextLen - keyLen) {
                int idx = pgText.indexOf(keyword, left); // 从下标 left 位置开始，往后找 keyword 第一次出现的位置

                // 未找到关键字，处理最后一段文字
                if (idx == -1) {
                    // 最后一段文字
                    String tail = pgText.substring(left);
                    if (!tail.isEmpty()) {
                        paraContent.add(createNormalRun(tail, cloneRpr, false));
                    }
                    break;
                }

                // 截取关键字之前的文本
                String before = pgText.substring(left, idx);
                if (!before.isEmpty()) {
                    paraContent.add(createNormalRun(before, cloneRpr, false));
                }

                // 删除原关键字
                R del = createNormalRun(keyword, cloneRpr, true);
                paraContent.add(createNormalRunDel(del));

                // 插入新内容
                R ins = createNormalRun(replaceContent, cloneRpr, false);
                paraContent.add(createNormalRunIns(ins));

                // 滑动窗口：指针移动
                left = idx + keyLen;
            }
        }
    }

    /**
     * 创建RunDel
     *
     * @param r R元素
     * @return 新生成的RunDel元素
     */
    @Override
    public RunDel createNormalRunDel(R r) {
        RunDel runDel = factory.createRunDel();
        runDel.getCustomXmlOrSmartTagOrSdt().add(r);
        return runDel;
    }

    /**
     * 递归检查对象是否含分页元素
     */
    @Override
    public boolean hasPage(List<?> nodeList) {
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof R) {
                    boolean res = hasLastRenderedPageBreak((R) node);
                    if (res) {
                        return true;
                    }
                } else if (node instanceof P) {
                    boolean res = hasPage(((P) node).getContent());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof RunDel) {
                    boolean res = hasPage(((RunDel) node).getCustomXmlOrSmartTagOrSdt());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof RunIns) {
                    boolean res = hasPage(((RunIns) node).getCustomXmlOrSmartTagOrSdt());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof Tbl) {
                    boolean res = hasPage(((Tbl) node).getContent());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof Tr) {
                    boolean res = hasPage(((Tr) node).getContent());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof Tc) {
                    boolean res = hasPage(((Tc) node).getContent());
                    if (res) {
                        return true;
                    }
                } else if (node instanceof JAXBElement) {
                    boolean res = hasPage(
                            Collections.singletonList(((JAXBElement<?>) node).getValue())
                    );
                    if (res) {
                        return true;
                    }
                } else if (node instanceof SdtElement) {
                    boolean res = hasPage(
                            ((SdtElement) node).getSdtContent().getContent()
                    );
                    if (res) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 替换段落文本（带修订模式，先删后增）
     *
     * @param paragraph 目标段落
     * @param oldText 旧文本
     * @param newText 新文本
     * @param rpr 格式
     * @param modify 是否带修订
     */
    @Override
    public void replaceParagraphText(P paragraph, String oldText, String newText, RPr rpr, boolean modify) {
        if (paragraph != null && !oldText.equals(newText)) {
            List<Object> paraContent = paragraph.getContent();

            // 文本格式
            RPr firstRunPrP = rpr == null ? getFirstRunPrP(paragraph) : rpr;
            RPr cloneRpr = firstRunPrP != null ? XmlUtils.deepCopy(firstRunPrP) : null;

            // 段落格式（居中）
            PPr ppr = paragraph.getPPr();
            if (ppr == null) {
                ppr = factory.createPPr();
                paragraph.setPPr(ppr);
            }

            Jc jc = ppr.getJc();
            if (jc == null) {
                jc = factory.createJc();
                ppr.setJc(jc);
            }

            jc.setVal(JcEnumeration.CENTER);

            // 替换文本
            paraContent.clear();

            if (modify) {
                R delRun = createNormalRun(oldText, cloneRpr, true);
                RunDel delRunDel = createNormalRunDel(delRun);
                paraContent.add(delRunDel);

                R insRun = createNormalRun(newText, cloneRpr, false);
                RunIns insRunIns = createNormalRunIns(insRun);
                paraContent.add(insRunIns);
            } else {
                R run = createNormalRun(newText, cloneRpr, false);
                paraContent.add(run);
            }
        }
    }

    // 校验文本的每层标题和配置记录的每层标题是否匹配
    @Override
    public boolean isTitleMatched(Map<Integer, String> textTitleMap,
                                  Map<Integer, String> configTitleMap,
                                  int matchLevel) {
        if (matchLevel < 1) {
            return false;
        }

        for (int i = 1; i <= matchLevel; i++) {
            if (!textTitleMap.get(i).replaceAll("\\s", "")
                    .equals(configTitleMap.get(i).replaceAll("\\s", ""))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 在关键字前后追加文本（整体替换段落内容，保留格式）
     * @param paragraph 目标段落
     * @param originalText 段落原有完整文本
     * @param keyword 关键字
     * @param appendText 要追加的文本
     * @param isBefore true=关键字前追加，false=关键字后追加
     */
    @Override
    public void appendTextToPara(P paragraph, String originalText, String keyword, String appendText, boolean isBefore) {
        // 1、入参校验：空值直接返回
        if (StringUtils.isEmpty(keyword) || StringUtils.isEmpty(appendText)) {
            log.warn("关键字或追加（替换）文本为空，无需处理（关键字：{}，追加（替换）文本：{}）", keyword, appendText);
            return;
        }

        // 清空段落原有内容（保留段落格式PPr）
        List<Object> paraContent = paragraph.getContent();
        RPr rPr = getFirstRunPrP(paragraph);
        paraContent.clear();

        // 2、拆分原有文本，仅对新增部分添加修订标记
        int keywordIndex = originalText.indexOf(keyword);
        // 2.1 构建关键字前的原有文本（无修订）
        String beforeKeywordText = originalText.substring(0, isBefore ? keywordIndex : (keywordIndex + keyword.length()));
        if (StringUtils.isNotEmpty(beforeKeywordText)) {
            R normalRun = createNormalRun(beforeKeywordText, rPr, false);
            paraContent.add(normalRun);
        }
        // 2.2 构建新增文本（带修订标记）
        R insertRun = createNormalRun(appendText, rPr, false);
        RunIns runIns = createNormalRunIns(insertRun);
        paraContent.add(runIns);
        // 2.3 构建关键字后的原有文本（无修订）
        String afterKeywordText = originalText.substring(isBefore ? keywordIndex : (keywordIndex + keyword.length()));
        if (StringUtils.isNotEmpty(afterKeywordText)) {
            R normalRun = createNormalRun(afterKeywordText, rPr, false);
            paraContent.add(normalRun);
        }
    }

    /**
     * 生成新段落的带序号内容
     */
    @Override
    public String generateNewParagraphContent(String content,
                                              Map<Integer, String> textTitleMap,
                                              boolean generateNext) {
        StringBuilder paraContent = new StringBuilder();

        // 获取当前有效标题层级前缀
        for (int i = 5; i >= 1; i--) {
            String titlePrefix = textTitleMap.get(i);
            if (!StringUtils.isEmpty(titlePrefix)) {
                String targetPrefix = generateNext
                        ? generateNextTitlePrefix(titlePrefix)
                        : titlePrefix;
                paraContent.append(targetPrefix).append(content);
                break;
            }
        }

        // 无层级前缀时直接使用内容
        if (paraContent.length() == 0) {
            paraContent.append(content);
        }

        return paraContent.toString();
    }

    /**
     * 按层级+数字生成标题前缀
     * @param level 标题层级（1-5）
     * @param num 序号数字（如1、2、3）
     * @return 对应格式的标题前缀（如：5级+2 -> "2）"）
     */
    @Override
    public String generateTitlePrefixByLevelAndNum(int level, int num) {
        // 入参校验：层级超出范围/数字小于1，返回空字符串
        if (level < 1 || level > MAX_TITLE_LEVEL || num < 1) {
            log.warn("标题层级或数字非法，层级：{}，数字：{}", level, num);
            return "";
        }

        String prefix = "";
        switch (level) {
            case 1:
                prefix = intToChineseNum(num) + "、";
                break;
            case 2:
                prefix = "（" + intToChineseNum(num) + "）";
                break;
            case 3:
                prefix = num + "、";
                break;
            case 4:
                prefix = "（" + num + "）";
                break;
            case 5:
                prefix = num + "）";
                break;
            // 兜底：理论上不会走到这里（已做入参校验）
            default:
                prefix = "";
                break;
        }

        return prefix;
    }

    @Override
    public void deleteKeyWordInParaBatch(P paragraph,
                                         String pgText,
                                         List<String> keywords,
                                         boolean modify) {
        // 1、入参校验：空值直接返回
        if (StringUtils.isEmpty(pgText) || CollectionUtils.isEmpty(keywords)) {
            log.warn("段落文本为空，或待删除关键字列表为空，无需处理");
            return;
        }

        // 清空段落原有内容（保留段落格式PPr）
        List<Object> paraContent = paragraph.getContent();
        RPr rPr = getFirstRunPr(paragraph);
        RPr cloneRpr = XmlUtils.deepCopy(rPr);
        paraContent.clear();

        // 2、不采用修订模式：批量替换所有关键字
        if (!modify) {
            String newPgText = pgText;
            for (String keyword : keywords) {
                if (StringUtils.isNotEmpty(keyword)) {
                    newPgText = newPgText.replaceAll(Pattern.quote(keyword), "");
                }
            }

            R normalRun = createNormalRun(newPgText, rPr, false);
            paraContent.add(normalRun);
        } else {
            // 采用修订模式：采用正则法匹配关键字
            // 构造正则：匹配任意一个关键词（自动转义、支持特殊字符）
            StringBuilder regex = new StringBuilder();

            for (String kw : keywords) {
                if (!kw.isEmpty()) {
                    if (regex != null && regex.length() > 0) {
                        regex.append("|");
                    }
                    regex.append(Pattern.quote(kw));
                }
            }

            // 用正则分割，同时保留分隔符（关键词）
            Pattern pattern = Pattern.compile(regex.toString());
            Matcher matcher = pattern.matcher(pgText);
            int lastIndex = 0;

            while (matcher.find()) {
                // 前面的普通文本
                String before = pgText.substring(lastIndex, matcher.start());
                if (!before.isEmpty()) {
                    paraContent.add(createNormalRun(before, cloneRpr, false));
                }

                // 匹配到的关键词
                String key = matcher.group();
                R del = createNormalRun(key, cloneRpr, true);
                paraContent.add(createNormalRunDel(del));

                // 更新指针
                lastIndex = matcher.end();
            }

            // 最后一段文本
            if (lastIndex < pgText.length()) {
                paraContent.add(
                        createNormalRun(
                                pgText.substring(lastIndex),
                                cloneRpr,
                                false
                        )
                );
            }
        }
    }

    @Override
    public void deleteKeyWordInParaOne(P paragraph, String pgText, String keyword, boolean modify) {
        // 1、入参校验：空值直接返回
        if (StringUtils.isEmpty(pgText) || StringUtils.isEmpty(keyword)) {
            log.warn("段落文本为空，或待删除关键字为空，无需处理（关键字：{}）", keyword);
            return;
        }

        // 清空段落原有内容（保留段落格式PPr）
        List<Object> paraContent = paragraph.getContent();
        RPr rPr = getFirstRunPr(paragraph);
        RPr cloneRpr = XmlUtils.deepCopy(rPr);
        paraContent.clear();

        // 2、删除文本关键字内容
        // 不采用修订模式
        if (!modify) {
            String newPgText = pgText.replaceAll(Pattern.quote(keyword), "");
            R normalRun = createNormalRun(newPgText, rPr, false);
            paraContent.add(normalRun);
        } else {
            // 采用修订模式，采用普通法匹配关键字
            int pgTextLen = pgText.length();
            int keyLen = keyword.length();
            int left = 0; // 左指针

            while (left <= pgTextLen - keyLen) {
                int idx = pgText.indexOf(keyword, left); // 从下标 left 位置开始，往后找 keyword 第一次出现的位置

                // 未找到关键字，处理最后一段文字
                if (idx == -1) {
                    // 最后一段文字
                    String tail = pgText.substring(left);
                    if (!tail.isEmpty()) {
                        paraContent.add(createNormalRun(tail, cloneRpr, false));
                    }
                    break;
                }

                // 截取关键字之前的文本
                String before = pgText.substring(left, idx);
                if (!before.isEmpty()) {
                    paraContent.add(createNormalRun(before, cloneRpr, false));
                }

                // 删除原关键字
                R del = createNormalRun(keyword, cloneRpr, true);
                paraContent.add(createNormalRunDel(del));

                // 滑动窗口：指针移动
                left = idx + keyLen;
            }
        }
    }

    /**
     * 提取表格行的完整文本内容
     * @param tr 表格行对象
     * @return 行文本
     */
    @Override
    public String extractTableRowText(Tr tr) {
        if (tr == null || CollectionUtils.isEmpty(tr.getContent())) {
            log.debug("提取表格行的完整文本内容时，表格行为空，返回空文本");
            return "";
        }

        StringBuilder rowText = new StringBuilder();
        for (Object cellObj : tr.getContent()) {
            String cellText = "";

            // 1、处理JAXBElement包装的Tc
            if (cellObj instanceof JAXBElement) {
                JAXBElement<?> jaxbElement = (JAXBElement<?>) cellObj;
                cellObj = jaxbElement.getValue();
            }

            // 2、解析Tc单元格的文本
            if (cellObj instanceof Tc) {
                Tc tc = (Tc) cellObj;
                cellText = extractTableCellText(tc); // 提取单元格文本
            }

            // 3、列之间用\t分隔，保持格式
            rowText.append(cellText).append("\t");
        }

        // 去除最后一个\t，trim掉首尾空格
        String result = StringUtils.trim(
                rowText.toString().replaceAll("\\t$", "")
        );

        log.debug("提取表格行的完整文本内容，表格行文本提取完成：{}", result);
        return result;
    }

    /**
     * 在指定表格行下方插入N行新数据
     * @param table 目标表格
     * @param targetRow 目标行（匹配关键字的行）
     * @param targetRowIndex 目标行在目标表格中的索引
     * @param insertRowNum 待插入行数
     */
    @Override
    public void insertRowsBelow(Tbl table, Tr targetRow, int targetRowIndex, long insertRowNum,
                                List<ModifyConfigTableDetail> tableDetailList) {
        // 1、获取原表格列数（从目标行获取）
        int columnNum = 0;
        for (Object cellObj : targetRow.getContent()) {
            // 处理JAXBElement包装的情况，获取真实的节点
            Object realCellObj = cellObj;
            if (cellObj instanceof JAXBElement) {
                JAXBElement<?> jaxbElement = (JAXBElement<?>) cellObj;
                realCellObj = jaxbElement.getValue();
            }

            if (realCellObj instanceof Tc) {
                columnNum++;
            }
        }

        if (columnNum == 0) {
            log.warn("向表格插入行数据时，目标行无列数据，无法插入新行");
            return;
        }

        // 2、先获取目标行的所有单元格（解包JAXBElement）
        List<Object> tableRows = table.getContent();
        List<Tc> targetCells = new ArrayList<>();
        for (Object cellObj : targetRow.getContent()) {
            Object realCell = cellObj instanceof JAXBElement
                    ? ((JAXBElement<?>) cellObj).getValue()
                    : cellObj;
            if (realCell instanceof Tc) {
                targetCells.add((Tc) realCell);
            }
        }

        if (columnNum != targetCells.size()) {
            log.error("在指定表格行下方插入N行新数据时，表格列数不一致！");
            return;
        }

        // 3、生成并插入新行
        for (int i = 0; i < insertRowNum; i++) {
            // 3.1、创建新行（继承目标行的格式并清空原有内容，保留行属性（TrPr））
            Tr newRow = (Tr) XmlUtils.deepCopy(targetRow);
            newRow.getContent().clear();

            // 3.2、为新行创建单元格
            for (int col = 0; col < columnNum; col++) {
                Tc newCell = null;
                RPr sourceRunPr = null;

                // 3.2.1、复制目标单元格格式（优先用目标行对应列的单元格）
                Tc sourceTc = targetCells.get(col);
                sourceRunPr = getFirstRunPrTc(sourceTc);
                newCell = (Tc) XmlUtils.deepCopy(sourceTc);
                newCell.getContent().clear();

                // 3.2.2 创建新单元格的文本内容（继承格式）
                String cellText = "";
                for (ModifyConfigTableDetail t : tableDetailList) {
                    if (t.getRowNumber() == i + 1 && t.getColNumber() == col + 1) {
                        cellText = t.getContent();
                        break;
                    }
                }

                P cellPara = Context.getWmlObjectFactory().createP();

                // 仅新增文本添加修订标记
                if (StringUtils.isNotEmpty(cellText)) {
                    cellText = cellText.trim();
                    String[] cellTextSplit = cellText.split(PARAGRAPH_SPLIT_FLAG);
                    for (int s = 0; s < cellTextSplit.length; s++) {
                        String segment = cellTextSplit[s];
                        if (StringUtils.isEmpty(segment)) continue;

                        R cellRun = createNormalRun(segment, sourceRunPr, false);

                        // 最后一行不换行
                        if (s != cellTextSplit.length - 1) {
                            Br br = factory.createBr(); // Word换行标记
                            cellRun.getContent().add(br);
                        }

                        RunIns runIns = createNormalRunIns(cellRun);
                        cellPara.getContent().add(runIns);
                    }
                }

                // 3.2.4 继承段落格式
                // 提取源单元格的第一个段落，复制其格式
                for (Object tcContent : sourceTc.getContent()) {
                    Object realContent = tcContent instanceof JAXBElement
                            ? ((JAXBElement<?>) tcContent).getValue()
                            : tcContent;
                    if (realContent instanceof P) {
                        copyParagraphFormat((P) realContent, cellPara);
                        break; // 只复制第一个段落的格式即可
                    }
                }

                // 3.2.5 把带格式的段落加入单元格
                newCell.getContent().add(cellPara);

                // 确保单元格格式完整（兜底）
                copyTableCellFormat(
                        col < targetCells.size() ? targetCells.get(col) : null,
                        newCell
                );

                // 3.6 添加单元格到新行
                newRow.getContent().add(newCell);
            }

            // 更新表格内容，插入新行到目标行下方
            tableRows.add(targetRowIndex + 1 + i, newRow);
        }
    }

    /**
     * 复制段落格式（对齐方式、行距、缩进等）
     * @param sourceP 源段落（目标行的段落）
     * @param targetP 目标段落（新行的段落）
     */
    @Override
    public void copyParagraphFormat(P sourceP, P targetP) {
        if (sourceP == null || targetP == null) {
            return;
        }

        // 复制段落属性（PPr包含对齐、行距等所有段落格式）
        PPr sourcePPr = sourceP.getPPr();
        if (sourcePPr != null) {
            // 深拷贝避免引用同一对象导致格式联动修改
            PPr targetPPr = (PPr) XmlUtils.deepCopy(sourcePPr);
            targetP.setPPr(targetPPr);
        }
    }

    /**
     * 仅替换段落的序号前缀（如（2）→（3）），仅序号变化时为序号加修订标记，保留正文和原有修订格式
     * @param paragraph 目标段落
     * @param paraText 目标段落文本内容
     * @param oldPrefix 旧序号前缀（如"（2）"）
     * @param newPrefix 新序号前缀（如"（3）"）
     * @param newPara 是否是新插入的新段落
     */
    @Override
    public void replaceTitlePrefixOnly(P paragraph, String paraText, String oldPrefix, String newPrefix, boolean newPara) {
        // 校验
        if (paragraph == null) {
            return;
        }

        if (oldPrefix.equals(newPrefix)) {
            return;
        }

        List<Object> contentList = paragraph.getContent();
        if (CollectionUtils.isEmpty(contentList)) {
            return;
        }

        RPr firstRunPrP = getFirstRunPrP(paragraph);
        RPr cloneRpr = XmlUtils.deepCopy(firstRunPrP);

        // 从段落头部 逐个Run遍历，删除匹配 oldPrefix 的文本
        // 只删开头的序号，后面的内容全部保留
        int needRemoveLength = oldPrefix.length();
        Iterator<Object> iterator = contentList.iterator();

        if (iterator == null) {
            return;
        }

        while (iterator.hasNext()) {
            Object obj = iterator.next();
            boolean isRun = obj instanceof R;
            boolean isRunIns = obj instanceof RunIns;

            // 非Run、RunIns的元素跳过
            if (!isRun && !isRunIns) {
                continue;
            }

            String runText = isRun
                    ? RunUtils.getText((R) obj)
                    : RunInsUtils.getText((RunIns) obj);

            if (StringUtils.isEmpty(runText) || !runText.startsWith(oldPrefix)) {
                continue;
            }

            // 除了包含前缀还有其他文本内容
            if (runText.length() > needRemoveLength) {
                // 获取剩余部分
                String part = runText.substring(needRemoveLength);
                iterator.remove();

                // 先把剩余部分补上
                R partRun = createNormalRun(part, cloneRpr, false);
                contentList.add(0, isRunIns ? createNormalRunIns(partRun) : partRun);

                R insRun = createNormalRun(newPrefix, cloneRpr, false);
                RunIns insRunIns = createNormalRunIns(insRun);
                contentList.add(0, insRunIns);

                if (!newPara) {
                    R delRun = createNormalRun(oldPrefix, cloneRpr, true);
                    RunDel delRunDel = createNormalRunDel(delRun);
                    contentList.add(0, delRunDel);
                }
            } else {
                iterator.remove();

                R insRun = createNormalRun(newPrefix, cloneRpr, false);
                RunIns insRunIns = createNormalRunIns(insRun);
                contentList.add(0, insRunIns);

                if (!newPara) {
                    R delRun = createNormalRun(oldPrefix, cloneRpr, true);
                    RunDel delRunDel = createNormalRunDel(delRun);
                    contentList.add(0, delRunDel);
                }
            }

            // 处理完退出
            break;
        }
    }

    /**
     * 从源单元格提取第一个有效文本运行对象（用于继承字体格式）
     * @param sourceTc 源单元格
     * @return 第一个包含文本的R对象，无则返回null
     */
    @Override
    public RPr getFirstRunPrTc(Tc sourceTc) {
        if (sourceTc == null || CollectionUtils.isEmpty(sourceTc.getContent())) {
            return null;
        }

        // 遍历单元格中的段落
        for (Object tcContent : sourceTc.getContent()) {
            Object realContent = tcContent instanceof JAXBElement
                    ? ((JAXBElement<?>) tcContent).getValue()
                    : tcContent;

            if (realContent instanceof P) {
                P para = (P) realContent;
                return getFirstRunPrP(para);
            }
        }

        return null;
    }

    /**
     * 复制单元格格式（宽度、边距、对齐、边框等）
     * @param sourceTc 源单元格（目标行的单元格）
     * @param targetTc 目标单元格（新行的单元格）
     */
    @Override
    public void copyTableCellFormat(Tc sourceTc, Tc targetTc) {
        if (sourceTc == null || targetTc == null) {
            return;
        }

        // 复制单元格属性（TcPr包含宽度、边距、对齐等）
        TcPr sourceTcPr = sourceTc.getTcPr();
        if (sourceTcPr != null) {
            TcPr targetTcPr = (TcPr) XmlUtils.deepCopy(sourceTcPr);
            targetTc.setTcPr(targetTcPr);
        }
    }

    /**
     * replaceContent中的元素是段落P和表格元素
     */
    @Override
    public void replaceKeyWordInParaAnything(ListIterator<Object> mainIterator,
                                             P paragraph,
                                             String pgText,
                                             String keyword,
                                             List<Object> replaceContent,
                                             boolean modify) {
        // 1、入参校验：空值直接返回
        if (StringUtils.isEmpty(pgText) || StringUtils.isEmpty(keyword)) {
            log.warn("段落文本为空，或待替换关键字为空，无需处理（关键字：{}）", keyword);
            return;
        }

        // 清空段落原有内容（保留段落格式PPr）
        List<Object> paraContent = paragraph.getContent();
        paraContent.clear();

        mainIterator.remove();
        for (int i = 0; i < replaceContent.size(); i++) {
            mainIterator.add(replaceContent.get(i));
        }
    }

    /**
     * 提取单个表格单元格（Tc）的文本内容（递归解析P->R->Text层级）
     * @param tc 单元格对象
     * @return 单元格文本（空则返回""）
     */
    @Override
    public String extractTableCellText(Tc tc) {
        if (tc == null || CollectionUtils.isEmpty(tc.getContent())) {
            log.debug("提取单个表格单元格（Tc）的文本内容时，表格行为空，返回空文本");
            return "";
        }

        StringBuilder cellText = new StringBuilder();

        // 遍历Tc的子节点（主要是P段落）
        for (Object tcContent : tc.getContent()) {
            // 处理JAXBElement包装的节点
            if (tcContent instanceof JAXBElement) {
                JAXBElement<?> jaxbElement = (JAXBElement<?>) tcContent;
                tcContent = jaxbElement.getValue();
            }

            // 解析段落（P）中的文本
            if (tcContent instanceof P) {
                P para = (P) tcContent;

                // 复用提取段落文本的方法
                String paraText = extractFullParagraphText(para.getContent());
                cellText.append(paraText);
            }
        }

        return StringUtils.trim(cellText.toString());
    }

    /**
     * R元素中是否有R$LastRenderedPageBreak元素，若有返回批注ID，若无则返回-1
     */
    public boolean hasLastRenderedPageBreak(R r) {
        boolean res = false;
        List<?> nodeList = r.getContent();

        for (Object obj : nodeList) {
            if (obj instanceof JAXBElement<?>) {
                JAXBElement<?> jaxbElement = (JAXBElement<?>) obj;

                // 判断是不是 commentReference
                if ("lastRenderedPageBreak".equals(jaxbElement.getName().getLocalPart())) {
                    res = true;
                }
            }
        }

        return res;
    }

    public void extractNodeFromNodeList(List<?> paragraphContent, StringBuilder fullText,
                                        List<Object> sub, List<List<Object>> res) {
        if (CollectionUtils.isEmpty(paragraphContent)) {
            return;
        }

        for (Object node : paragraphContent) {
            if (node instanceof Text) {
                String textValue = ((Text) node).getValue();
                if (StringUtils.isNotEmpty(textValue)) {
                    fullText.append(textValue);
                }
            } else if (node instanceof CTOMath) {
                // 公式保留
                if (fullText.length() > 0) {
                    sub.add(fullText.toString().trim());
                    fullText.setLength(0);
                }
                sub.add(node);
            } else if (node instanceof Br || node instanceof R.Cr) {
                // 拆分：当前行结束，存入list，清空fullText，sub
                if (fullText.length() > 0) {
                    sub.add(fullText.toString());
                    fullText.setLength(0);
                }

                res.add(new LinkedList<>(sub));
                sub.clear();
            } else if (node instanceof JAXBElement) {
                extractNodeFromNodeList(
                        Collections.singletonList(((JAXBElement<?>) node).getValue()),
                        fullText, sub, res
                );
            } else if (node instanceof R) {
                extractNodeFromNodeList(((R) node).getContent(), fullText, sub, res);
            } else if (node instanceof RunIns) {
                extractNodeFromNodeList(((RunIns) node).getCustomXmlOrSmartTagOrSdt(),
                        fullText, sub, res);
            } else if (node instanceof SdtElement) {
                extractNodeFromNodeList(((SdtElement) node).getSdtContent().getContent(),
                        fullText, sub, res);
            }
        }
    }

    /**
     * R元素中是否有R$CommentReference元素，若有返回批注ID，若无则返回-1
     */
    public String hasCommentReferenceInRun(R r) {
        String res = "";
        List<?> nodeList = r.getContent();
        for (Object obj : nodeList) {
            if (obj instanceof JAXBElement<?>) {
                JAXBElement<?> jaxbElement = (JAXBElement<?>) obj;
                // 判断是不是 commentReference
                if ("commentReference".equals(jaxbElement.getName().getLocalPart())) {
                    // 拿到内部的 R$CommentReference
                    R.CommentReference commentRef = (R.CommentReference) jaxbElement.getValue();
                    // 获取 id
                    res = String.valueOf(commentRef.getId());
                }
            }
        }
        return res;
    }

    public void getCommentIdSub(List<?> nodeList, List<String> res, int type) {
        if (CollectionUtils.isEmpty(nodeList)) {
            return;
        }

        for (Object node : nodeList) {
            if (node instanceof CommentRangeStart && type == COMMENT_RANGE_START) {
                res.add(String.valueOf(((CommentRangeStart) node).getId()));
            } else if (node instanceof CommentRangeEnd && type == COMMENT_RANGE_END) {
                res.add(String.valueOf(((CommentRangeEnd) node).getId()));
            } else if (node instanceof JAXBElement) {
                getCommentIdSub(
                        Collections.singletonList(((JAXBElement<?>) node).getValue()),
                        res,
                        type
                );
            } else if (node instanceof R) {
                getCommentIdSub(((R) node).getContent(), res, type);
            } else if (node instanceof RunIns) {
                getCommentIdSub(((RunIns) node).getCustomXmlOrSmartTagOrSdt(), res, type);
            } else if (node instanceof RunDel) {
                getCommentIdSub(((RunDel) node).getCustomXmlOrSmartTagOrSdt(), res, type);
            } else if (node instanceof SdtElement) {
                getCommentIdSub(((SdtElement) node).getSdtContent().getContent(), res, type);
            } else if (node instanceof Tbl) {
                getCommentIdSub(((Tbl) node).getContent(), res, type);
            } else if (node instanceof Tr) {
                getCommentIdSub(((Tr) node).getContent(), res, type);
            } else if (node instanceof Tc) {
                getCommentIdSub(((Tc) node).getContent(), res, type);
            } else if (node instanceof P) {
                getCommentIdSub(((P) node).getContent(), res, type);
            }
        }
    }

    /**
     * R元素中是否有Text元素
     */
    public boolean hasTextInRun(R r) {
        List<?> nodeList = r.getContent();
        if (nodeList != null && nodeList.size() > 0) {
            for (Object node : nodeList) {
                if (node instanceof Text ||
                        (node instanceof JAXBElement && ((JAXBElement<?>) node).getValue() instanceof Text)) {
                    return true;
                }
            }
        }

        return false;
    }
}
