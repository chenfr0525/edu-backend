package com.edu.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.domain.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
