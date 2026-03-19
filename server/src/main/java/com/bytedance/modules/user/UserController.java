package com.bytedance.modules.user;


import com.bytedance.common.Result;
import com.bytedance.modules.user.LoginRequest;
import com.bytedance.modules.user.LoginResult;
import com.bytedance.modules.user.User;
import com.bytedance.modules.user.IUserService;
import com.bytedance.modules.user.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private IUserService userService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        LoginResult loginResult = userService.login(request.getUsername(), request.getPassword());

        Map<String, Object> data = new HashMap<>();
        data.put("token", loginResult.getToken());
        data.put("userInfo", loginResult.getUser());

        return Result.success(data);
    }

    /**
     * 获取所有用户列表
     * URL: GET /api/users/list
     */
    @GetMapping("/list")
    public Result<List<UserVO>> getUserList() {
        List<User> users = userService.list();
        List<UserVO> userVOList = users.stream()
                .map(user -> UserVO.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());
        return Result.success(userVOList);
    }
}
