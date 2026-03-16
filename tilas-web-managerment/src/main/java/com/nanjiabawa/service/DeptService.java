package com.nanjiabawa.service;

import com.nanjiabawa.pojo.Dept;

import java.util.List;

public interface DeptService {
    List<Dept> findAll();

    void save(Dept dept);

    void delete(Integer id);
}
