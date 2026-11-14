package dev.kirothon.resume.demo;

import dev.kirothon.resume.demo.DemoSeeder.SeedResult;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 데모 프로필에서만 등록된다. 꺼져 있으면 매핑이 없어 404 NOT_FOUND(12.2). */
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
public class DemoSeedController {
    private final DemoSeeder seeder;

    public DemoSeedController(DemoSeeder seeder) {
        this.seeder = seeder;
    }

    @PostMapping("/seed")
    public ResponseEntity<SeedResult> seed(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user) {
        return ResponseEntity.status(201).body(seeder.seed(user.getId()));
    }
}
