package com.nanjiabawa.service.impl;

import com.nanjiabawa.mapper.DeptMapper;
import com.nanjiabawa.pojo.Dept;
import com.nanjiabawa.service.DeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeptServiceImpl implements DeptService {

    @Autowired
    private DeptMapper deptMapper;

    @Override
    public List<Dept> findAll() {
        return deptMapper.findAll();
    }

    @Override
    public void save(Dept dept) {
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());
        deptMapper.save(dept);
    }

    @Override
    public void delete(Integer id) {
        deptMapper.delete(id);
    }
}
