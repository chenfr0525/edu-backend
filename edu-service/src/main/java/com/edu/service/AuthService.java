package com.edu.service;

import com.edu.common.JwtUtils;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.Teacher;
import com.edu.domain.User;
import com.edu.domain.Menu;
import com.edu.domain.dto.MenuDTO;
import com.edu.domain.dto.StudentDTO;
import com.edu.domain.dto.TeacherDTO;
import com.edu.domain.dto.UpdateUserInfoRequest;
import com.edu.repository.MenuRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.TeacherRepository;
import com.edu.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final MenuRepository menuRepository;
    private final StudentService studentService;

    public Map<String, Object> login(String username, String password,String role) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

      if (!user.getRole().name().equals(role)) {
            throw new RuntimeException("User not found");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
            
        String token = jwtUtils.generateToken(user.getUsername(), user.getRole().name());
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        if (user.getRole() == Role.STUDENT) {
           Optional<Student> studentOpt = studentRepository.findByUserId(user.getId());
            if (studentOpt.isPresent()) {
                Student student = studentOpt.get();
                // user 已经存在，就是当前登录用户
                StudentDTO studentDTO = new StudentDTO(student, user,student.getClassInfo());
                result.put("info", studentDTO);
            } else {
                result.put("info", null);
            }
        } else if (user.getRole() == Role.TEACHER) {
            TeacherDTO teacherDTO = teacherRepository.findByUser(user)
                .map(teacher -> new TeacherDTO(teacher, teacher.getUser()))
                .orElse(null);
        
            result.put("info", teacherDTO);
        }
        return result;
    }

    public void register(String username, String password, String roleStr) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(username);
        user.setRole(Role.valueOf(roleStr.toUpperCase()));
        User savedUser= userRepository.save(user);
        if(user.getRole() == Role.STUDENT) {
            Student student = new Student();
            student.setUser(savedUser);
            student.setStudentNo(generateStudentNo());
            student.setGrade("大一");
            studentRepository.save(student);
            studentService.setStudentClass(student.getId(), "计算机1班");
        } else if(user.getRole() == Role.TEACHER) {
            Teacher teacher = new Teacher();
            teacher.setUser(savedUser);
             teacher.setTeacherNo(generateTeacherNo());
            teacherRepository.save(teacher);
        }
       
    }
    private String generateStudentNo() {
        String maxNo = studentRepository.findMaxStudentNo();
        if (maxNo == null) {
            return "S20240001";
        }
        int num = Integer.parseInt(maxNo.substring(1)) + 1;
        return String.format("S%08d", num);  // 格式：S00000001
    }

     private String generateTeacherNo() {
        String maxNo = teacherRepository.findMaxTeacherNo();
        if (maxNo == null) {
            return "T20040001";
        }
        int num = Integer.parseInt(maxNo.substring(1)) + 1;
        return String.format("T%08d", num);  // 格式：T00000001
    }

    public List<MenuDTO> getMenus(String role) { 
        List<Menu> menus = menuRepository.findByRoleOrderByIdAsc(role);
        return menus.stream()
        .map(MenuDTO::new)
        .collect(Collectors.toList());
       }

       public Map<String, Object> getUserInfo() {
        User user = userRepository.findByUsername(jwtUtils.extractUsername(jwtUtils.getToken()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentService.findByUserId(user.getId())
                .orElse(null);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("user", student);
        return userInfo;
       }

        public User getUser() {
        User user = userRepository.findByUsername(jwtUtils.extractUsername(jwtUtils.getToken()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user;
       }

       /**
        * 修改User基本信息
        */
       public User updateUserInfo(UpdateUserInfoRequest request) {
        User user = userRepository.findByUsername(jwtUtils.extractUsername(jwtUtils.getToken()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setGender(request.getGender());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        if(request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        return userRepository.save(user);
       }

    /**
     * 修改密码
     */
    public void updatePassword(String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(jwtUtils.extractUsername(jwtUtils.getToken()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Invalid old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

}
