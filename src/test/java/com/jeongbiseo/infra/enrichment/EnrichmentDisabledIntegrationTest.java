package com.jeongbiseo.infra.enrichment;

import com.jeongbiseo.support.MySqlContainerSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 보강이 꺼져 있을 때 호출 경로가 아예 없는지 확인하는 통합 테스트임.
 *
 * <p>
 * <b>이 테스트가 필요한 이유</b>: 지금까지 "{@code @ConditionalOnProperty}가 잘 돌 것"이라고 믿기만 했고 확인한 적이
 * 없었음. 조건이 오타 나거나 프로퍼티 이름이 바뀌면 <b>기본값이 꺼짐인데도 배치가 돌아</b> 운영에서 예상 못 한 외부 호출이 나감. 기본 프로필에서
 * 플래그를 켜지 않았을 때 러너 빈이 없다는 사실을 고정함.
 * </p>
 */
@SpringBootTest
@TestPropertySource(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "app.ingestion.enabled=false" })
class EnrichmentDisabledIntegrationTest extends MySqlContainerSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void 플래그를_켜지_않으면_보강_러너_빈이_없다() {
		assertThat(this.context.containsBean("enrichmentBatchRunner")).isFalse();
	}

	/**
	 * 러너가 없다는 것과 "기동 시 아무 러너도 보강을 부르지 않는다"는 다름. 등록된 ApplicationRunner 중 보강 러너가 섞여 있지 않은지
	 * 함께 봄.
	 */
	@Test
	void 등록된_기동_러너에_보강_러너가_섞여_있지_않다() {
		assertThat(this.context.getBeansOfType(ApplicationRunner.class)).doesNotContainKey("enrichmentBatchRunner");
	}

	/**
	 * 배치 컴포넌트 자체는 존재함(@Component라 스캔됨). 존재하되 아무도 부르지 않는 상태가 의도한 모습이며, 이 구분이 흐려지면 "빈이 있으니
	 * 돌겠지"라는 오해가 생김.
	 */
	@Test
	void 배치_컴포넌트는_있지만_부르는_러너가_없다() {
		assertThat(this.context.getBeanNamesForType(EnrichmentBatch.class)).isNotEmpty();
		assertThat(this.context.containsBean("enrichmentBatchRunner")).isFalse();
	}

}
