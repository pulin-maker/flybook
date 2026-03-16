package com.nanjiabawa.mapper;

import com.nanjiabawa.pojo.Dept;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeptMapper {

    @Select("SELECT * FROM dept")
    public List<Dept> findAll();

    @Insert("INSERT INTO dept(name, create_time, update_time) values (#{name}, #{createTime}, #{updateTime})")
    void save(Dept dept);

    @Delete("delete from dept where id = #{id}")
    void delete(Integer id);
}
