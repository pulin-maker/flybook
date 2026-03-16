package com.nanjiabawa.controller;

import com.nanjiabawa.pojo.Dept;
import com.nanjiabawa.pojo.Result;
import com.nanjiabawa.service.DeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/depts")
public class DeptController {

    @Autowired
    private DeptService deptService;

    @GetMapping
    public Result list() {
        List<Dept> deptList = deptService.findAll();
        return Result.success(deptList);
    }

    @PostMapping
    public Result save(@RequestBody Dept dept) {
        deptService.save(dept);
        return Result.success();
    }

    @DeleteMapping
    public Result delete(Integer id) {
        deptService.delete(id);
        return Result.success();
    }
}
