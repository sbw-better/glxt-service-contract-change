package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.contractcompare.model.AnalysisLogDO;
/** HT_ANALYSIS_LOG 数据访问。 */
public interface AnalysisLogMapper {
    int insertLog(AnalysisLogDO log);
    int updateLog(AnalysisLogDO log);
}
