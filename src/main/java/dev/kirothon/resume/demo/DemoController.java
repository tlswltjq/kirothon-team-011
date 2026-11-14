package dev.kirothon.resume.demo;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 요구사항 12.2: 화면이 샘플 데이터 버튼을 보일지 정한다. 프로필과 관계없이 항상 등록되고 인증이 필요 없다. */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    public record DemoStatus(boolean enabled) {
    }

    private final boolean enabled;

    public DemoController(Environment env) {
        this.enabled = env.acceptsProfiles(Profiles.of("demo"));
    }

    @GetMapping
    public DemoStatus status() {
        return new DemoStatus(enabled);
    }
}
