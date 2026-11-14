package dev.kirothon.resume.user;

import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.security.TokenHasher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AnonUserRepository users;

    public UserController(AnonUserRepository users) {
        this.users = users;
    }

    public record CreateUserRequest(
            @NotNull(message = "닉네임을 입력하세요.")
            @Size(min = 2, max = 20, message = "닉네임은 앞뒤 공백을 뺀 2~20자여야 합니다.")
            String nickname) {
        public CreateUserRequest {
            nickname = nickname == null ? null : nickname.strip();
        }
    }

    public record CreateUserResponse(String token, String nickname) {
    }

    public record MeResponse(String nickname) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        String token = TokenHasher.newToken();
        users.save(new AnonUser(request.nickname(), TokenHasher.sha256Hex(token)));
        return new CreateUserResponse(token, request.nickname());
    }

    @GetMapping("/me")
    public MeResponse me(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user) {
        return new MeResponse(user.getNickname());
    }
}
