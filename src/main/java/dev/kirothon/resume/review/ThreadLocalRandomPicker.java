package dev.kirothon.resume.review;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** 균등 확률 선택(6.1). 크레딧·평가 횟수·직무는 보지 않는다(6.4). */
@Component
public class ThreadLocalRandomPicker implements RandomPicker {
    @Override
    public int pick(int size) {
        return ThreadLocalRandom.current().nextInt(size);
    }
}
