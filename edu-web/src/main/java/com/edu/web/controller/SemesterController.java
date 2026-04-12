package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.SemesterService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/semester")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;
    @GetMapping("/list")
    public Result<List<Semester>> list() {
        return Result.success(semesterService.findAll());
    }
}
