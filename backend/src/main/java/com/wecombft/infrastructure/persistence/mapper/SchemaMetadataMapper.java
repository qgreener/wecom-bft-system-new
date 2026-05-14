package com.wecombft.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SchemaMetadataMapper {

    @Select("select count(*) from sys_app_metadata")
    long countAppMetadata();
}
