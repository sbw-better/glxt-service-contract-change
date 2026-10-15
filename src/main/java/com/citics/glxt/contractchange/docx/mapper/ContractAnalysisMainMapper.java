package com.citics.glxt.contractchange.docx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisMain;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractAnalysisMainMapper extends BaseMapper<ContractAnalysisMain> {
    int insert(ContractAnalysisMain obj);
}
