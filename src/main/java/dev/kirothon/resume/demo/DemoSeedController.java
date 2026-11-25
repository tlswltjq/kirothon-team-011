package dev.kirothon.resume.demo;

import dev.kirothon.resume.demo.DemoSeeder.SeedResult;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 데모 프로필에서만 등록된다. 꺼져 있으면 매핑이 없어 404 NOT_FOUND(12.2). */
@Tag(name = "데모", description = "데모 샘플 데이터")
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
public class DemoSeedController {
    private final DemoSeeder seeder;

    public DemoSeedController(DemoSeeder seeder) {
        this.seeder = seeder;
    }

    @Operation(summary = "데모 샘플 이력서와 평가를 넣는다(demo 프로필 전용)")
    @PostMapping("/seed")
    public ResponseEntity<SeedResult> seed(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user) {
        return ResponseEntity.status(201).body(seeder.seed(user.getId()));
    }
}
