package com.edu.domain;

public enum ActivityStatus {
   LOGIN,      // 登录
    HOMEWORK,   // 作业提交
    EXAM,       // 考试参与
    RESOURCE,   // 资源访问
    VIDEO,      // 视频观看
    
    // 兼容旧数据
    VISIT,
    STUDY,
    DISCUSSION,
    visit,
    study,
    discussion,
    homework,
    video
}
