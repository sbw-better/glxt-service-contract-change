package com.citics.glxt.contractchange.controller;

import com.citics.glxt.contractchange.common.result.ResultModel;
import com.citics.glxt.contractchange.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.service.DocxModifyAndLandService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 保护已上线接口：不要求UserId，响应data仍是单独的主表ID。 */
public class LegacyDocxControllerContractTest {
    @Test
    public void shouldKeepLegacyAnalyzeAndLandResponseContract() {
        DocxModifyAndLandService service = mock(DocxModifyAndLandService.class);
        when(service.analyzeAndLand(any(DocxAnalysisDTO.class))).thenReturn(77L);
        DocxController controller = new DocxController();
        ReflectionTestUtils.setField(controller, "docxModifyAndLandService", service);
        DocxAnalysisDTO request = new DocxAnalysisDTO();
        request.setFileGetPath("contract.docx");

        ResultModel<?> response = controller.analyzeAndLand(request);

        assertEquals(Integer.valueOf(200), response.getCode());
        assertEquals(Long.valueOf(77L), response.getData());
        verify(service).analyzeAndLand(request);
    }
}
