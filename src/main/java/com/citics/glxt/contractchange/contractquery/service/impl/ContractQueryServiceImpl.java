package com.citics.glxt.contractchange.contractquery.service.impl;

import com.citics.glxt.contractchange.contractquery.mapper.TpifCpwdkMapper;
import com.citics.glxt.contractchange.contractquery.domain.TpifCpwdk;
import com.citics.glxt.contractchange.contractquery.model.ContractInfo;
import com.citics.glxt.contractchange.contractquery.model.ContractFileDownload;
import com.citics.glxt.contractchange.contractquery.model.ContractQueryRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadRequest;
import com.citics.glxt.contractchange.contractquery.model.ContractDownloadPayload;
import com.citics.glxt.contractchange.contractquery.service.ContractQueryService;
import com.citics.glxt.contractchange.common.service.CommonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 负责计算查询起始日并调用文档库查询 Mapper。 */
@Slf4j
@Service
public class ContractQueryServiceImpl implements ContractQueryService {
    private static final DateTimeFormatter DOWNLOAD_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final TpifCpwdkMapper tpifCpwdkMapper;
    private final CommonService commonService;

    public ContractQueryServiceImpl(TpifCpwdkMapper tpifCpwdkMapper, CommonService commonService) {
        this.tpifCpwdkMapper = tpifCpwdkMapper;
        this.commonService = commonService;
    }

    @Override
    public List<ContractInfo> queryContracts(ContractQueryRequest request) {
        // 闭区间下界由基准日期减查询天数得到；queryDays=0 时仅查询基准日。
        Long managerId = request.getManagerId();
        LocalDate baseDate = request.getBaseDate();
        LocalDate startDate = baseDate.minusDays(request.getQueryDays().longValue());
        long startedAt = System.currentTimeMillis();
        log.info("合同文档查询开始, managerId={}, startDate={}, endDate={}", managerId, startDate, baseDate);
        List<ContractInfo> contracts = tpifCpwdkMapper.selectContracts(
                managerId, Date.valueOf(startDate), Date.valueOf(baseDate));
        log.info("合同文档查询完成, managerId={}, startDate={}, endDate={}, resultCount={}, elapsedMs={}",
                managerId, startDate, baseDate, contracts.size(), System.currentTimeMillis() - startedAt);
        return contracts;
    }

    @Override
    public ContractDownloadPayload downloadContracts(ContractDownloadRequest request) {
        Long managerId = request.getManagerId();
        List<Long> fileIds = new ArrayList<>(new LinkedHashSet<>(request.getFileIds()));
        long startedAt = System.currentTimeMillis();
        log.info("合同文档批量下载开始, managerId={}, fileCount={}", managerId, fileIds.size());
        List<TpifCpwdk> rows = tpifCpwdkMapper.selectOwnedContractsForDownload(fileIds, managerId);
        Map<Long, TpifCpwdk> rowsById = new HashMap<>();
        for (TpifCpwdk row : rows) rowsById.put(row.getId(), row);
        if (rowsById.size() != fileIds.size()) {
            log.warn("合同文档批量下载校验未通过或存在文件, managerId={}, requestedCount={}, matchedCount={}, elapsedMs={}",
                    managerId, fileIds.size(), rowsById.size(), System.currentTimeMillis() - startedAt);
            return null;
        }
        List<ContractFileDownload> downloads = new ArrayList<>();
        for (Long fileId : fileIds) {
            TpifCpwdk row = rowsById.get(fileId);
            byte[] content = commonService.downloadDocLibBytes(fileId);
            downloads.add(new ContractFileDownload(row.getFj(), content));
        }
        log.info("合同文档批量下载完成, managerId={}, fileCount={}, elapsedMs={}",
                managerId, downloads.size(), System.currentTimeMillis() - startedAt);
        return createDownloadPayload(downloads);
    }

    private ContractDownloadPayload createDownloadPayload(List<ContractFileDownload> downloads) {
        String timestamp = LocalDateTime.now().format(DOWNLOAD_TIMESTAMP);
        if (downloads.size() == 1) {
            return new ContractDownloadPayload("基金合同_" + timestamp + ".docx",
                    "Contract_" + timestamp + ".docx", "application/octet-stream", downloads.get(0).getContent());
        }
        try {
            byte[] zip = zipDownloads(downloads, timestamp);
            return new ContractDownloadPayload("基金合同_" + timestamp + ".zip",
                    "Contracts_" + timestamp + ".zip", "application/zip", zip);
        } catch (IOException e) {
            throw new IllegalStateException("合同文档压缩失败", e);
        }
    }

    private byte[] zipDownloads(List<ContractFileDownload> downloads, String timestamp) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < downloads.size(); i++) {
                zip.putNextEntry(new ZipEntry("基金合同_" + timestamp + "_" + String.format("%03d", i + 1) + ".docx"));
                zip.write(downloads.get(i).getContent());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
