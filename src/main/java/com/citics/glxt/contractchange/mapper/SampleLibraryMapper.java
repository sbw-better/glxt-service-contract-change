package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.domain.SampleLibraryControlDO;
import org.apache.ibatis.annotations.Param;

public interface SampleLibraryMapper {
    SampleLibraryControlDO control();
    SampleLibraryControlDO lockControl();
    int claim(@Param("operationId") String operationId);
    int release(@Param("operationId") String operationId);
    int switchVersion(@Param("control") SampleLibraryControlDO control);
    int usedVersion(@Param("version") String version);
    int committed(@Param("operationId") String operationId, @Param("expectedSamples") int expectedSamples);
    int complete(@Param("operationId") String operationId);
}
