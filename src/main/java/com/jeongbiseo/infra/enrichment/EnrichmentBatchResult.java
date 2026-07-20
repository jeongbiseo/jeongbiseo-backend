package com.jeongbiseo.infra.enrichment;

import java.util.Map;

/**
 * 보강 배치 한 회차 요약임. 발표 지표 3개 중 둘(근거 검증 통과율, 기권율)이 {@code outcomes} 분포에서 바로 나오므로 사유를 뭉개지 않고
 * 그대로 담음.
 *
 * @param calls 실제 LLM 호출 건수
 * @param saved 검증을 통과해 저장된 건수
 * @param skipped 호출 없이 건너뛴 건수(금액 표현 없음, 이미 보강함)
 * @param outcomes 판정별 건수. 키는 ACCEPTED와 {@code RejectionReason} 이름, 그리고
 * CALL_FAILED·SAVE_FAILED
 */
public record EnrichmentBatchResult(int calls, int saved, int skipped, Map<String, Integer> outcomes) {

}
