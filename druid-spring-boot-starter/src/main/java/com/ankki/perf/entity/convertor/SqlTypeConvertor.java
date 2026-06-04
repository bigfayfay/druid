package com.ankki.perf.entity.convertor;

import com.ankki.perf.entity.db.AuditBaseDO;
import com.ankki.perf.entity.db.SqlTemplateRecord;
import com.ankki.perf.entity.SqlTypeBO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.factory.Mappers;

/**
 * @author fay
 * @date 2026-06-04
 * @description
 */
@Mapper(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface SqlTypeConvertor {

    SqlTypeConvertor INSTANCE = Mappers.getMapper(SqlTypeConvertor.class);


    public SqlTypeBO mapToEntity(AuditBaseDO audit);

    @Mapping(target = "operType", source = "tem.operType")
    @Mapping(target = "operSentence", source = "tem.template")
    public SqlTypeBO mapToEntity(SqlTemplateRecord tem);

}
