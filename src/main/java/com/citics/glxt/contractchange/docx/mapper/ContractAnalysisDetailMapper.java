package com.citics.glxt.contractchange.docx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ContractAnalysisDetailMapper extends BaseMapper<ContractAnalysisDetail> {

    int insert(ContractAnalysisDetail obj);

    int insertBatch(List<ContractAnalysisDetail> objList);

    @Insert({
            "<script>",
            "INSERT INTO TPIF_HTJXZB_HTJXMXJL (ID,TPIF_HTJXZB_ID,MK,ZJMC,SFHBTQZ,HQZSBTJB,HQZSBT,YJBT,EJBT," +
                    "SANJBT,SIJBT,WJBT,DLXH,DLCJXX,YDLNR,XDLNR,SFXD,XDLX,PZBH,YM) ",
            "SELECT SEQ_TPIF_HTJXZB_HTJXMXJL.nextval, t.* FROM (",
            "<foreach collection='list' item='item' separator='UNION ALL'>",
            "SELECT ",
            "#{item.masterId,jdbcType=DECIMAL},",
            "#{item.module,jdbcType=DECIMAL},",
            "#{item.chapterName,jdbcType=VARCHAR},",
            "#{item.isBelongTitle,jdbcType=DECIMAL},",
            "#{item.titleLevel,jdbcType=VARCHAR},",
            "#{item.titlePrefix,jdbcType=VARCHAR},",
            "#{item.firstTitle,jdbcType=VARCHAR},",
            "#{item.secondTitle,jdbcType=VARCHAR},",
            "#{item.thirdTitle,jdbcType=VARCHAR},",
            "#{item.fourthTitle,jdbcType=VARCHAR},",
            "#{item.fifthTitle,jdbcType=VARCHAR},",
            "#{item.contentNumber,jdbcType=VARCHAR},",
            "#{item.paraLevelInfo,jdbcType=VARCHAR},",
            "to_clob(#{item.originalContent,jdbcType=CLOB}),",
            "to_clob(#{item.newContent,jdbcType=CLOB}),",
            "#{item.haveModify,jdbcType=DECIMAL},",
            "#{item.modifyType,jdbcType=DECIMAL},",
            "#{item.annotationNumber,jdbcType=VARCHAR},",
            "#{item.pageNumber,jdbcType=VARCHAR}",
            "FROM DUAL",
            "</foreach>",
            ") t",
            "</script>"
    })
    void insertBatch2(@Param("list") List<ContractAnalysisDetail> list);
}

