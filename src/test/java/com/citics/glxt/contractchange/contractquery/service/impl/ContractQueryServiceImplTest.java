package com.citics.glxt.contractchange.contractquery.service.impl;

import com.citics.glxt.contractchange.contractquery.mapper.TpifCpwdkMapper;
import com.citics.glxt.contractchange.contractquery.domain.TpifCpwdk;
import com.citics.glxt.contractchange.contractquery.model.ContractFileDownload;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadPayload;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryCriteria;
import com.citics.glxt.contractchange.contractquery.model.ContractPageQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import com.citics.glxt.contractchange.common.service.CommonService;
import org.junit.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class ContractQueryServiceImplTest {
    @Test
    public void shouldPassInclusiveDateRangeToMapper() {
        TpifCpwdkMapper mapper = mock(TpifCpwdkMapper.class);
        CommonService commonService = mock(CommonService.class);
        when(mapper.selectContracts(any(ContractQueryCriteria.class)))
                .thenReturn(Collections.emptyList());
        ContractQueryServiceImpl service = new ContractQueryServiceImpl(mapper, commonService);

        assertEquals(0, service.queryContracts(request(3)).size());

        org.mockito.ArgumentCaptor<ContractQueryCriteria> captor =
                org.mockito.ArgumentCaptor.forClass(ContractQueryCriteria.class);
        verify(mapper).selectContracts(captor.capture());
        assertEquals(Long.valueOf(525L), captor.getValue().getManagerId());
        assertEquals(Date.valueOf("2026-10-06"), captor.getValue().getStartDate());
        assertEquals(Date.valueOf("2026-10-09"), captor.getValue().getEndDate());
    }

    @Test
    public void zeroDaysShouldUseSameStartAndEndDate() {
        TpifCpwdkMapper mapper = mock(TpifCpwdkMapper.class);
        CommonService commonService = mock(CommonService.class);
        when(mapper.selectContracts(any(ContractQueryCriteria.class)))
                .thenReturn(Collections.emptyList());

        new ContractQueryServiceImpl(mapper, commonService).queryContracts(request(0));

        org.mockito.ArgumentCaptor<ContractQueryCriteria> captor =
                org.mockito.ArgumentCaptor.forClass(ContractQueryCriteria.class);
        verify(mapper).selectContracts(captor.capture());
        assertEquals(Date.valueOf("2026-10-09"), captor.getValue().getStartDate());
        assertEquals(Date.valueOf("2026-10-09"), captor.getValue().getEndDate());
    }

    @Test
    public void pagedQueryShouldBuildCriteriaObjectAndReturnPaginationMetadata() {
        TpifCpwdkMapper mapper = mock(TpifCpwdkMapper.class);
        CommonService commonService = mock(CommonService.class);
        com.github.pagehelper.Page<ContractInfo> mapperPage = new com.github.pagehelper.Page<>(2, 20);
        when(mapper.selectContracts(any(ContractQueryCriteria.class))).thenReturn(mapperPage);

        ContractPageQueryRequest request = new ContractPageQueryRequest();
        request.setManagerId(525L);
        request.setBaseDate(LocalDate.parse("2026-10-09"));
        request.setQueryDays(3);
        request.setPageNum(2);
        request.setPageSize(20);

        try {
            com.citics.glxt.contractchange.common.page.PageResult result =
                    new ContractQueryServiceImpl(mapper, commonService).queryContractsPage(request);

            org.mockito.ArgumentCaptor<ContractQueryCriteria> captor =
                    org.mockito.ArgumentCaptor.forClass(ContractQueryCriteria.class);
            verify(mapper).selectContracts(captor.capture());
            assertEquals(Long.valueOf(525L), captor.getValue().getManagerId());
            assertEquals(Date.valueOf("2026-10-06"), captor.getValue().getStartDate());
            assertEquals(Date.valueOf("2026-10-09"), captor.getValue().getEndDate());
            assertEquals(2, result.getPageNum());
            assertEquals(20, result.getPageSize());
            assertEquals(0L, result.getTotalSize());
        } finally {
            com.github.pagehelper.PageHelper.clearPage();
        }
    }

    @Test
    public void downloadShouldVerifyOwnerBeforeDownloadingFileBytes() {
        TpifCpwdkMapper mapper = mock(TpifCpwdkMapper.class);
        CommonService commonService = mock(CommonService.class);
        TpifCpwdk row = new TpifCpwdk();
        row.setId(123L);
        row.setFj("contract.docx");
        when(mapper.selectOwnedContractsForDownload(Collections.singletonList(123L), 525L)).thenReturn(Collections.singletonList(row));
        when(commonService.downloadDocLibBytes(123L)).thenReturn(new byte[]{1, 2, 3});

        ContractDownloadPayload file = new ContractQueryServiceImpl(mapper, commonService).downloadContracts(downloadRequest(123L));

        assertEquals("application/octet-stream", file.getContentType());
        org.junit.Assert.assertTrue(file.getFileName().matches("基金合同_\\d{17}\\.docx"));
        org.junit.Assert.assertTrue(file.getFallbackFileName().matches("Contract_\\d{17}\\.docx"));
        assertEquals(3, file.getContent().length);
        verify(mapper).selectOwnedContractsForDownload(eq(Collections.singletonList(123L)), eq(525L));
        verify(commonService).downloadDocLibBytes(123L);
    }

    @Test
    public void downloadShouldNotFetchWhenFileDoesNotBelongToManager() {
        TpifCpwdkMapper mapper = mock(TpifCpwdkMapper.class);
        CommonService commonService = mock(CommonService.class);

        ContractDownloadPayload file = new ContractQueryServiceImpl(mapper, commonService).downloadContracts(downloadRequest(123L));

        assertEquals(null, file);
        verify(mapper).selectOwnedContractsForDownload(Collections.singletonList(123L), 525L);
        org.mockito.Mockito.verifyNoMoreInteractions(commonService);
    }

    private ContractQueryRequest request(int queryDays) {
        ContractQueryRequest request = new ContractQueryRequest();
        request.setManagerId(525L);
        request.setBaseDate(LocalDate.parse("2026-10-09"));
        request.setQueryDays(queryDays);
        return request;
    }

    private ContractDownloadRequest downloadRequest(Long... ids) {
        ContractDownloadRequest request = new ContractDownloadRequest();
        request.setManagerId(525L);
        request.setFileIds(Arrays.asList(ids));
        return request;
    }
}
