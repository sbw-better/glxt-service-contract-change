package com.citics.glxt.contractchange.docx.aspose.model;

import com.aspose.words.Paragraph;

/** Aspose 文档处理流程共用的标准化段落信息。 */
public class ContractParagraph {
    private final Paragraph source;
    private final String text;
    private final long module;
    private final String[] titleNumbers;
    private final String[] titleTexts;
    private final int titleLevel;
    private final String titlePrefix;

    public ContractParagraph(Paragraph source, String text, long module,
                             String[] titleNumbers, String[] titleTexts,
                             int titleLevel, String titlePrefix) {
        this.source = source;
        this.text = text;
        this.module = module;
        this.titleNumbers = titleNumbers;
        this.titleTexts = titleTexts;
        this.titleLevel = titleLevel;
        this.titlePrefix = titlePrefix;
    }

    public Paragraph getSource() { return source; }
    public String getText() { return text; }
    public long getModule() { return module; }
    public String[] getTitleNumbers() { return titleNumbers; }
    public String[] getTitleTexts() { return titleTexts; }
    public int getTitleLevel() { return titleLevel; }
    public String getTitlePrefix() { return titlePrefix; }
}

