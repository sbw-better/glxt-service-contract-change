package com.citics.glxt.contractchange.docx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.citics.glxt.contractchange.docx.domain.ModifyConfiguration;
import com.citics.glxt.contractchange.docx.mapper.ModifyConfigurationMapper;
import com.citics.glxt.contractchange.docx.service.ModifyConfigurationService;
import org.springframework.stereotype.Service;

@Service
public class ModifyConfigurationServiceImpl extends ServiceImpl<ModifyConfigurationMapper, ModifyConfiguration> implements ModifyConfigurationService {
}
