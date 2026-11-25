package dev.kirothon.resume.review;

/** 후보 중 하나를 고르는 전략. 테스트에서 고정 구현으로 바꾼다. */
public interface RandomPicker {
    /** @return 0 이상 size 미만의 인덱스 (size >= 1) */
    int pick(int size);
}
